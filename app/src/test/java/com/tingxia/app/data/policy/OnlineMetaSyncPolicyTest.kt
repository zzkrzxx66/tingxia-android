package com.tingxia.app.data.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineMetaSyncPolicyTest {

    @Test
    fun alignment_reportsExactTruncatedAndNone() {
        assertEquals(OnlineMetaSyncPolicy.TitleAlignment.EXACT, OnlineMetaSyncPolicy.alignment(12, 12))
        assertEquals(OnlineMetaSyncPolicy.TitleAlignment.TRUNCATED, OnlineMetaSyncPolicy.alignment(12, 400))
        assertEquals(OnlineMetaSyncPolicy.TitleAlignment.NONE, OnlineMetaSyncPolicy.alignment(12, 0))
        assertEquals(OnlineMetaSyncPolicy.TitleAlignment.NONE, OnlineMetaSyncPolicy.alignment(0, 5))
    }

    @Test
    fun chapterTitleUpdates_alignsLeadingChaptersAndSkipsBlanks() {
        val updates = OnlineMetaSyncPolicy.chapterTitleUpdates(
            localChapterIds = listOf(7L, 8L, 9L),
            remoteTitles = listOf("第一章 起势", "   ", "第三章 收束", "第四章 多余"),
        )

        assertEquals(mapOf(7L to "第一章 起势", 9L to "第三章 收束"), updates)
    }

    @Test
    fun mergeBackup_keepsOriginalValuesAcrossRepeatedSyncs() {
        val first = OnlineMetaSyncPolicy.mergeBackup(
            existing = null,
            currentAuthor = "本地作者",
            currentCoverPath = "/data/covers/manual_cover_1.img",
            currentDescription = null,
            currentCategory = null,
            currentWordCount = 0L,
            chaptersAboutToChange = mapOf(1L to null, 2L to "我的标题"),
        )

        val second = OnlineMetaSyncPolicy.mergeBackup(
            existing = first,
            currentAuthor = "在线作者",
            currentCoverPath = "https://cdn/cover.jpg",
            currentDescription = "在线简介",
            currentCategory = "都市",
            currentWordCount = 123_456L,
            chaptersAboutToChange = mapOf(2L to "在线标题", 3L to "另一个本地标题"),
        )

        assertEquals("本地作者", second.author)
        assertEquals("/data/covers/manual_cover_1.img", second.coverPath)
        assertNull(second.description)
        assertNull(second.category)
        assertEquals(0L, second.wordCount)
        // Chapter 2 keeps its pre-first-sync title; chapter 3 is recorded now.
        assertEquals(mapOf(1L to null, 2L to "我的标题", 3L to "另一个本地标题"), second.chapterCustomTitles)
    }

    @Test
    fun encodeDecode_roundTripsNullsAndControlCharacters() {
        val backup = OnlineMetaSyncPolicy.Backup(
            author = null,
            coverPath = "content://tree/a\tb",
            description = "两行\n简介\\转义",
            category = "玄幻",
            wordCount = 42L,
            chapterCustomTitles = mapOf(1L to null, 2L to "带\t制表符", 3L to "普通"),
        )

        val decoded = OnlineMetaSyncPolicy.decode(OnlineMetaSyncPolicy.encode(backup))

        assertEquals(backup, decoded)
    }

    @Test
    fun decode_rejectsBlankAndForeignPayloads() {
        assertNull(OnlineMetaSyncPolicy.decode(null))
        assertNull(OnlineMetaSyncPolicy.decode(""))
        assertNull(OnlineMetaSyncPolicy.decode("{\"author\":\"x\"}"))
    }
}
