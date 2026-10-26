package com.tingxia.app.player

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter

/** Persistent positions are relative to the original chapter, including its intro. */
fun Chapter.playbackClip(book: Book): ChapterClip = chapterClip(
    durationMs, book.skipIntroMs, book.skipOutroMs, clipStartMs, clipEndMs,
)

fun Chapter.playbackOffsetMs(book: Book): Long =
    (playbackClip(book).startMs - (clipStartMs ?: 0L)).coerceAtLeast(0L)

fun Chapter.toPlaybackPosition(book: Book, chapterPositionMs: Long): Long =
    clampToChapterClip(chapterPositionMs - playbackOffsetMs(book), playbackClip(book), durationMs)

fun Chapter.playableDurationMs(book: Book): Long? {
    if (durationMs <= 0L && clipEndMs == null) return null
    val clip = playbackClip(book)
    return clip.playableDurationMs ?: (durationMs - playbackOffsetMs(book)).coerceAtLeast(0L)
}

/** Null means the remaining catalogue has unknown durations; do not display a confident zero. */
fun remainingPlaybackMs(book: Book, chapters: List<Chapter>, chapterId: Long?, positionMs: Long): Long? {
    val ordered = chapters.filter { it.bookId == book.id }.sortedBy { it.index }
    val current = ordered.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: 0
    if (ordered.isEmpty()) return null
    val remaining = ordered.drop(current)
    if (remaining.any { it.playableDurationMs(book) == null }) return null
    val chapter = remaining.first()
    val consumed = chapter.toPlaybackPosition(book, positionMs)
    return (remaining.sumOf { it.playableDurationMs(book)!! } - consumed).coerceAtLeast(0L)
}
