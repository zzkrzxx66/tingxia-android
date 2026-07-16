package com.tingxia.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastPlayedAt DESC, createdAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR IFNULL(author, '') LIKE '%' || :query || '%')
          AND (
            :filter = 'ALL'
            OR (:filter = 'NOT_STARTED' AND lastPlayedAt = 0)
            OR (:filter = 'IN_PROGRESS' AND lastPlayedAt > 0)
            OR (:filter = 'NEEDS_REAUTH' AND needsReauth = 1)
          )
        ORDER BY
          CASE WHEN :sort = 'RECENT' THEN lastPlayedAt ELSE 0 END DESC,
          CASE WHEN :sort = 'IMPORTED' THEN createdAt ELSE 0 END DESC,
          CASE WHEN :sort = 'TITLE' THEN title ELSE '' END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'PROGRESS' THEN
            CASE WHEN totalDurationMs <= 0 THEN 0.0
                 ELSE (listenedDurationMs * 1.0 / totalDurationMs) END
          ELSE 0 END DESC,
          createdAt DESC
        """
    )
    fun observeBooksFiltered(query: String, sort: String, filter: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): BookEntity?

    @Query(
        """
        SELECT * FROM books
        WHERE lastPlayedAt > 0
        ORDER BY lastPlayedAt DESC
        LIMIT 1
        """
    )
    fun observeRecentBook(): Flow<BookEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Query(
        """
        UPDATE books SET
            currentChapterId = :chapterId,
            currentPositionMs = :positionMs,
            listenedDurationMs = :listenedDurationMs,
            lastPlayedAt = :playedAt
        WHERE id = :bookId
        """
    )
    suspend fun updateProgress(
        bookId: Long,
        chapterId: Long,
        positionMs: Long,
        listenedDurationMs: Long = 0L,
        playedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE books SET needsReauth = :needsReauth WHERE id = :bookId")
    suspend fun setNeedsReauth(bookId: Long, needsReauth: Boolean)

    @Query("UPDATE books SET totalDurationMs = :total WHERE id = :bookId")
    suspend fun updateTotalDuration(bookId: Long, total: Long)

    @Query(
        """
        UPDATE books SET
            playbackSpeed = :speed
        WHERE id = :bookId
        """
    )
    suspend fun updatePlaybackSpeed(bookId: Long, speed: Float?)

    @Query("UPDATE books SET autoPlayNext = :autoPlayNext WHERE id = :bookId")
    suspend fun updateAutoPlayNext(bookId: Long, autoPlayNext: Boolean)

    @Query(
        """
        UPDATE books SET
            totalDurationMs = :totalDurationMs,
            lastScannedAt = :lastScannedAt,
            currentChapterId = :currentChapterId,
            currentPositionMs = :currentPositionMs,
            listenedDurationMs = :listenedDurationMs
        WHERE id = :bookId
        """
    )
    suspend fun updateAfterRescan(
        bookId: Long,
        totalDurationMs: Long,
        lastScannedAt: Long,
        currentChapterId: Long?,
        currentPositionMs: Long,
        listenedDurationMs: Long,
    )

    @Query("SELECT COUNT(*) FROM books WHERE rootUri = :rootUri")
    suspend fun countByRootUri(rootUri: String): Int

    @Query("SELECT COUNT(*) FROM books WHERE rootUri = :rootUri AND id != :excludeId")
    suspend fun countByRootUriExcluding(rootUri: String, excludeId: Long): Int

    @Query("SELECT id FROM books WHERE rootUri = :rootUri LIMIT 1")
    suspend fun findIdByRootUri(rootUri: String): Long?
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY `index` ASC")
    suspend fun getChapters(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Update
    suspend fun updateAll(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET `index` = :index WHERE id = :id")
    suspend fun updateIndex(id: Long, index: Int)

    @Query("DELETE FROM chapters WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun countForBook(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM bookmarks WHERE chapterId IN (:chapterIds)")
    suspend fun countBookmarksForChapters(chapterIds: List<Long>): Int
}

@Dao
interface BookmarkDao {
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE bookId = :bookId
        ORDER BY createdAt DESC
        """
    )
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmark(id: Long): BookmarkEntity?

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE bookId = :bookId AND chapterId = :chapterId
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun latestForChapter(bookId: Long, chapterId: Long): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE chapterId IN (:chapterIds)")
    suspend fun countForChapters(chapterIds: List<Long>): Int
}
