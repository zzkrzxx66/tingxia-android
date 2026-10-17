package com.tingxia.app.player

import com.tingxia.app.data.db.ChapterCacheEntry
import com.tingxia.app.data.remote.FqEndpoints
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheStatusSynchronizerTest {
    @Test
    fun clearingOrEvictingBytesRemovesPersistedOfflineBadge() = runTest {
        var entry = ChapterCacheEntry(1, "100", "200", "0", true)
        val synchronizer = CacheStatusSynchronizer(
            entries = { listOf(entry) },
            isFullyCached = { false },
            update = { _, cached -> entry = entry.copy(isCached = cached) },
        )
        synchronizer.refresh()
        assertFalse(entry.isCached)
    }

    @Test
    fun onlyTheCompleteRecordingForTheCurrentVoiceGetsAnOfflineBadge() = runTest {
        val entries = mutableListOf(
            ChapterCacheEntry(1, "100", "200", "0", false),
            ChapterCacheEntry(2, "100", "200", "5", true),
            ChapterCacheEntry(3, "100", "201", "0", false),
        )
        val complete = setOf(FqEndpoints.cacheKey("100", "200", "0"))
        val synchronizer = CacheStatusSynchronizer(
            entries = { entries.toList() },
            isFullyCached = complete::contains,
            update = { entry, cached ->
                val index = entries.indexOfFirst { it.chapterId == entry.chapterId }
                entries[index] = entry.copy(isCached = cached)
            },
        )
        synchronizer.refresh()
        assertEquals(listOf(true, false, false), entries.map { it.isCached })
    }

    @Test
    fun pendingCompletionCannotOverwriteANewerEvictionScan() = runTest {
        var entry = ChapterCacheEntry(1, "100", "200", "0", false)
        var onDisk = true
        val releaseWrite = CompletableDeferred<Unit>()
        val synchronizer = CacheStatusSynchronizer(
            entries = { listOf(entry) },
            isFullyCached = { onDisk },
            update = { _, cached ->
                releaseWrite.await()
                entry = entry.copy(isCached = cached)
            },
        )
        val completing = launch { synchronizer.refresh() }
        runCurrent()
        onDisk = false
        val evicting = launch { synchronizer.refresh() }
        runCurrent()
        assertTrue(completing.isActive)
        assertTrue(evicting.isActive)
        releaseWrite.complete(Unit)
        completing.join()
        evicting.join()
        assertFalse(entry.isCached)
    }
}
