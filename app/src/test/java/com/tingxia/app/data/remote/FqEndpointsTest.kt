package com.tingxia.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FqEndpointsTest {

    @Test
    fun narratedChapterKeepsHistoricalCacheKey() {
        // Caches written by earlier versions used this exact shape; changing it would
        // silently orphan every already-downloaded chapter.
        assertEquals(
            "fqnovel_7088215107158690853_7088605907067915278",
            FqEndpoints.cacheKey("7088215107158690853", "7088605907067915278", "0"),
        )
        assertEquals(
            "fqnovel_7088215107158690853_7088605907067915278",
            FqEndpoints.cacheKey("7088215107158690853", "7088605907067915278", null),
        )
    }

    @Test
    fun ttsVoiceIsPartOfTheCacheKey() {
        // Same novel, same chapter, two voices: two different recordings.
        val first = FqEndpoints.cacheKey("6982529841564224526", "6982735801973113351", "96")
        val second = FqEndpoints.cacheKey("6982529841564224526", "6982735801973113351", "57")
        assertEquals("fqnovel_6982529841564224526_96_6982735801973113351", first)
        assertTrue(first != second)
    }

    @Test
    fun streamUrlCarriesTone() {
        val url = FqEndpoints.streamUrl("6982529841564224526", "6982735801973113351", "96")
        assertTrue(url.endsWith("/audio/stream/6982529841564224526/6982735801973113351?toneId=96"))
    }

    @Test
    fun blankToneFallsBackToNarrated() {
        assertEquals(FqEndpoints.DEFAULT_TONE, FqEndpoints.normalizeTone(""))
        assertEquals(FqEndpoints.DEFAULT_TONE, FqEndpoints.normalizeTone(null))
        assertEquals("96", FqEndpoints.normalizeTone("96"))
    }
}
