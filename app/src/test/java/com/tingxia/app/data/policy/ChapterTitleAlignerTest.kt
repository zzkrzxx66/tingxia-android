package com.tingxia.app.data.policy

import com.tingxia.app.data.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTitleAlignerTest {

    private fun chapter(
        id: Long,
        index: Int,
        title: String,
        fileName: String = "$title.mp3",
    ) = Chapter(
        id = id,
        bookId = 1L,
        title = title,
        uri = "content://audio/$id",
        index = index,
        durationMs = 60_000L,
        fileName = fileName,
    )

    @Test
    fun parseNumber_readsArabicForms() {
        assertEquals(412, ChapterTitleAligner.parseNumber("412"))
        assertEquals(412, ChapterTitleAligner.parseNumber("第412章.mp3"))
        assertEquals(412, ChapterTitleAligner.parseNumber("412_归途.m4a"))
        assertEquals(412, ChapterTitleAligner.parseNumber("EP412"))
        assertEquals(412, ChapterTitleAligner.parseNumber("0412 归途"))
        assertEquals(413, ChapterTitleAligner.parseNumber("第413章(上)"))
        // The 第…章 marker wins over a leading track number.
        assertEquals(412, ChapterTitleAligner.parseNumber("001_第412章.mp3"))
        assertEquals(1, ChapterTitleAligner.parseNumber("001 片头.mp3"))
    }

    @Test
    fun parseNumber_readsChineseNumerals() {
        assertEquals(10, ChapterTitleAligner.parseNumber("第十章"))
        assertEquals(23, ChapterTitleAligner.parseNumber("第二十三章 雪夜"))
        assertEquals(108, ChapterTitleAligner.parseNumber("第一百零八章"))
        assertEquals(412, ChapterTitleAligner.parseNumber("第四百一十二章 归途"))
        assertEquals(1024, ChapterTitleAligner.parseNumber("第一千零二十四集"))
        assertEquals(10_005, ChapterTitleAligner.parseNumber("第一万零五章"))
        assertEquals(2, ChapterTitleAligner.parseNumber("第两章"))
    }

    @Test
    fun parseNumber_returnsNullWhenNothingReadable() {
        assertNull(ChapterTitleAligner.parseNumber("片头.mp3"))
        assertNull(ChapterTitleAligner.parseNumber(""))
        assertNull(ChapterTitleAligner.parseNumber("序章"))
    }

    @Test
    fun byNumber_skipsIntroFileAndSurvivesDrift() {
        // Local starts with a 片头 file, so positional alignment would be off by one.
        val local = listOf(
            chapter(1L, 0, "000 片头"),
            chapter(2L, 1, "第1章"),
            chapter(3L, 2, "第2章"),
        )
        val remote = listOf("第一章 起势", "第二章 落子", "第三章 收束")

        val plan = ChapterTitleAligner.byNumber(local, remote)

        assertEquals(
            mapOf(2L to "第一章 起势", 3L to "第二章 落子"),
            plan.updates,
        )
        assertEquals(2, plan.matchedCount)
        assertEquals(1, plan.unmatchedCount)
        // 片头 has no readable number and keeps its title.
        assertNull(plan.preview.first().remoteTitle)
    }

    @Test
    fun byNumber_handlesLocalStartingMidBookAndSplitParts() {
        val local = listOf(
            chapter(10L, 0, "第30章"),
            chapter(11L, 1, "第31章(上)"),
            chapter(12L, 2, "第31章(下)"),
        )
        val remote = (1..40).map { "第 $it 章" }

        val plan = ChapterTitleAligner.byNumber(local, remote)

        assertEquals("第 30 章", plan.updates[10L])
        // Both halves of a split chapter take the same online title.
        assertEquals("第 31 章", plan.updates[11L])
        assertEquals("第 31 章", plan.updates[12L])
        assertEquals(3, plan.matchedCount)
    }

    @Test
    fun byOffset_shiftsAndClampsInsteadOfWrapping() {
        val local = listOf(
            chapter(1L, 0, "a"),
            chapter(2L, 1, "b"),
            chapter(3L, 2, "c"),
        )
        val remote = listOf("t1", "t2", "t3", "t4")

        val shifted = ChapterTitleAligner.byOffset(local, remote, 1)
        assertEquals(mapOf(1L to "t2", 2L to "t3", 3L to "t4"), shifted.updates)

        val negative = ChapterTitleAligner.byOffset(local, remote, -1)
        assertEquals(mapOf(2L to "t1", 3L to "t2"), negative.updates)
        assertEquals(1, negative.unmatchedCount)

        val overshot = ChapterTitleAligner.byOffset(local, remote, 9)
        assertEquals(emptyMap<Long, String>(), overshot.updates)
        assertEquals(3, overshot.unmatchedCount)
    }

    @Test
    fun offsetRange_spansBothDirections() {
        assertEquals(-2..3, ChapterTitleAligner.offsetRange(3, 4))
        assertEquals(0..0, ChapterTitleAligner.offsetRange(0, 4))
    }

    @Test
    fun suggestedOffset_reportsTheDriftNumberMatchingFound() {
        // A 片头 file pushes every local chapter one position later than the online list.
        val local = listOf(
            chapter(1L, 0, "000 片头"),
            chapter(2L, 1, "第1章"),
            chapter(3L, 2, "第2章"),
        )
        val remote = listOf("第一章 起势", "第二章 落子", "第三章 收束")

        assertEquals(-1, ChapterTitleAligner.suggestedOffset(local, remote))

        // Nothing parseable on either side degrades to no drift rather than a guess.
        assertEquals(
            0,
            ChapterTitleAligner.suggestedOffset(
                listOf(chapter(1L, 0, "片头"), chapter(2L, 1, "序章")),
                listOf("引子", "后记"),
            ),
        )
    }

    @Test
    fun byNumber_keepsFirstTitleWhenRemoteRepeatsANumber() {
        val local = listOf(chapter(1L, 0, "第5章"))
        val remote = listOf("第5章 原版", "第5章 重制版")

        assertEquals(mapOf(1L to "第5章 原版"), ChapterTitleAligner.byNumber(local, remote).updates)
    }
}
