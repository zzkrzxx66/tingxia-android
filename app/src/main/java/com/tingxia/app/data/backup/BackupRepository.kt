package com.tingxia.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tingxia.app.data.db.BookDao
import com.tingxia.app.data.db.BookEntity
import com.tingxia.app.data.db.BookmarkDao
import com.tingxia.app.data.db.BookmarkEntity
import com.tingxia.app.data.db.ChapterDao
import com.tingxia.app.data.db.ChapterEntity
import com.tingxia.app.data.db.TingXiaDatabase
import com.tingxia.app.data.importer.ChapterIdentity
import com.tingxia.app.data.repo.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TingXiaDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val preferences: UserPreferencesRepository,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val books = bookDao.getAll()
        val chaptersByBook = books.associate { it.id to chapterDao.getForBook(it.id) }
        val bookmarksByBook = books.associate { it.id to bookmarkDao.getForBook(it.id) }
        val document = BackupDocument(
            formatVersion = BackupCodec.CURRENT_FORMAT_VERSION,
            exportedAt = System.currentTimeMillis(),
            settings = preferences.snapshot(),
            books = books.map { book ->
                val chapters = chaptersByBook[book.id].orEmpty()
                val chapterById = chapters.associateBy { it.id }
                BackupBook(
                    title = book.title,
                    author = book.author,
                    rootUri = book.rootUri,
                    totalDurationMs = book.totalDurationMs,
                    lastPlayedAt = book.lastPlayedAt,
                    currentChapterKey = book.currentChapterId?.let { chapterById[it]?.let(::chapterKey) },
                    currentPositionMs = book.currentPositionMs,
                    listenedDurationMs = book.listenedDurationMs,
                    createdAt = book.createdAt,
                    playbackSpeed = book.playbackSpeed,
                    autoPlayNext = book.autoPlayNext,
                    lastScannedAt = book.lastScannedAt,
                    coverUri = book.coverPath?.takeIf { it.startsWith("content:") || it.startsWith("http") },
                    chapters = chapters.map { ch ->
                        BackupChapter(
                            key = chapterKey(ch),
                            title = ch.title,
                            uri = ch.uri,
                            relativePath = ch.relativePath.ifBlank { ch.fileName },
                            fileName = ch.fileName,
                            fileSize = ch.fileSize,
                            documentId = ch.documentId,
                            mimeType = ch.mimeType,
                            durationMs = ch.durationMs,
                            index = ch.index,
                            customTitle = ch.customTitle,
                            completionState = ch.completionState,
                            completedAt = ch.completedAt,
                        )
                    },
                    bookmarks = bookmarksByBook[book.id].orEmpty().mapNotNull { mark ->
                        chapterById[mark.chapterId]?.let { chapter ->
                            BackupBookmark(
                                chapterKey = chapterKey(chapter),
                                positionMs = mark.positionMs,
                                note = mark.note,
                                createdAt = mark.createdAt,
                            )
                        }
                    },
                )
            },
        )
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法打开备份目标")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(BackupCodec.encode(document)) }
    }

    suspend fun importFrom(uri: Uri): BackupRestoreResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开备份文件")
        val json = input.use { stream ->
            require(stream.available() <= MAX_BACKUP_BYTES) { "备份文件过大" }
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
        val document = BackupCodec.decode(json)
        val result = database.withTransaction {
            var restoredBooks = 0
            var createdReauthBooks = 0
            var restoredChapters = 0
            var restoredBookmarks = 0
            var skippedBooks = 0
            document.books.forEach { backup ->
                val existing = bookDao.getBookByRootUri(backup.rootUri)
                if (existing == null) {
                    val restored = restoreMissingBook(backup)
                    createdReauthBooks++
                    restoredChapters += restored.first
                    restoredBookmarks += restored.second
                } else {
                    val restored = restoreExistingBook(existing, backup)
                    restoredBooks++
                    restoredChapters += restored.first
                    restoredBookmarks += restored.second
                }
            }
            BackupRestoreResult(
                restoredBooks,
                createdReauthBooks,
                restoredChapters,
                restoredBookmarks,
                skippedBooks,
            )
        }
        preferences.restore(document.settings)
        result
    }

    private suspend fun restoreMissingBook(backup: BackupBook): Pair<Int, Int> {
        val bookId = bookDao.insertBook(
            BookEntity(
                title = backup.title,
                author = backup.author,
                coverPath = backup.coverUri,
                rootUri = backup.rootUri,
                totalDurationMs = backup.totalDurationMs,
                lastPlayedAt = backup.lastPlayedAt,
                currentChapterId = null,
                currentPositionMs = backup.currentPositionMs,
                listenedDurationMs = backup.listenedDurationMs,
                createdAt = backup.createdAt,
                needsReauth = true,
                playbackSpeed = backup.playbackSpeed,
                autoPlayNext = backup.autoPlayNext,
                lastScannedAt = backup.lastScannedAt,
            ),
        )
        val chapters = backup.chapters.sortedBy { it.index }.map { it.toEntity(bookId) }
        val ids = chapterDao.insertAll(chapters)
        val byKey = backup.chapters.sortedBy { it.index }.zip(ids).associate { it.first.key to it.second }
        val currentId = backup.currentChapterKey?.let { byKey[it] }
        val currentBook = bookDao.getBook(bookId)!!
        bookDao.updateBook(currentBook.copy(currentChapterId = currentId))
        backup.bookmarks.forEach { mark ->
            byKey[mark.chapterKey]?.let { chapterId ->
                bookmarkDao.insert(mark.toEntity(bookId, chapterId))
            }
        }
        return chapters.size to backup.bookmarks.count { byKey.containsKey(it.chapterKey) }
    }

    private suspend fun restoreExistingBook(existing: BookEntity, backup: BackupBook): Pair<Int, Int> {
        val current = chapterDao.getForBook(existing.id)
        val byKey = current.associateBy { chapterKey(it) }
        val byPath = current.associateBy { ChapterIdentity.normalizeRelativePath(it.relativePath.ifBlank { it.fileName }) }
        var restoredChapters = 0
        val keyToCurrentId = mutableMapOf<String, Long>()
        backup.chapters.forEach { backupChapter ->
            val target = byKey[backupChapter.key]
                ?: byPath[ChapterIdentity.normalizeRelativePath(backupChapter.relativePath)]
            if (target != null) {
                chapterDao.update(
                    target.copy(
                        title = backupChapter.title,
                        customTitle = backupChapter.customTitle,
                        completionState = backupChapter.completionState,
                        completedAt = backupChapter.completedAt,
                    ),
                )
                keyToCurrentId[backupChapter.key] = target.id
                restoredChapters++
            }
        }
        val currentChapterId = backup.currentChapterKey?.let { keyToCurrentId[it] } ?: existing.currentChapterId
        bookDao.updateBook(
            existing.copy(
                title = backup.title,
                author = backup.author,
                coverPath = backup.coverUri ?: existing.coverPath,
                totalDurationMs = backup.totalDurationMs,
                lastPlayedAt = backup.lastPlayedAt,
                currentChapterId = currentChapterId,
                currentPositionMs = backup.currentPositionMs,
                listenedDurationMs = backup.listenedDurationMs,
                createdAt = backup.createdAt,
                playbackSpeed = backup.playbackSpeed,
                autoPlayNext = backup.autoPlayNext,
                lastScannedAt = backup.lastScannedAt,
            ),
        )
        bookmarkDao.deleteForBook(existing.id)
        var restoredBookmarks = 0
        backup.bookmarks.forEach { mark ->
            keyToCurrentId[mark.chapterKey]?.let { chapterId ->
                bookmarkDao.insert(mark.toEntity(existing.id, chapterId))
                restoredBookmarks++
            }
        }
        return restoredChapters to restoredBookmarks
    }

    private fun chapterKey(chapter: ChapterEntity): String =
        chapter.stableKey?.takeIf { it.isNotBlank() }
            ?: "path:${ChapterIdentity.normalizeRelativePath(chapter.relativePath.ifBlank { chapter.fileName })}"

    private fun BackupChapter.toEntity(bookId: Long) = ChapterEntity(
        bookId = bookId,
        title = title,
        uri = uri,
        index = index,
        durationMs = durationMs,
        fileName = fileName,
        relativePath = relativePath,
        fileSize = fileSize,
        documentId = documentId,
        mimeType = mimeType,
        stableKey = key.takeUnless { it.startsWith("path:") },
        customTitle = customTitle,
        completionState = completionState,
        completedAt = completedAt,
    )

    private fun BackupBookmark.toEntity(bookId: Long, chapterId: Long) = BookmarkEntity(
        bookId = bookId,
        chapterId = chapterId,
        positionMs = positionMs,
        note = note,
        createdAt = createdAt,
    )

    private companion object {
        const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
    }
}
