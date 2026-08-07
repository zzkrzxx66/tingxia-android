package com.tingxia.app.player

data class ChapterClip(
    val startMs: Long,
    val endMs: Long?,
) {
    val playableDurationMs: Long?
        get() = endMs?.let { (it - startMs).coerceAtLeast(0L) }
}

/**
 * Clip window for one queue item, in source-file coordinates.
 *
 * An embedded m4b chapter supplies the outer window ([clipStartMs]/[clipEndMs]);
 * the book's intro/outro skipping is applied inside that window.
 */
fun chapterClip(
    durationMs: Long,
    skipIntroMs: Long,
    skipOutroMs: Long,
    clipStartMs: Long? = null,
    clipEndMs: Long? = null,
): ChapterClip {
    val windowStart = clipStartMs?.coerceAtLeast(0L) ?: 0L
    val windowEnd = clipEndMs?.takeIf { it > windowStart }
    val windowDuration = when {
        windowEnd != null -> windowEnd - windowStart
        durationMs > 0L -> (durationMs - windowStart).coerceAtLeast(0L)
        else -> 0L
    }
    val intro = skipIntroMs.coerceAtLeast(0L)
    val outro = skipOutroMs.coerceAtLeast(0L)
    if (intro == 0L && outro == 0L) {
        return if (windowStart == 0L && windowEnd == null) {
            ChapterClip(startMs = 0L, endMs = null)
        } else {
            ChapterClip(startMs = windowStart, endMs = windowEnd)
        }
    }
    if (windowDuration <= 0L) return ChapterClip(startMs = windowStart + intro, endMs = windowEnd)

    val minimumPlayableMs = minOf(MINIMUM_PLAYABLE_MS, windowDuration)
    val start = windowStart + intro.coerceAtMost(windowDuration - minimumPlayableMs)
    val end = if (windowEnd != null || durationMs > 0L) {
        val resolvedEnd = windowEnd ?: durationMs
        (resolvedEnd - outro).coerceIn(start + minimumPlayableMs, resolvedEnd)
    } else {
        // Unknown duration and no embedded window: leave the end open and rely on
        // the media end (intro still applies).
        null
    }
    return ChapterClip(startMs = start, endMs = end)
}

fun clampToChapterClip(positionMs: Long, clip: ChapterClip, sourceDurationMs: Long = 0L): Long =
    (clip.playableDurationMs ?: sourceDurationMs.takeIf { it > 0L })
        ?.let { positionMs.coerceIn(0L, it) }
        ?: positionMs.coerceAtLeast(0L)

/**
 * Player positions are relative to the active clip window; the source file is the
 * only coordinate system shared across different skip settings.
 */
fun clipRelativeToAbsolute(positionMs: Long, clip: ChapterClip): Long =
    clip.startMs + positionMs.coerceAtLeast(0L)

fun absoluteToClipRelative(positionMs: Long, clip: ChapterClip, sourceDurationMs: Long = 0L): Long =
    clampToChapterClip(positionMs - clip.startMs, clip, sourceDurationMs)

const val MINIMUM_PLAYABLE_MS = 1_000L
