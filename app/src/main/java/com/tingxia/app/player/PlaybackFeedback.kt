package com.tingxia.app.player

/**
 * Timing policy for the loading / buffering feedback the player UI shows.
 *
 * Kept free of Android and Compose types so the thresholds are unit-testable: getting
 * these wrong is what makes streaming playback feel either laggy (no feedback at all)
 * or twitchy (a spinner flashing on every 200 ms hiccup).
 */
object PlaybackFeedback {
    /** How often [PlayerController] polls the session for position/buffer. */
    const val POLL_INTERVAL_MS = 500L

    /** A stall shorter than this never reaches the screen. */
    const val BUFFERING_GRACE_MS = 350L

    /** Jumps larger than one poll interval (plus slack) are seeks: snap, don't glide. */
    const val SNAP_THRESHOLD_MS = 1_200f

    fun shouldSnap(fromMs: Float, toMs: Float): Boolean =
        kotlin.math.abs(toMs - fromMs) > SNAP_THRESHOLD_MS

    /**
     * Buffered head as a 0..1 fraction, or null when there is nothing worth drawing:
     * unknown duration, or the buffer has not run ahead of the playhead yet (a local
     * file reports the whole chapter instantly, so the band would just be noise).
     */
    fun bufferedFraction(bufferedMs: Long, positionMs: Long, durationMs: Long): Float? {
        if (durationMs <= 0L) return null
        val buffered = (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val played = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        return if (buffered <= played + 0.004f) null else buffered
    }
}
