package com.tingxia.app.player

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

sealed interface SleepTimerMode {
    data object Off : SleepTimerMode
    data class AfterDuration(val durationMs: Long) : SleepTimerMode
    data object EndOfChapter : SleepTimerMode
}

object SleepOptions {
    val MINUTES = listOf(0, 15, 30, 45, 60)

    fun label(minutes: Int): String = when (minutes) {
        0 -> "关闭"
        -1 -> "本章结束"
        else -> "${minutes} 分钟"
    }
}

object SeekOffsets {
    const val SHORT_MS = 15_000L
    const val LONG_MS = 30_000L
}

object CustomCommands {
    const val SEEK_BACK_15 = "SEEK_BACK_15"
    const val SEEK_FWD_15 = "SEEK_FWD_15"
    const val SEEK_BACK_30 = "SEEK_BACK_30"
    const val SEEK_FWD_30 = "SEEK_FWD_30"
    const val SET_SPEED = "SET_SPEED"
    const val SET_SLEEP = "SET_SLEEP"
    const val SET_SLEEP_MODE = "SET_SLEEP_MODE"
    const val SET_BOOK_SPEED = "SET_BOOK_SPEED"
    const val PLAY_BOOK = "PLAY_BOOK"
}
