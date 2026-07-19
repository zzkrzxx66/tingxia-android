package com.tingxia.app.data.backup

import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import com.tingxia.app.data.repo.PreferencesSnapshot
import com.tingxia.app.data.repo.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun roundTrip_preservesLibraryMetadataAndSettings() {
        val source = BackupDocument(
            formatVersion = BackupCodec.CURRENT_FORMAT_VERSION,
            exportedAt = 123L,
            settings = PreferencesSnapshot(
                themeMode = ThemeMode.DARK,
                defaultSpeed = 1.25f,
                shelfSort = ShelfSort.TITLE,
                shelfFilter = ShelfFilter.COMPLETED,
                playbackErrorPolicy = PlaybackErrorPolicy.SKIP,
            ),
            books = listOf(
                BackupBook(
                    title = "测试书",
                    author = "作者",
                    rootUri = "content://tree/book",
                    totalDurationMs = 10_000L,
                    lastPlayedAt = 99L,
                    currentChapterKey = "stable-key",
                    currentPositionMs = 500L,
                    listenedDurationMs = 1_500L,
                    createdAt = 10L,
                    playbackSpeed = 1.5f,
                    autoPlayNext = false,
                    lastScannedAt = 88L,
                    coverUri = "content://cover",
                    chapters = listOf(
                        BackupChapter(
                            key = "stable-key",
                            title = "第一章",
                            uri = "content://chapter/1",
                            relativePath = "第一章.mp3",
                            fileName = "第一章.mp3",
                            fileSize = 12L,
                            documentId = "doc-1",
                            mimeType = "audio/mpeg",
                            durationMs = 10_000L,
                            index = 0,
                            customTitle = "开场",
                            completionState = 1,
                            completedAt = null,
                        ),
                    ),
                    bookmarks = listOf(
                        BackupBookmark("stable-key", 100L, "重点", 77L),
                    ),
                ),
            ),
        )

        val restored = BackupCodec.decode(BackupCodec.encode(source))

        assertEquals(source.formatVersion, restored.formatVersion)
        assertEquals(source.exportedAt, restored.exportedAt)
        assertEquals(source.settings, restored.settings)
        assertEquals(source.books.single().title, restored.books.single().title)
        assertEquals(source.books.single().chapters.single().customTitle, restored.books.single().chapters.single().customTitle)
        assertEquals(source.books.single().bookmarks.single().note, restored.books.single().bookmarks.single().note)
        assertNull(restored.books.single().chapters.single().completedAt)
    }

    @Test
    fun decode_rejectsUnknownFormatVersion() {
        val thrown = runCatching {
            BackupCodec.decode("{\"formatVersion\":99,\"exportedAt\":0,\"settings\":{},\"books\":[]}")
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }
}
