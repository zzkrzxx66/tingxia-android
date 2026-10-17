package com.tingxia.app.player

import com.tingxia.app.data.db.ChapterCacheEntry
import com.tingxia.app.data.remote.FqEndpoints
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Both clearing and completing a download reconcile against the actual recording on disk. */
internal class CacheStatusSynchronizer(
    private val entries: suspend () -> List<ChapterCacheEntry>,
    private val isFullyCached: (String) -> Boolean,
    private val update: suspend (ChapterCacheEntry, Boolean) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun refresh() = mutex.withLock {
        entries().forEach { entry ->
            val cached = isFullyCached(FqEndpoints.cacheKey(entry.audioBookId, entry.itemId, entry.toneId))
            if (cached != entry.isCached) update(entry, cached)
        }
    }
}
