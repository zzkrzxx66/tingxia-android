package com.tingxia.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Documents the correct progress payload when leaving a chapter.
 * AUTO transition should treat previous chapter as finished when duration is known.
 */
class ChapterTransitionProgressTest {

    data class LeaveProgress(
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
    )

    private fun progressOnLeave(
        prevBookId: Long?,
        prevChapterId: Long?,
        prevPositionMs: Long,
        prevDurationMs: Long,
        newChapterId: Long?,
        autoAdvance: Boolean,
    ): LeaveProgress? {
        if (prevBookId == null || prevChapterId == null) return null
        if (prevChapterId == newChapterId) return null
        val pos = if (autoAdvance && prevDurationMs > 0L) prevDurationMs else prevPositionMs
        return LeaveProgress(prevBookId, prevChapterId, pos)
    }

    @Test
    fun autoAdvance_savesCompletedPosition() {
        val p = progressOnLeave(1, 10, prevPositionMs = 900, prevDurationMs = 1000, newChapterId = 11, autoAdvance = true)
        assertEquals(LeaveProgress(1, 10, 1000), p)
    }

    @Test
    fun manualNext_savesLastKnownPosition() {
        val p = progressOnLeave(1, 10, prevPositionMs = 400, prevDurationMs = 1000, newChapterId = 11, autoAdvance = false)
        assertEquals(LeaveProgress(1, 10, 400), p)
    }

    @Test
    fun sameChapter_noLeaveSave() {
        val p = progressOnLeave(1, 10, 400, 1000, newChapterId = 10, autoAdvance = false)
        assertEquals(null, p)
    }

    @Test
    fun missingIds_noSave() {
        assertEquals(null, progressOnLeave(null, 10, 1, 1, 11, true))
        assertEquals(null, progressOnLeave(1, null, 1, 1, 11, true))
    }
}
