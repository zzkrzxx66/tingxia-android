package com.tingxia.app.ui.theme

/**
 * Picks the accent colour a book lends to its own progress indicators.
 *
 * Pure ARGB maths so it can be unit tested on the JVM: no Bitmap, no Palette library. The caller
 * hands over a downsampled pixel array; this decides which hue the artwork is actually about and
 * then forces it into a band that stays legible on the app's paper-white and ink-black surfaces.
 */
object CoverAccentPolicy {

    /** Hue buckets: coarse enough that a red cover with orange highlights stays one colour. */
    private const val BUCKETS = 12

    /**
     * @param pixels ARGB pixels of a small (e.g. 32×32) sample of the artwork.
     * @param dark whether the app is in its dark scheme, which needs a lighter accent.
     * @return packed ARGB colour, or null when the artwork has no usable hue (greyscale covers).
     */
    fun pick(pixels: IntArray, dark: Boolean): Int? {
        if (pixels.isEmpty()) return null
        val weights = DoubleArray(BUCKETS)
        val hueSin = DoubleArray(BUCKETS)
        val hueCos = DoubleArray(BUCKETS)
        val satSum = DoubleArray(BUCKETS)
        var counted = 0

        pixels.forEach { argb ->
            val alpha = (argb ushr 24) and 0xFF
            if (alpha < 128) return@forEach
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            val (h, s, l) = rgbToHsl(r, g, b)
            // Ignore paper, ink and mud: they carry no identity and would average to grey.
            if (s < 0.18 || l < 0.12 || l > 0.92) return@forEach
            val bucket = ((h / 360.0) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            // Saturated mid-tones describe a cover better than pale washes.
            val weight = s * (1.0 - kotlin.math.abs(l - 0.5) * 1.2)
            if (weight <= 0.0) return@forEach
            weights[bucket] += weight
            val radians = Math.toRadians(h)
            hueSin[bucket] += kotlin.math.sin(radians) * weight
            hueCos[bucket] += kotlin.math.cos(radians) * weight
            satSum[bucket] += s * weight
            counted++
        }
        if (counted == 0) return null

        var best = 0
        for (i in 1 until BUCKETS) if (weights[i] > weights[best]) best = i
        if (weights[best] <= 0.0) return null

        // Circular mean keeps hues that straddle the 0°/360° seam (reds) from averaging to cyan.
        var hue = Math.toDegrees(kotlin.math.atan2(hueSin[best], hueCos[best]))
        if (hue < 0) hue += 360.0
        val saturation = (satSum[best] / weights[best]).coerceIn(0.42, 0.78)
        val lightness = if (dark) 0.62 else 0.42
        val (r, g, b) = hslToRgb(hue, saturation, lightness)
        return (0xFF shl 24) or
            (((r * 255).toInt().coerceIn(0, 255)) shl 16) or
            (((g * 255).toInt().coerceIn(0, 255)) shl 8) or
            ((b * 255).toInt().coerceIn(0, 255))
    }

    fun rgbToHsl(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2.0
        if (max == min) return Triple(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2.0 - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + if (g < b) 6.0 else 0.0)
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        } * 60.0
        return Triple(h, s, l)
    }

    fun hslToRgb(h: Double, s: Double, l: Double): Triple<Double, Double, Double> {
        if (s == 0.0) return Triple(l, l, l)
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val hk = h / 360.0
        return Triple(
            hueToChannel(p, q, hk + 1.0 / 3.0),
            hueToChannel(p, q, hk),
            hueToChannel(p, q, hk - 1.0 / 3.0),
        )
    }

    private fun hueToChannel(p: Double, q: Double, tRaw: Double): Double {
        var t = tRaw
        if (t < 0) t += 1.0
        if (t > 1) t -= 1.0
        return when {
            t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
            t < 1.0 / 2.0 -> q
            t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
            else -> p
        }
    }
}
