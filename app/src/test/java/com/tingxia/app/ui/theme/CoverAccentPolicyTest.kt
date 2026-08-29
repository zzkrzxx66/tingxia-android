package com.tingxia.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverAccentPolicyTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun hueOf(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return CoverAccentPolicy.rgbToHsl(r, g, b).first
    }

    private fun lightnessOf(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return CoverAccentPolicy.rgbToHsl(r, g, b).third
    }

    @Test
    fun pick_followsTheDominantHue() {
        // Mostly crimson with a few blue pixels: the accent must be red, not an average purple.
        val pixels = IntArray(64) { index ->
            if (index < 56) argb(196, 32, 40) else argb(30, 60, 200)
        }

        val hue = hueOf(CoverAccentPolicy.pick(pixels, dark = false)!!)

        assertTrue("hue was $hue", hue < 25.0 || hue > 340.0)
    }

    @Test
    fun pick_survivesTheRedSeam() {
        // Hues straddling 0°: naive averaging lands on cyan, circular mean stays red.
        val pixels = IntArray(64) { index ->
            if (index % 2 == 0) argb(210, 40, 30) else argb(210, 30, 60)
        }

        val hue = hueOf(CoverAccentPolicy.pick(pixels, dark = false)!!)

        assertTrue("hue was $hue", hue < 30.0 || hue > 330.0)
    }

    @Test
    fun pick_ignoresPaperInkAndTransparency() {
        val greyscale = intArrayOf(
            argb(255, 255, 255), argb(250, 250, 248), argb(12, 12, 12), argb(128, 128, 128),
            argb(200, 40, 40, a = 10), // transparent, must not count
        )

        assertNull(CoverAccentPolicy.pick(greyscale, dark = false))
        assertNull(CoverAccentPolicy.pick(IntArray(0), dark = false))
    }

    @Test
    fun pick_landsInALegibleBandForEachScheme() {
        val pixels = IntArray(32) { argb(240, 220, 60) } // bright yellow: unusable as-is on paper

        val light = lightnessOf(CoverAccentPolicy.pick(pixels, dark = false)!!)
        val dark = lightnessOf(CoverAccentPolicy.pick(pixels, dark = true)!!)

        assertEquals(0.42, light, 0.02)
        assertEquals(0.62, dark, 0.02)
        assertTrue(dark > light)
    }

    @Test
    fun hslRoundTripsWithinRoundingError() {
        listOf(
            Triple(0.82, 0.14, 0.16),
            Triple(0.20, 0.45, 0.70),
            Triple(0.50, 0.50, 0.50),
        ).forEach { (r, g, b) ->
            val (h, s, l) = CoverAccentPolicy.rgbToHsl(r, g, b)
            val (r2, g2, b2) = CoverAccentPolicy.hslToRgb(h, s, l)
            assertEquals(r, r2, 0.001)
            assertEquals(g, g2, 0.001)
            assertEquals(b, b2, 0.001)
        }
    }
}
