package com.tingxia.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculatorTest {

    private fun chapters(): List<Chapter> = listOf(
        Chapter(id = 1, bookId = 10, title = "c1", uri = "u1", index = 0, durationMs = 1000, fileName = "01.mp3"),
        Chapter(id = 2, bookId = 10, title = "c2", uri = "u2", index = 1, durationMs = 2000, fileName = "02.mp3"),
        Chapter(id = 3, bookId = 10, title = "c3", uri = "u3", index = 2, durationMs = 3000, fileName = "03.mp3"),
    )

    @Test
    fun firstChapter_partial() {
        val listened = ProgressCalculator.linearPositionMs(chapters(), currentChapterId = 1, currentPositionMs = 400)
        assertEquals(400L, listened)
        val fraction = ProgressCalculator.progressFraction(chapters(), 1, 400)
        assertEquals(400f / 6000f, fraction, 1e-5f)
    }

    @Test
    fun secondChapter_includesPrevious() {
        val listened = ProgressCalculator.linearPositionMs(chapters(), currentChapterId = 2, currentPositionMs = 500)
        assertEquals(1000L + 500L, listened)
    }

    @Test
    fun thirdChapter_fullPrevious() {
        val listened = ProgressCalculator.linearPositionMs(chapters(), currentChapterId = 3, currentPositionMs = 100)
        assertEquals(1000L + 2000L + 100L, listened)
        assertEquals((3100f / 6000f), ProgressCalculator.progressFraction(chapters(), 3, 100), 1e-5f)
    }

    @Test
    fun clampsPositionToChapterDuration() {
        val listened = ProgressCalculator.linearPositionMs(chapters(), currentChapterId = 1, currentPositionMs = 99999)
        assertEquals(1000L, listened)
    }

    @Test
    fun unknownChapter_fallsBackToPosition() {
        val listened = ProgressCalculator.linearPositionMs(chapters(), currentChapterId = 99, currentPositionMs = 123)
        assertEquals(123L, listened)
    }

    @Test
    fun emptyChapters() {
        assertEquals(0L, ProgressCalculator.linearPositionMs(emptyList(), 1, 50))
        assertEquals(0f, ProgressCalculator.progressFraction(emptyList(), 1, 50), 0f)
    }

    @Test
    fun bookProgressFraction_usesListenedField() {
        val book = Book(
            id = 1, title = "t", author = null, coverPath = null, rootUri = "r",
            totalDurationMs = 6000, lastPlayedAt = 1, currentChapterId = 2,
            currentPositionMs = 500, linearPositionMs = 1500, createdAt = 0, needsReauth = false,
        )
        assertEquals(0.25f, book.progressFraction, 1e-5f)
    }
}
