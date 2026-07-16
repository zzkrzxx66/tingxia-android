package com.tingxia.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["rootUri"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String? = null,
    val coverPath: String? = null,
    val rootUri: String,
    val totalDurationMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val currentChapterId: Long? = null,
    val currentPositionMs: Long = 0L,
    val listenedDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val needsReauth: Boolean = false,
    val playbackSpeed: Float? = null,
    val autoPlayNext: Boolean = true,
    val lastScannedAt: Long = 0L,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("bookId"),
        Index(value = ["bookId", "index"], unique = true),
        Index("stableKey"),
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val uri: String,
    val index: Int,
    val durationMs: Long = 0L,
    val fileName: String,
    val relativePath: String = fileName,
    val fileSize: Long = 0L,
    val documentId: String? = null,
    val mimeType: String? = null,
    val stableKey: String? = null,
    val customTitle: String? = null,
    val completionState: Int = 0,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId"), Index("chapterId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterId: Long,
    val positionMs: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
