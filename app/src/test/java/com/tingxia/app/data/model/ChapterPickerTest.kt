package com.tingxia.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPickerTest {

    private fun chapter(
        index: Int,
        title: String = "第 ${index + 1} 章",
        completionState: Int = 0,
        cached: Boolean = false,
    ) = Chapter(
        id = (index + 1).toLong(),
        bookId = 1L,
        title = title,
        uri = "content://audio/${index + 1}",
        index = index,
        durationMs = 60_000L,
        fileName = "${index + 1}.mp3",
        completionState = completionState,
        isCached = cached,
    )

    @Test
    fun matches_byTitleAndChapterNumber() {
        val ch = chapter(411, title = "第四百一十二章 归途")

        assertTrue(ChapterPicker.matches(ch, ""))
        assertTrue(ChapterPicker.matches(ch, "归途"))
        assertTrue(ChapterPicker.matches(ch, "412"))
        assertFalse(ChapterPicker.matches(ch, "411"))
        assertFalse(ChapterPicker.matches(ch, "序章"))
    }

    @Test
    fun visible_appliesFilterAndOrder() {
        val chapters = listOf(
            chapter(0, completionState = 2, cached = true),
            chapter(1, completionState = 1),
            chapter(2, cached = true),
        )

        assertEquals(
            listOf(2, 1, 0),
            ChapterPicker.visible(chapters, order = ChapterOrder.DESC).map { it.index },
        )
        assertEquals(
            listOf(1, 2),
            ChapterPicker.visible(chapters, filter = ChapterFilter.UNFINISHED).map { it.index },
        )
        assertEquals(
            listOf(0, 2),
            ChapterPicker.visible(chapters, filter = ChapterFilter.CACHED).map { it.index },
        )
    }

    @Test
    fun visible_sortsByIndexRegardlessOfInputOrder() {
        val shuffled = listOf(chapter(5), chapter(0), chapter(3))

        assertEquals(listOf(0, 3, 5), ChapterPicker.visible(shuffled).map { it.index })
    }

    @Test
    fun group_chunksHundredsAndLabelsRanges() {
        val chapters = (0 until 250).map { chapter(it) }
        val groups = ChapterPicker.group(ChapterPicker.visible(chapters))

        assertEquals(3, groups.size)
        assertEquals(1..100, groups[0].label)
        assertEquals(101..200, groups[1].label)
        assertEquals(201..250, groups[2].label)
    }

    @Test
    fun group_labelsDescendingBlocksAscending() {
        val chapters = (0 until 150).map { chapter(it) }
        val groups = ChapterPicker.group(
            ChapterPicker.visible(chapters, order = ChapterOrder.DESC),
        )

        assertEquals(2, groups.size)
        assertEquals(51..150, groups[0].label)
        assertEquals(150, groups[0].chapters.first().index + 1)
        assertEquals(1..50, groups[1].label)
    }

    @Test
    fun group_dropsHeadersWhenNarrowed() {
        val chapters = (0 until 250).map { chapter(it) }
        val narrowed = ChapterPicker.visible(chapters, query = "1")

        val groups = ChapterPicker.group(narrowed, grouped = false)

        assertEquals(1, groups.size)
        assertNull(groups.single().label)
        assertFalse(ChapterPicker.shouldGroup("1", ChapterFilter.ALL))
        assertFalse(ChapterPicker.shouldGroup("", ChapterFilter.CACHED))
        assertTrue(ChapterPicker.shouldGroup("  ", ChapterFilter.ALL))
    }

    @Test
    fun flatIndexOf_countsHeaderRows() {
        val chapters = (0 until 250).map { chapter(it) }
        val groups = ChapterPicker.group(ChapterPicker.visible(chapters))

        // group 0 header, rows 1..100, group 1 header at 101, chapter 101 at 102
        assertEquals(1, ChapterPicker.flatIndexOf(groups, chapters[0].id))
        assertEquals(100, ChapterPicker.flatIndexOf(groups, chapters[99].id))
        assertEquals(102, ChapterPicker.flatIndexOf(groups, chapters[100].id))
        assertNull(ChapterPicker.flatIndexOf(groups, 99_999L))
    }

    @Test
    fun flatIndexOf_withoutHeadersIsPlainPosition() {
        val chapters = (0 until 5).map { chapter(it) }
        val groups = ChapterPicker.group(ChapterPicker.visible(chapters), grouped = false)

        assertEquals(0, ChapterPicker.flatIndexOf(groups, chapters[0].id))
        assertEquals(4, ChapterPicker.flatIndexOf(groups, chapters[4].id))
    }
}
