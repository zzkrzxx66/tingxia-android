package com.tingxia.app.data.repo

import com.tingxia.app.data.db.BookmarkDao
import com.tingxia.app.data.db.BookmarkEntity
import com.tingxia.app.data.db.ChapterDao
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val chapterDao: ChapterDao,
) {
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> {
        return combine(
            bookmarkDao.observeBookmarks(bookId),
            chapterDao.observeChapters(bookId),
        ) { bookmarks, chapters ->
            val byId = chapters.associateBy { it.id }
            bookmarks.map { b ->
                val ch = byId[b.chapterId]
                b.toModel(
                    chapterTitle = ch?.let { it.customTitle ?: it.title },
                    chapterIndex = ch?.index,
                )
            }
        }
    }

    suspend fun addBookmark(
        bookId: Long,
        chapterId: Long,
        positionMs: Long,
        note: String? = null,
    ): Bookmark {
        val chapter = chapterDao.getChapter(chapterId) ?: error("章节不存在")
        require(chapter.bookId == bookId) { "书签章节不属于当前书籍" }
        val pos = if (chapter.durationMs > 0L) {
            positionMs.coerceIn(0L, chapter.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        val latest = bookmarkDao.latestForChapter(bookId, chapterId)
        val now = System.currentTimeMillis()
        if (latest != null &&
            now - latest.createdAt <= 2_000L &&
            kotlin.math.abs(latest.positionMs - pos) <= 1_000L
        ) {
            // Merge rapid duplicate taps.
            val updated = latest.copy(positionMs = pos, note = note ?: latest.note, createdAt = now)
            bookmarkDao.update(updated)
            return updated.toModel(chapter.customTitle ?: chapter.title, chapter.index)
        }
        val id = bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                chapterId = chapterId,
                positionMs = pos,
                note = note,
                createdAt = now,
            ),
        )
        val entity = bookmarkDao.getBookmark(id)!!
        return entity.toModel(chapter.customTitle ?: chapter.title, chapter.index)
    }

    suspend fun updateNote(id: Long, note: String?) {
        val existing = bookmarkDao.getBookmark(id) ?: return
        bookmarkDao.update(existing.copy(note = note))
    }

    suspend fun delete(id: Long) {
        bookmarkDao.delete(id)
    }
}
