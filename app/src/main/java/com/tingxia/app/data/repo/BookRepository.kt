package com.tingxia.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.tingxia.app.data.db.BookDao
import com.tingxia.app.data.db.BookEntity
import com.tingxia.app.data.db.ChapterDao
import com.tingxia.app.data.db.ChapterEntity
import com.tingxia.app.data.db.TingXiaDatabase
import com.tingxia.app.data.importer.FolderScanner
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ProgressCalculator
import com.tingxia.app.data.model.toModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TingXiaDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val folderScanner: FolderScanner,
) {
    fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { list -> list.map { it.toModel() } }

    fun observeBook(id: Long): Flow<Book?> =
        bookDao.observeBook(id).map { it?.toModel() }

    fun observeRecentBook(): Flow<Book?> =
        bookDao.observeRecentBook().map { it?.toModel() }

    fun observeChapters(bookId: Long): Flow<List<Chapter>> =
        chapterDao.observeChapters(bookId).map { list -> list.map { it.toModel() } }

    suspend fun getBook(id: Long): Book? = bookDao.getBook(id)?.toModel()

    suspend fun getChapters(bookId: Long): List<Chapter> =
        chapterDao.getChapters(bookId).map { it.toModel() }

    suspend fun getChapter(id: Long): Chapter? = chapterDao.getChapter(id)?.toModel()

    /**
     * Import a folder as a book. Permission is taken before scan; if scan fails and this tree
     * is not already used by another book, the newly acquired permission is released.
     */
    suspend fun importFolder(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): Book {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        var permissionTaken = false
        val alreadyHeld = isUriPermissionHeld(treeUri)
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            permissionTaken = true
        } catch (_: SecurityException) {
            // may already be taken or not persistable
        }

        val root = treeUri.toString()
        return try {
            val scanned = folderScanner.scanTree(treeUri, onProgress)
            database.withTransaction {
                val bookId = bookDao.insertBook(
                    BookEntity(
                        title = scanned.title,
                        author = null,
                        coverPath = scanned.coverPath,
                        rootUri = scanned.rootUri,
                        totalDurationMs = scanned.totalDurationMs,
                        lastPlayedAt = 0L,
                        currentChapterId = null,
                        currentPositionMs = 0L,
                        listenedDurationMs = 0L,
                        needsReauth = false,
                    ),
                )
                val chapters = scanned.chapters.map { ch ->
                    ChapterEntity(
                        bookId = bookId,
                        title = ch.title,
                        uri = ch.uri,
                        index = ch.index,
                        durationMs = ch.durationMs,
                        fileName = ch.fileName,
                    )
                }
                val ids = chapterDao.insertAll(chapters)
                if (ids.isNotEmpty()) {
                    bookDao.updateProgress(
                        bookId = bookId,
                        chapterId = ids.first(),
                        positionMs = 0L,
                        listenedDurationMs = 0L,
                        playedAt = 0L,
                    )
                }
                bookDao.getBook(bookId)!!.toModel()
            }
        } catch (e: Exception) {
            if (permissionTaken && !alreadyHeld) {
                if (bookDao.countByRootUri(root) == 0) {
                    releaseUriPermission(treeUri)
                }
            }
            throw e
        }
    }

    /**
     * Re-bind an existing book to a SAF tree after permission loss.
     * Replaces chapters from a fresh scan while preserving progress by fileName when possible.
     */
    suspend fun reauthBook(
        bookId: Long,
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): Book {
        val existing = bookDao.getBook(bookId) ?: error("书籍不存在")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
        }

        val oldRoot = existing.rootUri
        val scanned = folderScanner.scanTree(treeUri, onProgress)
        return database.withTransaction {
            val oldChapters = chapterDao.getChapters(bookId)
            val progressChapterFile = oldChapters
                .firstOrNull { it.id == existing.currentChapterId }
                ?.fileName
            val savedPosition = existing.currentPositionMs

            chapterDao.deleteForBook(bookId)
            val newEntities = scanned.chapters.map { ch ->
                ChapterEntity(
                    bookId = bookId,
                    title = ch.title,
                    uri = ch.uri,
                    index = ch.index,
                    durationMs = ch.durationMs,
                    fileName = ch.fileName,
                )
            }
            val ids = chapterDao.insertAll(newEntities)
            val matchedIndex = progressChapterFile?.let { name ->
                scanned.chapters.indexOfFirst { it.fileName == name }
            }?.takeIf { it >= 0 } ?: 0
            val chapterId = ids.getOrNull(matchedIndex) ?: ids.firstOrNull()
            val position = if (
                progressChapterFile != null &&
                scanned.chapters.getOrNull(matchedIndex)?.fileName == progressChapterFile
            ) {
                savedPosition
            } else {
                0L
            }
            val chapterModels = scanned.chapters.mapIndexed { idx, ch ->
                Chapter(
                    id = ids.getOrElse(idx) { 0L },
                    bookId = bookId,
                    title = ch.title,
                    uri = ch.uri,
                    index = ch.index,
                    durationMs = ch.durationMs,
                    fileName = ch.fileName,
                )
            }
            val listened = ProgressCalculator.listenedDurationMs(
                chapters = chapterModels,
                currentChapterId = chapterId,
                currentPositionMs = position,
            )

            bookDao.updateBook(
                existing.copy(
                    title = scanned.title.ifBlank { existing.title },
                    coverPath = scanned.coverPath ?: existing.coverPath,
                    rootUri = scanned.rootUri,
                    totalDurationMs = scanned.totalDurationMs,
                    currentChapterId = chapterId,
                    currentPositionMs = position,
                    listenedDurationMs = listened,
                    needsReauth = false,
                ),
            )

            if (oldRoot != scanned.rootUri) {
                maybeReleaseRootUri(oldRoot, excludeBookId = bookId)
            }

            bookDao.getBook(bookId)!!.toModel()
        }
    }

    suspend fun removeBook(bookId: Long) {
        val book = bookDao.getBook(bookId) ?: return
        val root = book.rootUri
        bookDao.deleteBook(bookId)
        if (bookDao.countByRootUri(root) == 0) {
            releaseUriPermission(Uri.parse(root))
        }
    }

    suspend fun saveProgress(bookId: Long, chapterId: Long, positionMs: Long) {
        val chapters = chapterDao.getChapters(bookId).map { it.toModel() }
        val listened = ProgressCalculator.listenedDurationMs(
            chapters = chapters,
            currentChapterId = chapterId,
            currentPositionMs = positionMs.coerceAtLeast(0L),
        )
        bookDao.updateProgress(
            bookId = bookId,
            chapterId = chapterId,
            positionMs = positionMs.coerceAtLeast(0L),
            listenedDurationMs = listened,
        )
        val book = bookDao.getBook(bookId)
        if (book?.needsReauth == true) {
            bookDao.setNeedsReauth(bookId, false)
        }
    }

    suspend fun markNeedsReauth(bookId: Long, needs: Boolean) {
        bookDao.setNeedsReauth(bookId, needs)
    }

    /** Probe whether the book's root URI is still readable. */
    suspend fun checkBookAccess(bookId: Long): Boolean {
        val book = bookDao.getBook(bookId) ?: return false
        val ok = canAccessUri(Uri.parse(book.rootUri))
        if (!ok && !book.needsReauth) {
            bookDao.setNeedsReauth(bookId, true)
        } else if (ok && book.needsReauth) {
            bookDao.setNeedsReauth(bookId, false)
        }
        return ok
    }

    fun canAccessUri(uri: Uri): Boolean {
        return try {
            val treeId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Exception) {
                null
            }
            val queryUri = if (treeId != null) {
                DocumentsContract.buildDocumentUriUsingTree(uri, treeId)
            } else {
                uri
            }
            context.contentResolver.query(
                queryUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun isUriPermissionHeld(uri: Uri): Boolean {
        val target = uri.toString()
        return context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == target && it.isReadPermission
        }
    }

    private fun releaseUriPermission(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun maybeReleaseRootUri(rootUri: String, excludeBookId: Long) {
        if (bookDao.countByRootUriExcluding(rootUri, excludeBookId) == 0) {
            releaseUriPermission(Uri.parse(rootUri))
        }
    }
}
