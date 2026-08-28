package com.tingxia.app.ui.chapters

import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.data.model.ChapterOrder

/**
 * Search / order / filter / multi-select state for a chapter list.
 *
 * Shared by the book-detail list and the player's picker sheet, and mutated through the pure
 * helpers below so both view models stay one-liners (and the rules stay unit testable).
 */
data class ChapterListControls(
    val query: String = "",
    val searchOpen: Boolean = false,
    val order: ChapterOrder = ChapterOrder.ASC,
    val filter: ChapterFilter = ChapterFilter.ALL,
    val selection: Set<Long> = emptySet(),
    val selectionMode: Boolean = false,
) {
    /** True when search or filter hides part of the book, i.e. group headers stop making sense. */
    val hasNarrowing: Boolean get() = query.isNotBlank() || filter != ChapterFilter.ALL

    fun withQuery(value: String): ChapterListControls = copy(query = value)

    /** Closing the search box also clears the term, otherwise the list stays silently filtered. */
    fun toggleSearch(): ChapterListControls {
        val open = !searchOpen
        return copy(searchOpen = open, query = if (open) query else "")
    }

    fun toggleOrder(): ChapterListControls = copy(
        order = if (order == ChapterOrder.ASC) ChapterOrder.DESC else ChapterOrder.ASC,
    )

    fun withFilter(value: ChapterFilter): ChapterListControls = copy(filter = value)

    fun startSelection(chapterId: Long): ChapterListControls =
        copy(selection = setOf(chapterId), selectionMode = true)

    /** Deselecting the last chapter leaves selection mode; no empty action bar. */
    fun toggleSelection(chapterId: Long): ChapterListControls {
        val next = if (chapterId in selection) selection - chapterId else selection + chapterId
        return copy(selection = next, selectionMode = next.isNotEmpty())
    }

    fun selectAll(chapterIds: Collection<Long>): ChapterListControls =
        copy(selection = chapterIds.toSet(), selectionMode = chapterIds.isNotEmpty())

    fun clearSelection(): ChapterListControls = copy(selection = emptySet(), selectionMode = false)

    /** Drops selected ids that no longer exist (a rescan can delete chapters mid-selection). */
    fun retainExisting(existingIds: Set<Long>): ChapterListControls {
        if (selection.isEmpty()) return this
        val kept = selection.intersect(existingIds)
        if (kept.size == selection.size) return this
        return copy(selection = kept, selectionMode = kept.isNotEmpty())
    }

    /** State to return to when a sheet closes: transient bits go, preferences stay. */
    fun collapsed(): ChapterListControls =
        copy(query = "", searchOpen = false, selection = emptySet(), selectionMode = false)
}
