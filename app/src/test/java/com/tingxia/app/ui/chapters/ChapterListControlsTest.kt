package com.tingxia.app.ui.chapters

import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.data.model.ChapterOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterListControlsTest {

    @Test
    fun toggleSearch_clearsTermOnClose() {
        val open = ChapterListControls().toggleSearch().withQuery("412")

        assertTrue(open.searchOpen)
        assertEquals("412", open.query)

        val closed = open.toggleSearch()

        assertFalse(closed.searchOpen)
        assertEquals("", closed.query)
    }

    @Test
    fun toggleOrder_flipsBothWays() {
        val desc = ChapterListControls().toggleOrder()
        assertEquals(ChapterOrder.DESC, desc.order)
        assertEquals(ChapterOrder.ASC, desc.toggleOrder().order)
    }

    @Test
    fun selection_leavesModeWhenLastChapterDeselected() {
        val one = ChapterListControls().startSelection(7L)
        assertTrue(one.selectionMode)

        val two = one.toggleSelection(8L)
        assertEquals(setOf(7L, 8L), two.selection)

        val backToOne = two.toggleSelection(8L)
        assertTrue(backToOne.selectionMode)

        val none = backToOne.toggleSelection(7L)
        assertTrue(none.selection.isEmpty())
        assertFalse(none.selectionMode)
    }

    @Test
    fun retainExisting_dropsDeletedChaptersAndExitsWhenEmpty() {
        val controls = ChapterListControls().selectAll(listOf(1L, 2L, 3L))

        val afterRescan = controls.retainExisting(setOf(2L, 3L, 9L))
        assertEquals(setOf(2L, 3L), afterRescan.selection)
        assertTrue(afterRescan.selectionMode)

        val allGone = controls.retainExisting(emptySet())
        assertTrue(allGone.selection.isEmpty())
        assertFalse(allGone.selectionMode)

        // Untouched selections keep identity, so no needless state emission.
        val unchanged = controls.retainExisting(setOf(1L, 2L, 3L))
        assertTrue(unchanged === controls)
    }

    @Test
    fun collapsed_keepsOrderAndFilterButDropsTransientState() {
        val controls = ChapterListControls()
            .toggleOrder()
            .withFilter(ChapterFilter.UNFINISHED)
            .toggleSearch()
            .withQuery("归途")
            .startSelection(5L)

        val collapsed = controls.collapsed()

        assertEquals(ChapterOrder.DESC, collapsed.order)
        assertEquals(ChapterFilter.UNFINISHED, collapsed.filter)
        assertEquals("", collapsed.query)
        assertFalse(collapsed.searchOpen)
        assertFalse(collapsed.selectionMode)
        assertTrue(collapsed.selection.isEmpty())
    }

    @Test
    fun hasNarrowing_tracksQueryAndFilter() {
        assertFalse(ChapterListControls().hasNarrowing)
        assertTrue(ChapterListControls().withQuery("1").hasNarrowing)
        assertFalse(ChapterListControls().withQuery("   ").hasNarrowing)
        assertTrue(ChapterListControls().withFilter(ChapterFilter.CACHED).hasNarrowing)
    }
}
