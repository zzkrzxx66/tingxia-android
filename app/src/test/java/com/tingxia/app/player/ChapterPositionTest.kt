package com.tingxia.app.player

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import org.junit.Assert.*
import org.junit.Test

class ChapterPositionTest {
    private val book = Book(1, "Book", null, null, "root", 10000, 1, 1, 5000, 5000, 1, false,
        skipIntroMs = 2000, skipOutroMs = 3000)
    private val chapter = Chapter(1, 1, "Chapter", "uri", 0, 10000, "file")

    @Test fun bookmarkUsesSameSourceMomentAfterChangingSkipSettings() {
        val sourcePosition = 1200L + chapter.playbackOffsetMs(book)
        assertEquals(3200L, sourcePosition)
        assertEquals(1200L, chapter.toPlaybackPosition(book, sourcePosition))
        assertEquals(3200L, chapter.toPlaybackPosition(book.copy(skipIntroMs = 0, skipOutroMs = 0), sourcePosition))
    }
    @Test fun m4bPositionDoesNotIncludeWholeFileOffset() {
        val embedded = chapter.copy(clipStartMs = 60000, clipEndMs = 70000)
        assertEquals(2000L, embedded.playbackOffsetMs(book))
        assertEquals(1000L, embedded.toPlaybackPosition(book, 3000))
        assertEquals(5000L, embedded.playableDurationMs(book))
    }
    @Test fun unknownDurationResolutionDoesNotDoubleSkipIntro() {
        val unknown = chapter.copy(durationMs = 0)
        val source = unknown.playbackOffsetMs(book) + 1000L
        assertEquals(1000L, chapter.toPlaybackPosition(book, source))
        assertNull(unknown.playableDurationMs(book))
    }
    @Test fun remainingUsesClippedWindowsAndUnknownIsNotZero() {
        val second = chapter.copy(id = 2, index = 1)
        assertEquals(7000L, remainingPlaybackMs(book, listOf(chapter, second), 1, 5000))
        assertNull(remainingPlaybackMs(book, listOf(chapter, second.copy(durationMs = 0)), 1, 5000))
    }
}
