package com.tingxia.app.player

data class ChapterClip(
    val startMs: Long,
    val endMs: Long?,
) {
    val playableDurationMs: Long?
        get() = endMs?.let { (it - startMs).coerceAtLeast(0L) }
}

fun chapterClip(
    durationMs: Long,
    skipIntroMs: Long,
    skipOutroMs: Long,
): ChapterClip {
    val intro = skipIntroMs.coerceAtLeast(0L)
    val outro = skipOutroMs.coerceAtLeast(0L)
    if (intro == 0L && outro == 0L) return ChapterClip(startMs = 0L, endMs = null)
    if (durationMs <= 0L) return ChapterClip(startMs = intro, endMs = null)

    val minimumPlayableMs = minOf(MINIMUM_PLAYABLE_MS, durationMs)
    val start = intro.coerceAtMost(durationMs - minimumPlayableMs)
    val end = (durationMs - outro).coerceIn(start + minimumPlayableMs, durationMs)
    return ChapterClip(startMs = start, endMs = end)
}

fun clampToChapterClip(positionMs: Long, clip: ChapterClip, sourceDurationMs: Long = 0L): Long =
    (clip.playableDurationMs ?: sourceDurationMs.takeIf { it > 0L })
        ?.let { positionMs.coerceIn(0L, it) }
        ?: positionMs.coerceAtLeast(0L)

private const val MINIMUM_PLAYABLE_MS = 1_000L
