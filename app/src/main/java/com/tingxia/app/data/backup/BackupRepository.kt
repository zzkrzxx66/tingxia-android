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
import com.tingxia.app.data.model.toModel
import com.tingxia.app.data.repo.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import androidx.media3.common.util.UnstableApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TingXiaDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val preferences: UserPreferencesRepository,
    private val playerController: com.tingxia.app.player.PlayerController,
    private val bookRepository: com.tingxia.app.data.repo.BookRepository,
    private val listenSessionDao: com.tingxia.app.data.db.ListenSessionDao,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val document = database.withTransaction {
            val books = bookDao.getAll()
            val chaptersByBook = books.associate { it.id to chapterDao.getForBook(it.id) }
            val bookmarksByBook = books.associate { it.id to bookmarkDao.getForBook(it.id) }
            BackupDocument(
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
                        skipIntroMs = book.skipIntroMs,
                        skipOutroMs = book.skipOutroMs,
                        sourceType = book.sourceType,
                        remoteBookId = book.remoteBookId,
                        remoteAudioBookId = book.remoteAudioBookId,
                        remoteToneId = book.remoteToneId,
                        description = book.description,
                        category = book.category,
                        wordCount = book.wordCount,
                        metaSyncSourceId = book.metaSyncSourceId,
                        metaSyncedAt = book.metaSyncedAt,
                        metaSyncBackup = book.metaSyncBackup,
                        remoteScore = book.remoteScore,
                        remoteListenCount = book.remoteListenCount,
                        remoteFinished = book.remoteFinished,
                        remoteLastChapterTitle = book.remoteLastChapterTitle,
                        remoteUpdateCheckedAt = book.remoteUpdateCheckedAt,
                        remoteNewChapterCount = book.remoteNewChapterCount,
                        listening = listenSessionDao.getForBook(book.id).map { BackupListening(it.dayStartMs, it.listenedMs) },
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
                                remoteItemId = ch.remoteItemId,
                                clipStartMs = ch.clipStartMs,
                                clipEndMs = ch.clipEndMs,
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
        }
        val encoded = BackupCodec.encode(document)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "备份文件过大" }
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法打开备份目标")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
    }

    suspend fun readFrom(uri: Uri): BackupDocument = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开备份文件")
        val json = input.use { BackupReader.readUtf8(it, MAX_BACKUP_BYTES) }
        BackupCodec.decode(json).also { document ->
            require(document.books.map { it.rootUri }.distinct().size == document.books.size) { "备份包含重复书籍" }
            document.books.forEach { book ->
                require(book.chapters.map { it.key }.distinct().size == book.chapters.size) { "备份包含重复章节" }
                require(book.chapters.map { it.index }.distinct().size == book.chapters.size) { "备份章节顺序重复" }
            }
        }
    }

    suspend fun importFrom(uri: Uri): BackupRestoreResult = restore(readFrom(uri))

    private val restoreMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun restore(document: BackupDocument): BackupRestoreResult = restoreMutex.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = withContext(Dispatchers.Main.immediate) {
                playerController.prepareLibraryMutation(-1L, clearPlaylist = true)
            }
            val activeBookId = snapshot.bookId.takeIf { snapshot.wasActive }
            try {
                val result = database.withTransaction {
                    var restoredBooks = 0
                    var createdReauthBooks = 0
                    var restoredChapters = 0
                    var restoredBookmarks = 0
                    document.books.forEach { backup ->
                        val existing = bookDao.getBookByRootUri(backup.rootUri)
                        val restored = if (existing == null) {
                            createdReauthBooks++
                            restoreMissingBook(backup)
                        } else {
                            restoredBooks++
                            restoreExistingBook(existing, backup)
                        }
                        restoredChapters += restored.first
                        restoredBookmarks += restored.second
                    }
                    BackupRestoreResult(restoredBooks, createdReauthBooks, restoredChapters, restoredBookmarks, 0)
                }
                preferences.restore(document.settings)
                result
            } finally {
                bookRepository.invalidateAllOffsetIndices()
                if (activeBookId != null) withContext(NonCancellable + Dispatchers.Main.immediate) {
                    val restored = bookRepository.getBook(activeBookId)
                    if (restored != null) playerController.refreshPlaylistAfterRescan(
                        activeBookId, restored.currentChapterId, restored.currentPositionMs, wasPlaying = false,
                    )
                }
            }
        }
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
                needsReauth = backup.sourceType == "LOCAL",
                playbackSpeed = backup.playbackSpeed,
                autoPlayNext = backup.autoPlayNext,
                lastScannedAt = backup.lastScannedAt,
                skipIntroMs = backup.skipIntroMs,
                skipOutroMs = backup.skipOutroMs,
                sourceType = backup.sourceType,
                remoteBookId = backup.remoteBookId,
                remoteAudioBookId = backup.remoteAudioBookId,
                remoteToneId = backup.remoteToneId,
                description = backup.description,
                category = backup.category,
                wordCount = backup.wordCount,
                metaSyncSourceId = backup.metaSyncSourceId,
                metaSyncedAt = backup.metaSyncedAt,
                metaSyncBackup = backup.metaSyncBackup,
                remoteScore = backup.remoteScore,
                remoteListenCount = backup.remoteListenCount,
                remoteFinished = backup.remoteFinished,
                remoteLastChapterTitle = backup.remoteLastChapterTitle,
                remoteUpdateCheckedAt = backup.remoteUpdateCheckedAt,
                remoteNewChapterCount = backup.remoteNewChapterCount,
            ),
        )
        val chapters = backup.chapters.sortedBy { it.index }.map { it.toEntity(bookId) }
        val ids = chapterDao.insertAll(chapters)
        val byKey = backup.chapters.sortedBy { it.index }.zip(ids).associate { it.first.key to it.second }
        val currentId = backup.currentChapterKey?.let { byKey[it] }
        val currentBook = bookDao.getBook(bookId)!!
        val linear = com.tingxia.app.data.model.ProgressCalculator.linearPositionMs(
            chapters.map { it.toModel() }.zip(ids).map { (chapter, id) -> chapter.copy(id = id) },
            currentId, backup.currentPositionMs,
        )
        bookDao.updateBook(currentBook.copy(currentChapterId = currentId, listenedDurationMs = linear))
        restoreListening(bookId, backup)
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
        val byRemoteItem = current.filter { it.remoteItemId != null }.associateBy { it.remoteItemId }
        var nextIndex = (current.maxOfOrNull { it.index } ?: -1) + 1
        val byPath = current.associateBy { ChapterIdentity.normalizeRelativePath(it.relativePath.ifBlank { it.fileName }) }
        var restoredChapters = 0
        val keyToCurrentId = mutableMapOf<String, Long>()
        backup.chapters.forEach { backupChapter ->
            val target = byKey[backupChapter.key]
                ?: byRemoteItem[backupChapter.remoteItemId]
                ?: byPath[ChapterIdentity.normalizeRelativePath(backupChapter.relativePath)]
            if (target != null) {
                chapterDao.update(
                    target.copy(
                        title = backupChapter.title,
                        durationMs = if (backup.sourceType == "FQNOVEL") backupChapter.durationMs else target.durationMs,
                        customTitle = backupChapter.customTitle,
                        completionState = backupChapter.completionState,
                        completedAt = backupChapter.completedAt,
                    ),
                )
                keyToCurrentId[backupChapter.key] = target.id
                restoredChapters++
            } else if (backup.sourceType == "FQNOVEL") {
                val id = chapterDao.insertAll(listOf(backupChapter.toEntity(existing.id).copy(index = nextIndex++))).single()
                keyToCurrentId[backupChapter.key] = id
                restoredChapters++
            }
        }
        val matchedChapterId = backup.currentChapterKey?.let { keyToCurrentId[it] }
        val currentChapterId = matchedChapterId ?: existing.currentChapterId
        val position = if (matchedChapterId != null) backup.currentPositionMs else existing.currentPositionMs
        val restoredChapterModels = chapterDao.getForBook(existing.id).map { it.toModel() }
        val linearPosition = com.tingxia.app.data.model.ProgressCalculator.linearPositionMs(restoredChapterModels, currentChapterId, position)
        bookDao.updateBook(
            existing.copy(
                title = backup.title,
                author = backup.author,
                coverPath = backup.coverUri ?: existing.coverPath,
                totalDurationMs = backup.totalDurationMs,
                lastPlayedAt = backup.lastPlayedAt,
                currentChapterId = currentChapterId,
                currentPositionMs = position,
                listenedDurationMs = linearPosition,
                createdAt = backup.createdAt,
                playbackSpeed = backup.playbackSpeed,
                autoPlayNext = backup.autoPlayNext,
                lastScannedAt = backup.lastScannedAt,
                skipIntroMs = backup.skipIntroMs,
                skipOutroMs = backup.skipOutroMs,
                sourceType = backup.sourceType,
                remoteBookId = backup.remoteBookId,
                remoteAudioBookId = backup.remoteAudioBookId,
                remoteToneId = backup.remoteToneId,
                description = backup.description,
                category = backup.category,
                wordCount = backup.wordCount,
                metaSyncSourceId = backup.metaSyncSourceId,
                metaSyncedAt = backup.metaSyncedAt,
                metaSyncBackup = backup.metaSyncBackup,
                remoteScore = backup.remoteScore,
                remoteListenCount = backup.remoteListenCount,
                remoteFinished = backup.remoteFinished,
                remoteLastChapterTitle = backup.remoteLastChapterTitle,
                remoteUpdateCheckedAt = backup.remoteUpdateCheckedAt,
                remoteNewChapterCount = backup.remoteNewChapterCount,
            ),
        )
        restoreListening(existing.id, backup)
        val existingMarks = bookmarkDao.getForBook(existing.id).map { Triple(it.chapterId, it.positionMs, it.note) }.toMutableSet()
        var restoredBookmarks = 0
        backup.bookmarks.forEach { mark ->
            keyToCurrentId[mark.chapterKey]?.let { chapterId ->
                if (existingMarks.add(Triple(chapterId, mark.positionMs, mark.note))) {
                    bookmarkDao.insert(mark.toEntity(existing.id, chapterId))
                    restoredBookmarks++
                }
            }
        }
        return restoredChapters to restoredBookmarks
    }

    private suspend fun restoreListening(bookId: Long, backup: BackupBook) {
        // Repeated imports are idempotent and do not erase newer local listening time.
        val local = listenSessionDao.getForBook(bookId).associate { it.dayStartMs to it.listenedMs }.toMutableMap()
        backup.listening.groupBy { it.dayStartMs }.forEach { (day, rows) ->
            val delta = rows.sumOf { it.listenedMs } - (local[day] ?: 0L)
            if (delta > 0L) listenSessionDao.accumulate(com.tingxia.app.data.db.ListenSessionEntity(bookId = bookId, dayStartMs = day, listenedMs = delta))
        }
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
        remoteItemId = remoteItemId,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
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
