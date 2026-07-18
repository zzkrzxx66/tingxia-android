package com.tingxia.app.player

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {

    @Test
    fun resumePlan_restoresChapterPositionAndBookSpeed() {
        val chapters = listOf(chapter(10, 0, 1_000), chapter(20, 1, 2_000))
        val plan = createPlaybackResumePlan(
            book = book(currentChapterId = 20, positionMs = 1_250, speed = 1.5f),
            chapters = chapters,
            defaultSpeed = 1.0f,
        )

        assertEquals(1, plan?.startIndex)
        assertEquals(1_250L, plan?.startPositionMs)
        assertEquals(1.5f, plan?.speed)
    }

    @Test
    fun resumePlan_fallsBackToFirstChapterAndClampsPosition() {
        val plan = createPlaybackResumePlan(
            book = book(currentChapterId = 999, positionMs = 9_000, speed = null),
            chapters = listOf(chapter(10, 0, 1_000)),
            defaultSpeed = 1.25f,
        )

        assertEquals(0, plan?.startIndex)
        assertEquals(1_000L, plan?.startPositionMs)
        assertEquals(1.25f, plan?.speed)
    }

    @Test
    fun resumePlan_returnsNullForEmptyQueue() {
        assertNull(createPlaybackResumePlan(book(10, 0, null), emptyList(), 1.0f))
    }

    @Test
    fun skipPolicy_neverSkipsPermissionErrorsOrPastQueueEnd() {
        assertTrue(shouldSkipPlaybackError(PlaybackErrorPolicy.SKIP, false, true))
        assertFalse(shouldSkipPlaybackError(PlaybackErrorPolicy.SKIP, true, true))
        assertFalse(shouldSkipPlaybackError(PlaybackErrorPolicy.SKIP, false, false))
        assertFalse(shouldSkipPlaybackError(PlaybackErrorPolicy.STOP, false, true))
    }

    private fun book(
        currentChapterId: Long?,
        positionMs: Long,
        speed: Float?,
    ) = Book(
        id = 1,
        title = "Book",
        author = null,
        coverPath = null,
        rootUri = "content://book",
        totalDurationMs = 3_000,
        lastPlayedAt = 1,
        currentChapterId = currentChapterId,
        currentPositionMs = positionMs,
        linearPositionMs = positionMs,
        createdAt = 1,
        needsReauth = false,
        playbackSpeed = speed,
    )

    private fun chapter(id: Long, index: Int, durationMs: Long) = Chapter(
        id = id,
        bookId = 1,
        title = "Chapter $id",
        uri = "content://chapter/$id",
        index = index,
        durationMs = durationMs,
        fileName = "$id.mp3",
    )
}
