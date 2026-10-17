package com.tingxia.app.data.db

/** Current recording identity, including voice, used when reconciling disk and Room. */
data class ChapterCacheEntry(
    val chapterId: Long,
    val audioBookId: String,
    val itemId: String,
    val toneId: String,
    val isCached: Boolean,
)
