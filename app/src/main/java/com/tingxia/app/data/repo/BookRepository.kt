package com.tingxia.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.tingxia.app.data.db.BookDao
import com.tingxia.app.data.db.BookEntity
import com.tingxia.app.data.db.BookmarkDao
import com.tingxia.app.data.db.ChapterDao
import com.tingxia.app.data.db.ChapterEntity
import com.tingxia.app.data.db.TingXiaDatabase
import com.tingxia.app.data.importer.ChapterIdentity
import com.tingxia.app.data.importer.FolderScanner
import com.tingxia.app.data.importer.RescanPlanner
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.importer.ScannedChapter
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterOffsetIndex
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.data.model.toModel
import com.tingxia.app.data.policy.SafPermissionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class RescanPreview(
    val bookId: Long,
    val plan: RescanPlanner.Plan,
    val affectedBookmarkCount: Int,
)

data class RescanApplyResult(
    val book: Book,
    val chapters: List<Chapter>,
    val currentChapterId: Long?,
    val currentPositionMs: Long,
    val removedChapterIds: Set<Long>,
)

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TingXiaDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val folderScanner: FolderScanner,
) {
    private val offsetIndexCache = ConcurrentHashMap<Long, ChapterOffsetIndex>()

    fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { list -> list.map { it.toModel() } }

    fun observeBooks(
        query: String,
        sort: ShelfSort,
        filter: ShelfFilter,
    ): Flow<List<Book>> =
        bookDao.observeBooksFiltered(
            query = query.trim(),
            sort = sort.name,
            filter = filter.name,
        ).map { list -> list.map { it.toModel() } }

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
            return bookDao.getBook(existingId)!!.toModel()
        }

        return try {
            val scanned = folderScanner.scanTree(treeUri, onProgress)
            database.withTransaction {
                val now = System.currentTimeMillis()
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
                        playbackSpeed = null,
                        autoPlayNext = true,
                        lastScannedAt = now,
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
                        relativePath = ch.relativePath,
                        fileSize = ch.fileSize,
                        documentId = ch.documentId,
                        mimeType = ch.mimeType,
                        stableKey = ch.stableKey,
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

    suspend fun previewRescan(
        bookId: Long,
        onProgress: (ScanProgress) -> Unit = {},
    ): RescanPreview {
        val book = bookDao.getBook(bookId) ?: error("书籍不存在")
        if (!canAccessUri(Uri.parse(book.rootUri))) {
            bookDao.setNeedsReauth(bookId, true)
            error("目录权限失效，请重新授权")
        }
        val existing = chapterDao.getChapters(bookId).map { it.toModel() }
        val scanned = folderScanner.scanTree(Uri.parse(book.rootUri), onProgress)
        if (scanned.rootUri != book.rootUri) {
            error("扫描结果目录与书籍目录不一致，请使用重新授权")
        }
        val plan = RescanPlanner.plan(bookId, existing, scanned.chapters)
        val removedIds = plan.removed.map { it.id }
        val affected = if (removedIds.isEmpty()) 0 else bookmarkDao.countForChapters(removedIds)
        return RescanPreview(bookId, plan, affected)
    }

    suspend fun applyRescan(
        bookId: Long,
        plan: RescanPlanner.Plan,
        acceptedWeak: Map<Long, ScannedChapter> = emptyMap(),
        acceptedAmbiguous: Map<String, Long> = emptyMap(),
    ): RescanApplyResult {
        val book = bookDao.getBook(bookId) ?: error("书籍不存在")
        val existing = chapterDao.getChapters(bookId).map { it.toModel() }
        // Recompute plan with decisions to be safe
        val finalPlan = if (acceptedWeak.isEmpty() && acceptedAmbiguous.isEmpty()) {
            plan
        } else {
            RescanPlanner.plan(
                bookId = bookId,
                existing = existing,
                scanned = plan.finalChaptersPreview,
                acceptedWeak = acceptedWeak,
                acceptedAmbiguous = acceptedAmbiguous,
            )
        }
        if (finalPlan.weakMatches.isNotEmpty() || finalPlan.ambiguous.isNotEmpty()) {
            error("仍有未确认的弱匹配或歧义章节")
        }

        val strong = finalPlan.autoMatches
        val removedIds = finalPlan.removed.map { it.id }.toSet()
        val added = finalPlan.added
        val scannedOrder = finalPlan.finalChaptersPreview

        return database.withTransaction {
            val oldEntities = chapterDao.getChapters(bookId)
            val oldById = oldEntities.associateBy { it.id }

            // 1) Move retained indices to temporary negative values
            oldEntities.forEach { ch ->
                if (ch.id in strong || ch.id !in removedIds) {
                    // all currently present; only keep matched ones
                }
            }
            val keptIds = strong.keys
            oldEntities.filter { it.id in keptIds }.forEach { ch ->
                chapterDao.updateIndex(ch.id, -(ch.index + 1))
            }

            // 2) Delete removed
            if (removedIds.isNotEmpty()) {
                chapterDao.deleteByIds(removedIds.toList())
            }

            // 3) Update matched rows (content) with temporary still-negative or update fields first
            val finalIndexByOldId = mutableMapOf<Long, Int>()
            scannedOrder.forEachIndexed { newIndex, sc ->
                val oldId = strong.entries.firstOrNull { it.value.uri == sc.uri || it.value.relativePath == sc.relativePath }?.key
                    ?: strong.entries.firstOrNull { it.value.stableKey == sc.stableKey }?.key
                if (oldId != null) {
                    finalIndexByOldId[oldId] = newIndex
                }
            }

            strong.forEach { (oldId, sc) ->
                val old = oldById[oldId] ?: return@forEach
                val newIndex = finalIndexByOldId[oldId] ?: sc.index
                chapterDao.update(
                    old.copy(
                        title = sc.title,
                        uri = sc.uri,
                        // keep temp negative index for now if still negative
                        index = -(old.index + 1),
                        durationMs = sc.durationMs,
                        fileName = sc.fileName,
                        relativePath = sc.relativePath,
                        fileSize = sc.fileSize,
                        documentId = sc.documentId,
                        mimeType = sc.mimeType,
                        stableKey = sc.stableKey,
                    ),
                )
            }

            // 4) Insert added with final indices
            val addedIdByUri = mutableMapOf<String, Long>()
            scannedOrder.forEachIndexed { newIndex, sc ->
                val matchedOld = strong.entries.firstOrNull {
                    it.value.uri == sc.uri ||
                        it.value.relativePath == sc.relativePath ||
                        it.value.stableKey == sc.stableKey
                }?.key
                if (matchedOld == null) {
                    val id = chapterDao.insert(
                        ChapterEntity(
                            bookId = bookId,
                            title = sc.title,
                            uri = sc.uri,
                            index = newIndex,
                            durationMs = sc.durationMs,
                            fileName = sc.fileName,
                            relativePath = sc.relativePath,
                            fileSize = sc.fileSize,
                            documentId = sc.documentId,
                            mimeType = sc.mimeType,
                            stableKey = sc.stableKey,
                        ),
                    )
                    addedIdByUri[sc.uri] = id
                }
            }

            // 5) Write final indices for matched
            finalIndexByOldId.forEach { (oldId, newIndex) ->
                chapterDao.updateIndex(oldId, newIndex)
            }

            // Build final chapter models
            val finalEntities = chapterDao.getChapters(bookId).sortedBy { it.index }
            val finalModels = finalEntities.map { it.toModel() }

            // Resolve current chapter
            val oldCurrent = book.currentChapterId
            val currentStill = oldCurrent != null && oldCurrent in strong
            val newCurrentId: Long?
            val newPos: Long
            if (currentStill) {
                newCurrentId = oldCurrent
                val newDur = finalModels.firstOrNull { it.id == oldCurrent }?.durationMs ?: 0L
                newPos = if (newDur > 0L) book.currentPositionMs.coerceIn(0L, newDur) else book.currentPositionMs
            } else {
                val replacement = RescanPlanner.chooseReplacementChapterId(
                    oldChapters = oldEntities.map { it.toModel() },
                    removedIds = removedIds,
                    currentChapterId = oldCurrent,
                    survivingOldIdToNewIndex = finalIndexByOldId,
                    addedInOrder = added,
                    newChapterIdsInFinalOrder = finalModels.map { it.id },
                )
                newCurrentId = replacement
                newPos = 0L
            }

            val listened = ProgressCalculatorSafe.listened(finalModels, newCurrentId, newPos)
            val now = System.currentTimeMillis()
            bookDao.updateAfterRescan(
                bookId = bookId,
                totalDurationMs = finalModels.sumOf { it.durationMs },
                lastScannedAt = now,
                currentChapterId = newCurrentId,
                currentPositionMs = newPos,
                listenedDurationMs = listened,
            )

            invalidateOffsetIndex(bookId)
            val updated = bookDao.getBook(bookId)!!.toModel()
            RescanApplyResult(
                book = updated,
                chapters = finalModels,
                currentChapterId = newCurrentId,
                currentPositionMs = newPos,
                removedChapterIds = removedIds,
            )
        }
    }

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
        val oldChapters = chapterDao.getChapters(bookId).map { it.toModel() }

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

            // Update root first, then apply rescan plan against new scan.
            database.withTransaction {
                bookDao.updateBook(existing.copy(rootUri = scanned.rootUri, needsReauth = false, coverPath = scanned.coverPath ?: existing.coverPath))
            }
            val plan = RescanPlanner.plan(bookId, oldChapters, scanned.chapters)
            // Auto-accept weak matches on reauth for better UX when same library moved.
            val acceptedWeak = plan.weakMatches
            val result = applyRescan(bookId, plan, acceptedWeak = acceptedWeak, acceptedAmbiguous = emptyMap())
            if (oldRoot != scanned.rootUri) {
                maybeReleaseRootUri(oldRoot, excludeBookId = bookId)
            }
            result.book
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
        deleteLocalCoverIfOwned(coverPath)
    }

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

    suspend fun setBookPlaybackSpeed(bookId: Long, speed: Float?) {
        bookDao.updatePlaybackSpeed(bookId, speed)
    }

    suspend fun setAutoPlayNext(bookId: Long, autoPlayNext: Boolean) {
        bookDao.updateAutoPlayNext(bookId, autoPlayNext)
    }

    suspend fun markNeedsReauth(bookId: Long, needs: Boolean) {
        bookDao.setNeedsReauth(bookId, needs)
    }

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

/** Local helper to avoid circular imports in transaction. */
private object ProgressCalculatorSafe {
    fun listened(chapters: List<Chapter>, currentChapterId: Long?, positionMs: Long): Long {
        return com.tingxia.app.data.model.ProgressCalculator.listenedDurationMs(
            chapters, currentChapterId, positionMs,
        )
    }
}
