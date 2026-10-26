package com.tingxia.app.player

/** Main-thread tracker. Settle the previous state before adopting the new one. */
class ListeningTracker {
    data class Segment(val bookId: Long, val elapsedMs: Long, val endedAtMs: Long)
    private var activeBookId: Long? = null
    private var previousElapsedMs: Long? = null

    fun update(bookId: Long?, isPlaying: Boolean, elapsedMs: Long, wallMs: Long): Segment? {
        val segment = activeBookId?.let { id ->
            previousElapsedMs?.let { previous ->
                (elapsedMs - previous).takeIf { it > 0L }?.let { Segment(id, it, wallMs) }
            }
        }
        activeBookId = bookId?.takeIf { isPlaying && it > 0L }
        previousElapsedMs = elapsedMs
        return segment
    }
}
