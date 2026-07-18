package com.tingxia.app.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressIntegrityTest {
    private lateinit var db: TingXiaDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TingXiaDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun staleOrCrossBookChapterCannotBecomeCurrentProgress() = runBlocking {
        val firstBook = db.bookDao().insertBook(BookEntity(title = "a", rootUri = "tree://a"))
        val secondBook = db.bookDao().insertBook(BookEntity(title = "b", rootUri = "tree://b"))
        val firstChapter = db.chapterDao().insert(
            ChapterEntity(bookId = firstBook, title = "c", uri = "doc://c", index = 0, fileName = "c.mp3"),
        )

        assertEquals(0, db.bookDao().updateProgress(firstBook, 9_999, 100))
        assertEquals(0, db.bookDao().updateProgress(secondBook, firstChapter, 100))
        assertEquals(null, db.bookDao().getBook(firstBook)?.currentChapterId)
        assertEquals(null, db.bookDao().getBook(secondBook)?.currentChapterId)

        assertEquals(1, db.bookDao().updateProgress(firstBook, firstChapter, 100))
        assertEquals(firstChapter, db.bookDao().getBook(firstBook)?.currentChapterId)
    }
}
