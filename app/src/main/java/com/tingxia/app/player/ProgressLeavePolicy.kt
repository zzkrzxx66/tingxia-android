package com.tingxia.app.player

/**
 * Pure policy for progress writes around media-item transitions.
 * Kept free of Android player types so unit tests exercise real production code.
 */
object ProgressLeavePolicy {
    data class LeaveProgress(
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
    )

    data class CurrentProgress(
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
    )

    /**
     * @param autoAdvance true when the player advanced because the previous item ended.
     * @return progress for the chapter being left, or null if nothing should be written for leave.
     */
    fun progressOnLeave(
        prevBookId: Long?,
        prevChapterId: Long?,
        prevPositionMs: Long,
        prevDurationMs: Long,
        newChapterId: Long?,
        autoAdvance: Boolean,
    ): LeaveProgress? {
        if (prevBookId == null || prevChapterId == null) return null
        if (prevChapterId == newChapterId) return null
        val pos = if (autoAdvance && prevDurationMs > 0L) prevDurationMs else prevPositionMs.coerceAtLeast(0L)
        return LeaveProgress(prevBookId, prevChapterId, pos.coerceAtLeast(0L))
    }

    /**
     * After a transition, always also persist the *new* chapter head so resume
     * lands on the chapter the user is actually on (not the previous one).
     */
    fun progressOnEnter(
        bookId: Long?,
        chapterId: Long?,
        positionMs: Long,
    ): CurrentProgress? {
        if (bookId == null || chapterId == null) return null
        return CurrentProgress(bookId, chapterId, positionMs.coerceAtLeast(0L))
    }
}
