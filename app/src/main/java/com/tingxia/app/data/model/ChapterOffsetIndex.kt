package com.tingxia.app.data.model

/**
 * Precomputes per-chapter start offsets so progress saves avoid O(n) scans each tick.
 * Thread-safe for single-writer (PlaybackService) use; rebuild on chapter list change.
 */
class ChapterOffsetIndex(
    chapters: List<Chapter>,
) {
    /** chapterId -> start offset ms (sum of previous durations) */
    private val startOffsetById: Map<Long, Long>
    private val durationById: Map<Long, Long>
    val totalDurationMs: Long

    init {
        val sorted = chapters.sortedBy { it.index }
        val starts = HashMap<Long, Long>(sorted.size)
        val durs = HashMap<Long, Long>(sorted.size)
        var acc = 0L
        for (ch in sorted) {
            starts[ch.id] = acc
            val d = ch.durationMs.coerceAtLeast(0L)
            durs[ch.id] = d
            acc += d
        }
        startOffsetById = starts
        durationById = durs
        totalDurationMs = acc
    }

    fun linearPositionMs(chapterId: Long?, positionMs: Long): Long {
        if (chapterId == null) return positionMs.coerceAtLeast(0L)
        val start = startOffsetById[chapterId] ?: return positionMs.coerceAtLeast(0L)
        val dur = durationById[chapterId] ?: 0L
        val pos = if (dur > 0L) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
        return start + pos
    }

    @Deprecated("Use linearPositionMs", ReplaceWith("linearPositionMs(chapterId, positionMs)"))
    fun listenedDurationMs(chapterId: Long?, positionMs: Long): Long = linearPositionMs(chapterId, positionMs)

    fun contains(chapterId: Long): Boolean = startOffsetById.containsKey(chapterId)
}
