package com.tingxia.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastPlayedAt DESC, createdAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun countForBook(bookId: Long): Int
}
