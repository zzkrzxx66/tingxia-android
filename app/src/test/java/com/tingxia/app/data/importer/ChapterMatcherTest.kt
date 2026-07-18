package com.tingxia.app.data.importer

import com.tingxia.app.data.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterMatcherTest {
    private fun ch(
        id: Long,
        fileName: String,
        uri: String = "content://doc/$id",
        path: String = fileName,
        size: Long = 1000,
        duration: Long = 60_000,
        stable: String? = null,
    ) = Chapter(
        id = id,
        bookId = 1,
        title = fileName,
        uri = uri,
        index = id.toInt() - 1,
        durationMs = duration,
        fileName = fileName,
        relativePath = path,
        fileSize = size,
        documentId = "doc-$id",
        stableKey = stable,
    )

    private fun sc(
        fileName: String,
        uri: String,
        path: String = fileName,
        size: Long = 1000,
        duration: Long = 60_000,
        index: Int = 0,
    ): ScannedChapter {
        val key = ChapterIdentity.stableKey(path, size, duration)
        return ScannedChapter(
            title = fileName.substringBeforeLast('.'),
            uri = uri,
            documentId = null,
            relativePath = path,
            fileName = fileName,
            fileSize = size,
            mimeType = "audio/mpeg",
            durationMs = duration,
            index = index,
            stableKey = key,
        )
    }

    @Test
    fun matchesByUri() {
        val old = listOf(ch(1, "a.mp3", uri = "u1"))
        val neu = listOf(sc("a.mp3", uri = "u1"))
        val r = ChapterMatcher.match(old, neu)
        assertEquals(1, r.matches.size)
        assertEquals("u1", r.matches[1]!!.uri)
        assertTrue(r.added.isEmpty())
        assertTrue(r.removed.isEmpty())
    }

    @Test
    fun matchesRenamedSameSizeDuration() {
        val old = listOf(ch(1, "old.mp3", path = "old.mp3", size = 5000, duration = 120_000))
        val neu = listOf(sc("new.mp3", uri = "u2", path = "new.mp3", size = 5000, duration = 120_000))
        val r = ChapterMatcher.match(old, neu)
        assertEquals(1, r.matches.size)
        assertTrue(r.removed.isEmpty())
        assertTrue(r.added.isEmpty())
    }

    @Test
    fun doesNotMatchDifferentSubdirSameNameWithoutSize() {
        val old = listOf(
            ch(1, "a.mp3", path = "disc1/a.mp3", size = 0, duration = 0),
            ch(2, "a.mp3", path = "disc2/a.mp3", size = 0, duration = 0),
        )
        val neu = listOf(
            sc("a.mp3", "u1", path = "disc1/a.mp3", size = 0, duration = 0, index = 0),
            sc("a.mp3", "u2", path = "disc2/a.mp3", size = 0, duration = 0, index = 1),
        )
        val r = ChapterMatcher.match(old, neu)
        // relativePath exact match should win
        assertEquals("u1", r.matches[1]!!.uri)
        assertEquals("u2", r.matches[2]!!.uri)
    }

    @Test
    fun detectsAddedAndRemoved() {
        val old = listOf(ch(1, "a.mp3"), ch(2, "b.mp3"))
        val neu = listOf(sc("a.mp3", "u1"), sc("c.mp3", "u3", size = 2000, duration = 90_000, index = 1))
        val r = ChapterMatcher.match(old, neu)
        assertEquals(1, r.matches.size)
        assertEquals(1, r.removed.size)
        assertEquals(1, r.added.size)
    }

    @Test
    fun filenameOnlyMatchRequiresExplicitDecision() {
        val old = listOf(ch(1, "a.mp3", path = "old/a.mp3", size = 0, duration = 0))
        val neu = listOf(sc("a.mp3", "u2", path = "new/a.mp3", size = 0, duration = 0))

        val preview = RescanPlanner.plan(1, old, neu)
        assertEquals(setOf(1L), preview.weakMatches.keys)

        val rejected = RescanPlanner.plan(1, old, neu, rejectedWeak = setOf(1L))
        assertTrue(rejected.weakMatches.isEmpty())
        assertEquals(listOf(1L), rejected.removed.map { it.id })
        assertEquals(listOf("u2"), rejected.added.map { it.uri })
    }

    @Test
    fun rejectedAmbiguousScanBecomesAddedChapter() {
        val old = listOf(
            ch(1, "a.mp3", path = "same/a.mp3", size = 0, duration = 0),
            ch(2, "a.mp3", path = "same/a.mp3", size = 0, duration = 0),
        )
        val neu = listOf(sc("a.mp3", "u3", path = "same/a.mp3", size = 0, duration = 0))

        val preview = RescanPlanner.plan(1, old, neu)
        assertEquals(1, preview.ambiguousCount)

        val rejected = RescanPlanner.plan(1, old, neu, rejectedAmbiguous = setOf("u3"))
        assertTrue(rejected.ambiguous.isEmpty())
        assertEquals(listOf("u3"), rejected.added.map { it.uri })
    }

    @Test(timeout = 5_000)
    fun largeBookMatchingDoesNotRecomputeTheFullMatrixPerChapter() {
        val count = 400
        val old = (1..count).map { index ->
            ch(
                id = index.toLong(),
                fileName = "%04d.mp3".format(index),
                uri = "content://doc/$index",
                size = 10_000L + index,
                duration = 60_000L + index,
            )
        }
        val scanned = (1..count).map { index ->
            sc(
                fileName = "%04d.mp3".format(index),
                uri = "content://doc/$index",
                size = 10_000L + index,
                duration = 60_000L + index,
                index = index - 1,
            )
        }

        val result = ChapterMatcher.match(old, scanned)

        assertEquals(count, result.matches.size)
        assertTrue(result.added.isEmpty())
        assertTrue(result.removed.isEmpty())
    }
}
