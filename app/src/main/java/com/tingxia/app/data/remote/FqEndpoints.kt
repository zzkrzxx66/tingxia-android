package com.tingxia.app.data.remote

import com.tingxia.app.BuildConfig
import java.net.URLEncoder

/**
 * Single place that knows where the fqnovel service lives and how a chapter is
 * addressed, so playback, prefetch and the cache cannot drift apart.
 */
object FqEndpoints {

    val baseUrl: String = BuildConfig.FQ_BASE_URL
    val apiToken: String = BuildConfig.FQ_API_TOKEN

    fun normalizeTone(toneId: String?): String = toneId?.takeIf { it.isNotBlank() } ?: DEFAULT_TONE

    /** Playable, Range-capable URL for one chapter. */
    fun streamUrl(audioBookId: String, itemId: String, toneId: String? = DEFAULT_TONE): String {
        val tone = normalizeTone(toneId)
        val token = if (apiToken.isBlank()) "" else "&token=" + URLEncoder.encode(apiToken, "UTF-8")
        return "$baseUrl/audio/stream/$audioBookId/$itemId?toneId=$tone$token"
    }

    /**
     * Cache key for one chapter.
     *
     * Narrated editions (tone 0) keep the historical key so caches built by earlier
     * versions stay valid; synthesized voices add the tone, since the same item id
     * yields a different recording per voice.
     */
    fun cacheKey(audioBookId: String, itemId: String, toneId: String? = DEFAULT_TONE): String {
        val tone = normalizeTone(toneId)
        return if (tone == DEFAULT_TONE) {
            "fqnovel_${audioBookId}_$itemId"
        } else {
            "fqnovel_${audioBookId}_${tone}_$itemId"
        }
    }

    const val DEFAULT_TONE = "0"
}
