package com.tingxia.app.data.backup

import com.tingxia.app.data.repo.PreferencesSnapshot

data class BackupDocument(
    val formatVersion: Int,
    val exportedAt: Long,
    val settings: PreferencesSnapshot,
    val books: List<BackupBook>,
)

data class BackupBook(
    val title: String,
    val author: String?,
    val rootUri: String,
    val totalDurationMs: Long,
    val lastPlayedAt: Long,
    val currentChapterKey: String?,
    val currentPositionMs: Long,
    val listenedDurationMs: Long,
    val createdAt: Long,
    val playbackSpeed: Float?,
    val autoPlayNext: Boolean,
    val lastScannedAt: Long,
    val coverUri: String?,
    val chapters: List<BackupChapter>,
    val bookmarks: List<BackupBookmark>,
)

data class BackupChapter(
    val key: String,
    val title: String,
    val uri: String,
    val relativePath: String,
    val fileName: String,
    val fileSize: Long,
    val documentId: String?,
    val mimeType: String?,
    val durationMs: Long,
    val index: Int,
    val customTitle: String?,
    val completionState: Int,
    val completedAt: Long?,
)

data class BackupBookmark(
    val chapterKey: String,
    val positionMs: Long,
    val note: String?,
    val createdAt: Long,
)

data class BackupRestoreResult(
    val restoredBooks: Int,
    val createdReauthBooks: Int,
    val restoredChapters: Int,
    val restoredBookmarks: Int,
    val skippedBooks: Int,
)
