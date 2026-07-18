package com.tingxia.app.player

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.repo.PlaybackErrorPolicy

data class PlaybackResumePlan(
    val startIndex: Int,
    val startPositionMs: Long,
    val speed: Float,
)

fun createPlaybackResumePlan(
    book: Book,
    chapters: List<Chapter>,
    defaultSpeed: Float,
): PlaybackResumePlan? {
    if (chapters.isEmpty()) return null
    val startIndex = chapters.indexOfFirst { it.id == book.currentChapterId }
        .takeIf { it >= 0 } ?: 0
    val chapter = chapters[startIndex]
    val position = if (chapter.durationMs > 0L) {
        book.currentPositionMs.coerceIn(0L, chapter.durationMs)
    } else {
        book.currentPositionMs.coerceAtLeast(0L)
    }
    return PlaybackResumePlan(
        startIndex = startIndex,
        startPositionMs = position,
        speed = book.playbackSpeed ?: defaultSpeed,
    )
}

fun shouldSkipPlaybackError(
    policy: PlaybackErrorPolicy,
    isPermissionError: Boolean,
    hasNextChapter: Boolean,
): Boolean = policy == PlaybackErrorPolicy.SKIP && !isPermissionError && hasNextChapter
