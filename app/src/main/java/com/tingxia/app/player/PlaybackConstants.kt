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
    val MINUTES = listOf(0, 10, 15, 30, 45, 60)

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
    const val SET_SLEEP_MODE = "SET_SLEEP_MODE"
    const val SET_BOOK_SPEED = "SET_BOOK_SPEED"
    const val SET_AUTO_PLAY_NEXT = "SET_AUTO_PLAY_NEXT"
    const val PREPARE_LIBRARY_MUTATION = "PREPARE_LIBRARY_MUTATION"
}

object PlaybackStateKeys {
    const val SLEEP_MODE = "runtime_sleep_mode"
    const val SLEEP_END_ELAPSED_MS = "runtime_sleep_end_elapsed_ms"
    const val SLEEP_TARGET_CHAPTER_ID = "runtime_sleep_target_chapter_id"
    const val MUTATION_BOOK_ID = "mutation_book_id"
    const val MUTATION_CLEAR_PLAYLIST = "mutation_clear_playlist"
    const val MUTATION_WAS_ACTIVE = "mutation_was_active"
    const val MUTATION_WAS_PLAYING = "mutation_was_playing"
    const val MUTATION_CHAPTER_ID = "mutation_chapter_id"
    const val MUTATION_POSITION_MS = "mutation_position_ms"
}
