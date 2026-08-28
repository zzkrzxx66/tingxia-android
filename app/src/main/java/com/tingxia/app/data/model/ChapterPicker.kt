package com.tingxia.app.data.model

/** Ordering for the chapter picker. */
enum class ChapterOrder { ASC, DESC }

/** Row filter for the chapter picker. */
enum class ChapterFilter {
    /** Everything. */
    ALL,

    /** Not finished yet (unplayed or in progress). */
    UNFINISHED,

    /** Already sitting in the offline cache (online books only). */
    CACHED,
}

/** A block of chapters sharing one sticky header; [label] is null when headers are suppressed. */
data class ChapterGroup(
    val label: IntRange?,
    val chapters: List<Chapter>,
)

/**
 * Search / sort / group logic for the chapter picker, kept pure so it can be unit tested and
 * shared by the player sheet and the book-detail list.
 */
object ChapterPicker {

    const val GROUP_SIZE = 100

    /**
     * Matches [query] against the chapter title or its 1-based number. A numeric query matches
     * both the exact number and titles containing the digits, so "412" finds 第四百一十二章 and
     * chapter 412 alike.
     */
    fun matches(chapter: Chapter, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (chapter.displayTitle.contains(q, ignoreCase = true)) return true
        val asNumber = q.toIntOrNull() ?: return false
        return chapter.index + 1 == asNumber
    }

    /** Chapters left after search + filter, in [order]. Input order is irrelevant. */
    fun visible(
        chapters: List<Chapter>,
        query: String = "",
        filter: ChapterFilter = ChapterFilter.ALL,
        order: ChapterOrder = ChapterOrder.ASC,
    ): List<Chapter> {
        val filtered = chapters.asSequence()
            .filter { matches(it, query) }
            .filter { chapter ->
                when (filter) {
                    ChapterFilter.ALL -> true
                    ChapterFilter.UNFINISHED -> chapter.completionState != 2
                    ChapterFilter.CACHED -> chapter.isCached
                }
            }
            .sortedBy { it.index }
            .toList()
        return if (order == ChapterOrder.DESC) filtered.reversed() else filtered
    }

    /**
     * Split [visible] into [GROUP_SIZE] blocks for sticky headers. Grouping is suppressed (one
     * unlabelled group) while a search or filter is active — partial ranges like "第 401–500 章"
     * holding three rows read as a bug.
     */
    fun group(
        visible: List<Chapter>,
        grouped: Boolean = true,
        groupSize: Int = GROUP_SIZE,
    ): List<ChapterGroup> {
        if (visible.isEmpty()) return emptyList()
        if (!grouped || visible.size <= groupSize) {
            val label = if (grouped && visible.size > 1) rangeOf(visible) else null
            return listOf(ChapterGroup(label, visible))
        }
        return visible.chunked(groupSize).map { block -> ChapterGroup(rangeOf(block), block) }
    }

    /** True when neither search nor filter narrows the list, i.e. headers make sense. */
    fun shouldGroup(query: String, filter: ChapterFilter): Boolean =
        query.isBlank() && filter == ChapterFilter.ALL

    /**
     * Lazy-list index of [chapterId], counting one item per labelled header plus one per row.
     * Returns null when the chapter is not in the current view (filtered out, for example).
     */
    fun flatIndexOf(groups: List<ChapterGroup>, chapterId: Long): Int? {
        var index = 0
        groups.forEach { group ->
            if (group.label != null) index++
            group.chapters.forEach { chapter ->
                if (chapter.id == chapterId) return index
                index++
            }
        }
        return null
    }

    private fun rangeOf(block: List<Chapter>): IntRange {
        val first = block.first().index + 1
        val last = block.last().index + 1
        return if (first <= last) first..last else last..first
    }
}
