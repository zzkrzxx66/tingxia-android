package com.tingxia.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ChapterMediaItemTest {
    @Test
    fun skipOffsets_populateClippingConfiguration() {
        val item = testChapter().toMediaItem(testBook(skipIntroMs = 5_000L, skipOutroMs = 10_000L))

        assertEquals(5_000L, item.clippingConfiguration.startPositionMs)
        assertEquals(50_000L, item.clippingConfiguration.endPositionMs)
    }

    @Test
    fun zeroOffsets_leaveClippingUnset() {
        val item = testChapter().toMediaItem(testBook(skipIntroMs = 0L, skipOutroMs = 0L))

        assertEquals(0L, item.clippingConfiguration.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, item.clippingConfiguration.endPositionMs)
        assertEquals(MediaItem.ClippingConfiguration.UNSET, item.clippingConfiguration)
    }

    @Test
    fun unknownDuration_clipsOnlyIntro() {
        val item = testChapter(durationMs = 0L)
            .toMediaItem(testBook(skipIntroMs = 5_000L, skipOutroMs = 10_000L))

        assertEquals(5_000L, item.clippingConfiguration.startPositionMs)
        assertEquals(C.TIME_END_OF_SOURCE, item.clippingConfiguration.endPositionMs)
    }

    /**
     * Media3 transfers controller media items to the session through this bundle
     * variant; the clip must survive it or skipping silently stops working.
     */
    @Test
    fun clipping_survivesControllerToSessionBundleRoundTrip() {
        val item = testChapter().toMediaItem(testBook(skipIntroMs = 5_000L, skipOutroMs = 10_000L))

        val restored = MediaItem.fromBundle(item.toBundleIncludeLocalConfiguration())

        assertEquals(5_000L, restored.clippingConfiguration.startPositionMs)
        assertEquals(50_000L, restored.clippingConfiguration.endPositionMs)
        assertNotNull(restored.localConfiguration)
        assertEquals(item.localConfiguration?.uri, restored.localConfiguration?.uri)
        assertEquals(item.mediaId, restored.mediaId)
    }
}

private fun testBook(skipIntroMs: Long, skipOutroMs: Long) = Book(
    id = 1L,
    title = "测试书籍",
    author = null,
    coverPath = null,
    rootUri = "content://test/root",
    totalDurationMs = 60_000L,
    lastPlayedAt = 0L,
    currentChapterId = null,
    currentPositionMs = 0L,
    linearPositionMs = 0L,
    createdAt = 0L,
    needsReauth = false,
    skipIntroMs = skipIntroMs,
    skipOutroMs = skipOutroMs,
)

private fun testChapter(durationMs: Long = 60_000L) = Chapter(
    id = 7L,
    bookId = 1L,
    title = "第一章",
    uri = "content://test/root/chapter1.mp3",
    index = 0,
    durationMs = durationMs,
    fileName = "chapter1.mp3",
)
