package com.tingxia.app.data.model

import com.tingxia.app.data.db.BookEntity
import com.tingxia.app.data.db.ChapterEntity

data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val rootUri: String,
    val totalDurationMs: Long,
    val lastPlayedAt: Long,
    val currentChapterId: Long?,
    val currentPositionMs: Long,
    val listenedDurationMs: Long,
    val createdAt: Long,
    val needsReauth: Boolean,
) {
    val progressFraction: Float
        get() {
            if (totalDurationMs <= 0L) return 0f
            return (listenedDurationMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        }
}

data class Chapter(
    val id: Long,
    val bookId: Long,
    val title: String,
    val uri: String,
    val index: Int,
    val durationMs: Long,
    val fileName: String,
)

object ProgressCalculator {
    /**
     * Listened duration = sum of full durations of chapters before current + position in current.
     */
    fun listenedDurationMs(
        chapters: List<Chapter>,
        currentChapterId: Long?,
        currentPositionMs: Long,
    ): Long {
        if (chapters.isEmpty()) return 0L
        val sorted = chapters.sortedBy { it.index }
        val currentIndex = currentChapterId?.let { id -> sorted.indexOfFirst { it.id == id } } ?: -1
        if (currentIndex < 0) {
            return currentPositionMs.coerceAtLeast(0L)
        }
        val before = sorted.take(currentIndex).sumOf { it.durationMs.coerceAtLeast(0L) }
        val currentDur = sorted[currentIndex].durationMs.coerceAtLeast(0L)
        val pos = if (currentDur > 0L) {
            currentPositionMs.coerceIn(0L, currentDur)
        } else {
            currentPositionMs.coerceAtLeast(0L)
        }
        return before + pos
    }

    fun progressFraction(
        chapters: List<Chapter>,
        currentChapterId: Long?,
        currentPositionMs: Long,
        totalDurationMs: Long = chapters.sumOf { it.durationMs.coerceAtLeast(0L) },
    ): Float {
        if (totalDurationMs <= 0L) return 0f
        val listened = listenedDurationMs(chapters, currentChapterId, currentPositionMs)
        return (listened.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }
}

fun BookEntity.toModel() = Book(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath,
    rootUri = rootUri,
    totalDurationMs = totalDurationMs,
    lastPlayedAt = lastPlayedAt,
    currentChapterId = currentChapterId,
    currentPositionMs = currentPositionMs,
    listenedDurationMs = listenedDurationMs,
    createdAt = createdAt,
    needsReauth = needsReauth,
)

fun ChapterEntity.toModel() = Chapter(
    id = id,
    bookId = bookId,
    title = title,
    uri = uri,
    index = index,
    durationMs = durationMs,
    fileName = fileName,
)
