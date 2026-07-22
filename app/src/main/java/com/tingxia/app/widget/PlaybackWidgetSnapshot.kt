package com.tingxia.app.widget

data class PlaybackWidgetSnapshot(
    val hasMedia: Boolean = false,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
) {
    val progressPermille: Int
        get() {
            if (durationMs <= 0L) return 0
            val position = positionMs.coerceIn(0L, durationMs)
            return (position.toDouble() / durationMs.toDouble() * 1_000.0).toInt()
        }
}

fun formatWidgetDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
