package com.tingxia.app.data.policy

/** An incomplete chapter sum must not replace a duration supplied for the whole book. */
fun remoteTotalDuration(
    previousTotalMs: Long,
    knownChapterTotalMs: Long,
    hasUnknownChapters: Boolean,
): Long = if (hasUnknownChapters) {
    maxOf(previousTotalMs, knownChapterTotalMs, 0L)
} else {
    knownChapterTotalMs.coerceAtLeast(0L)
}
