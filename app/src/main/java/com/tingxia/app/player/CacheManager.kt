package com.tingxia.app.player

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.tingxia.app.data.db.ChapterDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared ExoPlayer [SimpleCache] for online (fqnovel) audio.
 *
 * Playback and prefetch both read/write through this single cache so a chapter
 * downloaded once is playable offline. LRU eviction caps total size; local
 * SAF files never touch this cache.
 */
@UnstableApi
@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    chapterDao: ChapterDao,
) {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val synchronizer = CacheStatusSynchronizer(
        entries = chapterDao::cacheEntries,
        isFullyCached = ::isFullyCached,
        update = { entry, cached ->
            chapterDao.updateCachedRecording(
                entry.chapterId, entry.audioBookId, entry.itemId, entry.toneId, cached,
            )
        },
    )

    private val pinPreferences = context.getSharedPreferences("offline_audio", Context.MODE_PRIVATE)
    private val pinnedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
        addAll(pinPreferences.getStringSet("keys", emptySet()).orEmpty())
    }

    @Volatile private var preserveLegacy = File(context.cacheDir, "audio_cache").exists()

    val cache: Cache by lazy {
        val dir = File(context.filesDir, "audio_cache")
        val legacy = File(context.cacheDir, "audio_cache")
        if (!dir.exists() && legacy.exists()) check(legacy.renameTo(dir)) { "无法迁移音频缓存" }
        check(dir.exists() || dir.mkdirs()) { "无法创建音频缓存目录" }
        val lru = PinnedCacheEvictor(MAX_CACHE_BYTES) { preserveLegacy || pinnedKeys.contains(it) }
        val evictor = object : CacheEvictor by lru {
            override fun onCacheInitialized() {
                lru.onCacheInitialized()
                refreshRequests.trySend(Unit)
            }

            override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                lru.onSpanAdded(cache, span)
                refreshRequests.trySend(Unit)
            }

            override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
                lru.onSpanRemoved(cache, span)
                refreshRequests.trySend(Unit)
            }
        }
        SimpleCache(
            dir,
            evictor,
            StandaloneDatabaseProvider(context),
        )
    }

    init {
        // Cache callbacks run under SimpleCache's lock. Only signal here; query Room
        // and read the cache later on IO, coalescing bursts of span changes.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (preserveLegacy) {
                try {
                    val oldDownloads = chapterDao.cacheEntries().filter { it.isCached }.map { entry ->
                        cacheKeyForChapter(entry.audioBookId, entry.itemId, entry.toneId)
                    }
                    synchronized(pinnedKeys) {
                        val updated = pinnedKeys.toSet() + oldDownloads
                        check(pinPreferences.edit().putStringSet("keys", updated).commit()) { "无法迁移下载保护" }
                        pinnedKeys.addAll(oldDownloads)
                    }
                    preserveLegacy = false
                } catch (error: Exception) { Log.w("TingXiaCache", "Keeping legacy downloads protected", error) }
            }
            for (request in refreshRequests) {
                delay(250)
                try {
                    synchronizeCacheStatus()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("TingXiaCache", "Could not reconcile cached chapters", error)
                }
            }
        }
        refreshRequests.trySend(Unit)
    }

    /** Persist protection before writing bytes; Android cache cleanup cannot remove these files. */
    suspend fun pin(key: String) = withContext(Dispatchers.IO) {
        synchronized(pinnedKeys) {
            val updated = pinnedKeys.toSet() + key
            check(pinPreferences.edit().putStringSet("keys", updated).commit()) { "无法保存下载保护状态" }
            pinnedKeys.add(key)
        }
    }

    private fun unpin(key: String) {
        synchronized(pinnedKeys) {
            val updated = pinnedKeys.toSet() - key
            check(pinPreferences.edit().putStringSet("keys", updated).commit()) { "无法清除下载保护状态" }
            pinnedKeys.remove(key)
        }
    }

    /** Cache data-source factory used by the player for remote items only. */
    fun cacheDataSourceFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun cacheKeyForChapter(
        bookRemoteAudioBookId: String,
        remoteItemId: String,
        toneId: String? = com.tingxia.app.data.remote.FqEndpoints.DEFAULT_TONE,
    ): String = com.tingxia.app.data.remote.FqEndpoints.cacheKey(bookRemoteAudioBookId, remoteItemId, toneId)

    /** True when every byte of [key] is already cached. */
    fun isFullyCached(key: String): Boolean {
        return try {
            val meta = cache.getContentMetadata(key)
            val length = meta.get(
                androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH,
                Long.MIN_VALUE,
            )
            if (length <= 0L) return false
            cache.getCachedBytes(key, 0, length) >= length
        } catch (_: Exception) {
            false
        }
    }

    /** Sum of all cached bytes, in the app's cache directory. */
    suspend fun cachedBytes(): Long = withContext(Dispatchers.IO) {
        cache.cacheSpace
    }

    /** Fully cached keys for one book, used to render per-book cache status. */
    fun fullyCachedKeys(
        bookRemoteAudioBookId: String,
        remoteItemIds: List<String>,
        toneId: String? = com.tingxia.app.data.remote.FqEndpoints.DEFAULT_TONE,
    ): Set<String> {
        return remoteItemIds.filterTo(HashSet()) { itemId ->
            isFullyCached(cacheKeyForChapter(bookRemoteAudioBookId, itemId, toneId))
        }
    }

    suspend fun synchronizeCacheStatus() = withContext(Dispatchers.IO) {
        synchronizer.refresh()
    }

    suspend fun remove(keys: Collection<String>) = withContext(Dispatchers.IO) {
        try {
            keys.distinct().forEach { key -> cache.removeResource(key); unpin(key) }
        } finally {
            // Partial failures must also leave the indicators consistent with disk.
            synchronizeCacheStatus()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        remove((cache.keys + pinnedKeys).toList())
    }

    companion object {
        /** LRU cap for the whole audio cache. */
        const val MAX_CACHE_BYTES = 1L * 1024L * 1024L * 1024L // 1 GB
    }
}
