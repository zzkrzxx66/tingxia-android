package com.tingxia.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercises real [ProgressLeavePolicy] production code. */
class ChapterTransitionProgressTest {

    @Test
    fun autoAdvance_savesCompletedPosition() {
        val p = ProgressLeavePolicy.progressOnLeave(
            prevBookId = 1,
            prevChapterId = 10,
            prevPositionMs = 900,
            prevDurationMs = 1000,
            newChapterId = 11,
            autoAdvance = true,
        )
        assertEquals(ProgressLeavePolicy.LeaveProgress(1, 10, 1000), p)
    }

    @Test
    fun manualNext_savesLastKnownPosition() {
        val p = ProgressLeavePolicy.progressOnLeave(
            prevBookId = 1,
            prevChapterId = 10,
            prevPositionMs = 400,
            prevDurationMs = 1000,
            newChapterId = 11,
            autoAdvance = false,
        )
        assertEquals(ProgressLeavePolicy.LeaveProgress(1, 10, 400), p)
    }

    @Test
    fun sameChapter_noLeaveSave() {
        val p = ProgressLeavePolicy.progressOnLeave(1, 10, 400, 1000, 10, false)
        assertNull(p)
    }

    @Test
    fun missingIds_noSave() {
        assertNull(ProgressLeavePolicy.progressOnLeave(null, 10, 1, 1, 11, true))
        assertNull(ProgressLeavePolicy.progressOnLeave(1, null, 1, 1, 11, true))
    }

    @Test
    fun enter_alwaysWritesCurrentHead() {
        val enter = ProgressLeavePolicy.progressOnEnter(1, 11, 0)
        assertEquals(ProgressLeavePolicy.CurrentProgress(1, 11, 0), enter)
        assertNull(ProgressLeavePolicy.progressOnEnter(null, 11, 0))
    }
}
