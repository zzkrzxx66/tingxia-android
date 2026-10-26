package com.tingxia.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
class PinnedCacheTest {
    @Test fun explicitDownloadsSurviveTemporaryEvictionAndRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "pin_test_${System.nanoTime()}").apply { mkdirs() }
        val pins = setOf("download")
        var cache: Cache = SimpleCache(directory, PinnedCacheEvictor(10, pins::contains), StandaloneDatabaseProvider(context))
        try {
            write(cache, "download", 20)
            write(cache, "oldTemporary", 6)
            write(cache, "newTemporary", 8)
            assertTrue(cache.isCached("download", 0, 20))
            assertFalse(cache.isCached("oldTemporary", 0, 6))
            assertTrue(cache.isCached("newTemporary", 0, 8))
            cache.release()
            cache = SimpleCache(directory, PinnedCacheEvictor(10, pins::contains), StandaloneDatabaseProvider(context))
            assertTrue(cache.isCached("download", 0, 20))
            cache.removeResource("download")
            assertFalse(cache.isCached("download", 0, 20))
        } finally { cache.release(); directory.deleteRecursively() }
    }
    private fun write(cache: Cache, key: String, size: Int) {
        val hole = cache.startReadWrite(key, 0, size.toLong())
        try {
            val file = cache.startFile(key, 0, size.toLong())
            file.writeBytes(ByteArray(size) { 42 })
            cache.commitFile(file, size.toLong())
        } finally { cache.releaseHoleSpan(hole) }
    }
}
