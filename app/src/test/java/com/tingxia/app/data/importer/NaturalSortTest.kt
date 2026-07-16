package com.tingxia.app.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {
    @Test
    fun chapterOrder_numericAware() {
        val files = listOf("第10章.mp3", "第2章.mp3", "第1章.mp3", "前言.mp3")
        val sorted = files.sortedWith { a, b -> FolderScanner.naturalCompare(a, b) }
        assertEquals(listOf("前言.mp3", "第1章.mp3", "第2章.mp3", "第10章.mp3"), sorted)
    }

    @Test
    fun isAudio_byExtension() {
        assertTrue(FolderScanner.isAudio("a.mp3", null))
        assertTrue(FolderScanner.isAudio("b.m4b", null))
        assertTrue(FolderScanner.isAudio("c.flac", "application/octet-stream"))
        assertTrue(FolderScanner.isAudio("x.bin", "audio/mpeg"))
    }

    @Test
    fun stripExtension() {
        assertEquals("第1章", FolderScanner.stripExtension("第1章.mp3"))
        assertEquals("noext", FolderScanner.stripExtension("noext"))
    }
}
