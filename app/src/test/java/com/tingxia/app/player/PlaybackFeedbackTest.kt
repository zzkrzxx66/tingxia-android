package com.tingxia.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFeedbackTest {

    @Test
    fun `one poll interval of drift glides`() {
        assertFalse(PlaybackFeedback.shouldSnap(10_000f, 10_500f))
    }

    @Test
    fun `double speed drift still glides`() {
        // At 2x the playhead advances ~1s per 500ms poll; that must not snap.
        assertFalse(PlaybackFeedback.shouldSnap(10_000f, 11_000f))
    }

    @Test
    fun `a seek snaps`() {
        assertTrue(PlaybackFeedback.shouldSnap(10_000f, 40_000f))
        assertTrue(PlaybackFeedback.shouldSnap(40_000f, 10_000f))
    }

    @Test
    fun `chapter change snaps back to zero`() {
        assertTrue(PlaybackFeedback.shouldSnap(1_800_000f, 0f))
    }

    @Test
    fun `unknown duration has no buffered band`() {
        assertNull(PlaybackFeedback.bufferedFraction(bufferedMs = 5_000, positionMs = 0, durationMs = 0))
    }

    @Test
    fun `buffer behind the playhead has no band`() {
        assertNull(
            PlaybackFeedback.bufferedFraction(
                bufferedMs = 30_000,
                positionMs = 30_000,
                durationMs = 600_000,
            ),
        )
    }

    @Test
    fun `buffered head ahead of the playhead is a fraction`() {
        val fraction = PlaybackFeedback.bufferedFraction(
            bufferedMs = 300_000,
            positionMs = 60_000,
            durationMs = 600_000,
        )
        assertEquals(0.5f, fraction!!, 0.0001f)
    }

    @Test
    fun `buffered past the end clamps to one`() {
        val fraction = PlaybackFeedback.bufferedFraction(
            bufferedMs = 900_000,
            positionMs = 60_000,
            durationMs = 600_000,
        )
        assertEquals(1f, fraction!!, 0.0001f)
    }
}
