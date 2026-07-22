package com.tingxia.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterClippingTest {
    @Test
    fun zeroOffsets_doNotClip() {
        val clip = chapterClip(durationMs = 60_000L, skipIntroMs = 0L, skipOutroMs = 0L)

        assertEquals(0L, clip.startMs)
        assertNull(clip.endMs)
        assertNull(clip.playableDurationMs)
    }

    @Test
    fun normalOffsets_clipBothEnds() {
        val clip = chapterClip(durationMs = 60_000L, skipIntroMs = 5_000L, skipOutroMs = 10_000L)

        assertEquals(5_000L, clip.startMs)
        assertEquals(50_000L, clip.endMs)
        assertEquals(45_000L, clip.playableDurationMs)
    }

    @Test
    fun oversizedOffsets_leaveOneSecondPlayable() {
        val clip = chapterClip(durationMs = 10_000L, skipIntroMs = 300_000L, skipOutroMs = 300_000L)

        assertEquals(9_000L, clip.startMs)
        assertEquals(10_000L, clip.endMs)
        assertEquals(1_000L, clip.playableDurationMs)
    }

    @Test
    fun subSecondChapter_remainsFullyPlayable() {
        val clip = chapterClip(durationMs = 500L, skipIntroMs = 1_000L, skipOutroMs = 1_000L)

        assertEquals(0L, clip.startMs)
        assertEquals(500L, clip.endMs)
    }

    @Test
    fun unknownDuration_appliesOnlyIntroOffset() {
        val clip = chapterClip(durationMs = 0L, skipIntroMs = 5_000L, skipOutroMs = 10_000L)

        assertEquals(5_000L, clip.startMs)
        assertNull(clip.endMs)
    }

    @Test
    fun position_isClampedToPlayableDuration() {
        val clip = chapterClip(durationMs = 10_000L, skipIntroMs = 2_000L, skipOutroMs = 3_000L)

        assertEquals(0L, clampToChapterClip(-1L, clip))
        assertEquals(5_000L, clampToChapterClip(9_000L, clip))
    }
}
