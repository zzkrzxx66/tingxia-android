package com.tingxia.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterOffsetIndexTest {
    private fun chapters() = listOf(
        Chapter(1, 10, "c1", "u1", 0, 1000, "01.mp3"),
        Chapter(2, 10, "c2", "u2", 1, 2000, "02.mp3"),
        Chapter(3, 10, "c3", "u3", 2, 3000, "03.mp3"),
    )

    @Test
    fun listenedIncludesPrevious() {
        val idx = ChapterOffsetIndex(chapters())
        assertEquals(6000L, idx.totalDurationMs)
        assertEquals(1500L, idx.linearPositionMs(2, 500))
        assertEquals(3100L, idx.linearPositionMs(3, 100))
        assertEquals(1000L, idx.linearPositionMs(1, 99999))
    }
}
