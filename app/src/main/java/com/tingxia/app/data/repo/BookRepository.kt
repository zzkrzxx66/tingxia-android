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
import com.tingxia.app.data.model.ChapterOffsetIndex
import com.tingxia.app.data.model.toModel
import com.tingxia.app.data.policy.SafPermissionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
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
    /** Cached start-offset index per book for O(1) listened-duration updates. */
    private val offsetIndexCache = ConcurrentHashMap<Long, ChapterOffsetIndex>()

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

    suspend fun findBookIdByRootUri(rootUri: String): Long? =
        bookDao.findIdByRootUri(rootUri)

    /**
     * Import a folder as a book. Permission is taken before scan; if scan fails and this tree
     * is not already used by another book, the newly acquired permission is released.
     * Same rootUri is not imported twice.
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
        }

        val root = treeUri.toString()
        bookDao.findIdByRootUri(root)?.let { existingId ->
            // Already imported — just return existing book.
            return bookDao.getBook(existingId)!!.toModel()
        }

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
                invalidateOffsetIndex(bookId)
                bookDao.getBook(bookId)!!.toModel()
            }
        } catch (e: Exception) {
            val count = bookDao.countByRootUri(root)
            if (
                SafPermissionPolicy.shouldReleaseAfterFailedTake(
                    rootUri = root,
                    permissionWasNewlyTaken = permissionTaken && !alreadyHeld,
                    booksUsingRoot = count,
                )
            ) {
                releaseUriPermission(treeUri)
            }
            throw e
        }
    }

    /**
     * Re-bind an existing book to a SAF tree after permission loss.
     * Rejects trees that do not sufficiently match the original chapter file names
     * (unless the root URI is identical).
     */
    suspend fun reauthBook(
        bookId: Long,
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): Book {
        val existing = bookDao.getBook(bookId) ?: error("书籍不存在")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        var permissionTaken = false
        val alreadyHeld = isUriPermissionHeld(treeUri)
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            permissionTaken = true
        } catch (_: SecurityException) {
        }

        val oldRoot = existing.rootUri
        val newRoot = treeUri.toString()
        val oldChapters = chapterDao.getChapters(bookId)

        return try {
            val scanned = folderScanner.scanTree(treeUri, onProgress)
            val acceptable = SafPermissionPolicy.isAcceptableReauthTree(
                oldRootUri = oldRoot,
                newRootUri = scanned.rootUri,
                oldFileNames = oldChapters.map { it.fileName },
                newFileNames = scanned.chapters.map { it.fileName },
            )
            if (!acceptable) {
                error("所选目录与原书籍章节不匹配，请选择正确的书目文件夹")
            }

            database.withTransaction {
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
                val index = ChapterOffsetIndex(chapterModels)
                offsetIndexCache[bookId] = index
                val listened = index.listenedDurationMs(chapterId, position)

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
        } catch (e: Exception) {
            val count = bookDao.countByRootUri(newRoot)
            if (
                SafPermissionPolicy.shouldReleaseAfterFailedTake(
                    rootUri = newRoot,
                    permissionWasNewlyTaken = permissionTaken && !alreadyHeld,
                    booksUsingRoot = count,
                )
            ) {
                releaseUriPermission(treeUri)
            }
            throw e
        }
    }

    suspend fun removeBook(bookId: Long) {
        val book = bookDao.getBook(bookId) ?: return
        val root = book.rootUri
        val coverPath = book.coverPath
        bookDao.deleteBook(bookId)
        invalidateOffsetIndex(bookId)
        if (bookDao.countByRootUri(root) == 0) {
            releaseUriPermission(Uri.parse(root))
        }
        // Clean extracted embedded covers under app filesDir/covers/
        deleteLocalCoverIfOwned(coverPath)
    }

    /**
     * Persist progress. Does **not** clear [needsReauth] — that is only cleared by
     * successful URI access checks or reauth completion.
     */
    suspend fun saveProgress(bookId: Long, chapterId: Long, positionMs: Long) {
        val pos = positionMs.coerceAtLeast(0L)
        val index = offsetIndexCache[bookId] ?: rebuildOffsetIndex(bookId)
        val listened = index.listenedDurationMs(chapterId, pos)
        bookDao.updateProgress(
            bookId = bookId,
            chapterId = chapterId,
            positionMs = pos,
            listenedDurationMs = listened,
        )
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
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun rebuildOffsetIndex(bookId: Long): ChapterOffsetIndex {
        val chapters = chapterDao.getChapters(bookId).map { it.toModel() }
        val index = ChapterOffsetIndex(chapters)
        offsetIndexCache[bookId] = index
        return index
    }

    private fun invalidateOffsetIndex(bookId: Long) {
        offsetIndexCache.remove(bookId)
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
        val remaining = bookDao.countByRootUriExcluding(rootUri, excludeBookId)
        // Model remaining as BookRefs with dummy ids for policy (count is enough for release)
        if (remaining == 0) {
            releaseUriPermission(Uri.parse(rootUri))
        }
    }

    private fun deleteLocalCoverIfOwned(coverPath: String?) {
        if (coverPath.isNullOrBlank()) return
        if (coverPath.startsWith("content:") || coverPath.startsWith("http")) return
        try {
            val coversDir = context.filesDir.resolve("covers").canonicalFile
            val file = java.io.File(coverPath).canonicalFile
            if (file.path.startsWith(coversDir.path) && file.isFile) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
