package com.tingxia.app.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.data.importer.EmbeddedChapterReader
import com.tingxia.app.data.importer.FolderScanner
import com.tingxia.app.data.importer.MetadataReader
import com.tingxia.app.data.remote.FqAudioChapter
import com.tingxia.app.data.remote.FqAudioMeta
import com.tingxia.app.data.repo.BookRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineBookIntegrityTest {
    private lateinit var db: TingXiaDatabase
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, TingXiaDatabase::class.java).build()
        repository = BookRepository(
            context, db, db.bookDao(), db.chapterDao(), db.bookmarkDao(), db.listenSessionDao(),
            FolderScanner(context, MetadataReader(context), EmbeddedChapterReader(context)),
        )
    }

    @After
    fun tearDown() = db.close()


    @Test fun lateDurationAndCatalogueResultsCannotMutateSwitchedVoice() = runBlocking {
        val bookId = remoteBook(tone = "5")
        val chapterId = chapter(bookId, "201", 0, duration = 60000)
        repository.switchTtsTone(bookId, "6")
        repository.recordRemoteChapterDuration(bookId, chapterId, 120000, "5")
        assertEquals(0L, db.chapterDao().getChapter(chapterId)!!.durationMs)
        assertEquals(0L, db.bookDao().getBook(bookId)!!.totalDurationMs)
        val stale = repository.applyRemoteUpdate(bookId, listOf(FqAudioChapter("202", "late", 1)), null, "100", "5")
        assertFalse(stale.checked)
        assertEquals(1, db.chapterDao().getChapters(bookId).size)
        repository.recordRemoteChapterDuration(bookId, chapterId, 90000, "6")
        assertEquals(90000L, db.chapterDao().getChapter(chapterId)!!.durationMs)
    }

    @Test fun listeningAccumulatesAtomicallyIntoOneRowPerDay() = runBlocking {
        val bookId = remoteBook()
        val stats = com.tingxia.app.data.repo.StatsRepository(db.listenSessionDao(), db.bookDao())
        val noon = com.tingxia.app.data.repo.StatsRepository.dayStartMs(System.currentTimeMillis()) + 3600000L
        stats.recordListening(bookId, 500, noon)
        stats.recordListening(bookId, 600, noon)
        assertEquals(1, db.listenSessionDao().getForBook(bookId).size)
        assertEquals(1100L, db.listenSessionDao().totalListenedMs())
    }

    @Test
    fun durationBackfillKeepsWholeBookEstimateUntilEveryChapterIsKnown() = runBlocking {
        val bookId = remoteBook(total = 3_600_000)
        val first = chapter(bookId, "201", 0)
        val second = chapter(bookId, "202", 1)

        repository.recordRemoteChapterDuration(bookId, first, 60_000)
        assertEquals(3_600_000L, db.bookDao().getBook(bookId)!!.totalDurationMs)
        assertEquals(60_000L, db.chapterDao().getChapter(first)!!.durationMs)

        repository.recordRemoteChapterDuration(bookId, second, 120_000)
        assertEquals(180_000L, db.bookDao().getBook(bookId)!!.totalDurationMs)
        // Repeated reports cannot inflate the total or overwrite a known duration.
        repository.recordRemoteChapterDuration(bookId, first, 500_000)
        assertEquals(180_000L, db.bookDao().getBook(bookId)!!.totalDurationMs)
    }

    @Test
    fun updateCheckRepairsOldPartialTotalEvenWithoutNewChapters() = runBlocking {
        val bookId = remoteBook(total = 60_000)
        chapter(bookId, "201", 0, duration = 60_000)
        chapter(bookId, "202", 1)
        val update = repository.applyRemoteUpdate(
            bookId,
            listOf(FqAudioChapter("201", "first", 0), FqAudioChapter("202", "second", 1)),
            FqAudioMeta(
                audioBookId = "100", bookName = null, author = null, description = null,
                coverUrl = null, chapterCount = 2, totalDurationMs = 3_600_000,
                score = null, listenCount = 0, lastChapterTitle = null,
                lastChapterItemId = null, lastChapterUpdateTime = 0, finished = false,
                novelBookId = null,
            ),
        )
        assertEquals(0, update.addedCount)
        assertEquals(3_600_000L, db.bookDao().getBook(bookId)!!.totalDurationMs)
    }

    @Test
    fun durationBackfillRejectsLocalAndCrossBookChapters() = runBlocking {
        val remote = remoteBook(total = 3_600_000)
        val local = db.bookDao().insertBook(BookEntity(title = "local", rootUri = "tree://local"))
        val localChapter = chapter(local, "201", 0)
        repository.recordRemoteChapterDuration(local, localChapter, 60_000)
        repository.recordRemoteChapterDuration(remote, localChapter, 60_000)
        assertEquals(0L, db.chapterDao().getChapter(localChapter)!!.durationMs)
        assertEquals(3_600_000L, db.bookDao().getBook(remote)!!.totalDurationMs)
    }

    @Test
    fun lateCacheResultForOldVoiceCannotMarkNewVoiceAsDownloaded() = runBlocking {
        val bookId = remoteBook(tone = "5")
        val chapterId = chapter(bookId, "201", 0)
        val old = db.chapterDao().cacheEntries().single()
        repository.switchTtsTone(bookId, "6")
        db.chapterDao().updateCachedRecording(
            old.chapterId, old.audioBookId, old.itemId, old.toneId, true,
        )
        assertFalse(db.chapterDao().getChapter(chapterId)!!.isCached)

        val current = db.chapterDao().cacheEntries().single()
        assertEquals("6", current.toneId)
        db.chapterDao().updateCachedRecording(chapterId, "100", "201", "6", true)
        assertTrue(db.chapterDao().getChapter(chapterId)!!.isCached)
        db.chapterDao().updateCachedRecording(chapterId, "100", "201", "6", false)
        assertFalse(db.chapterDao().getChapter(chapterId)!!.isCached)
    }

    private suspend fun remoteBook(total: Long = 0, tone: String = "0"): Long =
        db.bookDao().insertBook(
            BookEntity(
                title = "remote", rootUri = "fqnovel://100/$tone", sourceType = "FQNOVEL",
                remoteAudioBookId = "100", remoteToneId = tone, totalDurationMs = total,
            ),
        )

    private suspend fun chapter(bookId: Long, item: String, index: Int, duration: Long = 0): Long =
        db.chapterDao().insert(
            ChapterEntity(
                bookId = bookId, title = item, uri = "fqnovel://100/$item", index = index,
                fileName = item, remoteItemId = item, durationMs = duration,
            ),
        )
}
