package com.tingxia.app.player

/**
 * Shared constants and playback-related helpers used by service and UI.
 */
object PlaybackSpeeds {
    val ALL = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)

    fun label(speed: Float): String {
        return if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}x"
        } else {
            "${speed}x"
        }
    }
}

object SleepOptions {
    /** minutes; 0 = off */
    val MINUTES = listOf(0, 15, 30, 45, 60)

    fun label(minutes: Int): String = if (minutes == 0) "关闭" else "${minutes} 分钟"
}

object SeekOffsets {
    const val SHORT_MS = 15_000L
    const val LONG_MS = 30_000L
}

/** Custom session command names for Media3 custom actions */
object CustomCommands {
    const val SEEK_BACK_15 = "SEEK_BACK_15"
    const val SEEK_FWD_15 = "SEEK_FWD_15"
    const val SEEK_BACK_30 = "SEEK_BACK_30"
    const val SEEK_FWD_30 = "SEEK_FWD_30"
    const val SET_SPEED = "SET_SPEED"
    const val SET_SLEEP = "SET_SLEEP"
    const val PLAY_BOOK = "PLAY_BOOK"
}
