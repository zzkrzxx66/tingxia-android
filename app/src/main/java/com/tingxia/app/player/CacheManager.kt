package com.tingxia.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
) {
    val cache: Cache by lazy {
        val dir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
        SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /** Cache data-source factory used by the player for remote items only. */
    fun cacheDataSourceFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun cacheKeyForChapter(bookRemoteAudioBookId: String, remoteItemId: String): String =
        "fqnovel_${bookRemoteAudioBookId}_$remoteItemId"

    /** True when every byte of [key] is already cached. */
    fun isFullyCached(key: String): Boolean {
        return try {
            val meta = cache.getContentMetadata(key)
            val length = meta.get(
                androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH,
                Long.MIN_VALUE,
            )
            if (length == Long.MIN_VALUE) return false
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
    fun fullyCachedKeys(bookRemoteAudioBookId: String, remoteItemIds: List<String>): Set<String> {
        val out = HashSet<String>()
        remoteItemIds.forEach { itemId ->
            val key = cacheKeyForChapter(bookRemoteAudioBookId, itemId)
            val spans = cache.getCachedSpans(key)
            if (spans.isNotEmpty()) {
                // Fully cached iff the cache reports zero remaining bytes.
                val remaining = cache.getCachedLength(key, 0, Long.MAX_VALUE)
                val contentLength = cache.getContentMetadata(key)
                    .get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH,
                        Long.MIN_VALUE)
                if (contentLength != Long.MIN_VALUE) {
                    val cached = cache.getCachedBytes(key, 0, contentLength)
                    if (cached >= contentLength) out += itemId
                } else if (remaining != Long.MAX_VALUE) {
                    out += itemId
                }
            }
        }
        return out
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                cache.keys.forEach { key -> cache.removeResource(key) }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /** LRU cap for the whole audio cache. */
        const val MAX_CACHE_BYTES = 1L * 1024L * 1024L * 1024L // 1 GB
    }
}
