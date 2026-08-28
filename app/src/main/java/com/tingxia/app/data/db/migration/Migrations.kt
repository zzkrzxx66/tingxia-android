package com.tingxia.app.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Historical v1 → v2:
 * - add books.listenedDurationMs
 * - backfill cumulative progress from chapters
 * No unique rootUri index here (that is v3).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE books ADD COLUMN listenedDurationMs INTEGER NOT NULL DEFAULT 0",
        )
        backfillListenedDuration(db)
    }
}

/**
 * Historical v2 → v3:
 * - dedupe books that share rootUri (prefer most recently / furthest listened)
 * - create unique index on rootUri
 *
 * Also safe if a buggy intermediate build already created the unique index on v2:
 * CREATE UNIQUE INDEX IF NOT EXISTS is a no-op after dedupe.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dedupeBooksByRootUri(db)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_rootUri` ON `books` (`rootUri`)",
        )
    }
}

private fun backfillListenedDuration(db: SupportSQLiteDatabase) {
    db.query(
        """
        SELECT b.id, b.currentChapterId, b.currentPositionMs
        FROM books b
        WHERE b.currentChapterId IS NOT NULL
        """.trimIndent(),
    ).use { cursor ->
        val idIdx = cursor.getColumnIndex("id")
        val chapterIdx = cursor.getColumnIndex("currentChapterId")
        val posIdx = cursor.getColumnIndex("currentPositionMs")
        while (cursor.moveToNext()) {
            val bookId = cursor.getLong(idIdx)
            val chapterId = cursor.getLong(chapterIdx)
            val positionMs = cursor.getLong(posIdx).coerceAtLeast(0L)

            var currentIndex: Int? = null
            var currentDuration = 0L
            db.query(
                "SELECT `index`, durationMs FROM chapters WHERE id = ? AND bookId = ?",
                arrayOf(chapterId, bookId),
            ).use { ch ->
                if (ch.moveToFirst()) {
                    currentIndex = ch.getInt(0)
                    currentDuration = ch.getLong(1).coerceAtLeast(0L)
                }
            }
            if (currentIndex == null) {
                db.execSQL(
                    "UPDATE books SET listenedDurationMs = ? WHERE id = ?",
                    arrayOf(positionMs, bookId),
                )
                continue
            }

            var before = 0L
            db.query(
                "SELECT durationMs FROM chapters WHERE bookId = ? AND `index` < ?",
                arrayOf(bookId, currentIndex),
            ).use { prev ->
                while (prev.moveToNext()) {
                    before += prev.getLong(0).coerceAtLeast(0L)
                }
            }
            val pos = if (currentDuration > 0L) {
                positionMs.coerceIn(0L, currentDuration)
            } else {
                positionMs
            }
            db.execSQL(
                "UPDATE books SET listenedDurationMs = ? WHERE id = ?",
                arrayOf(before + pos, bookId),
            )
        }
    }
}

/**
 * Keep one book per rootUri using the same ranking as [BookDedupePolicy]:
 * lastPlayedAt DESC, listenedDurationMs DESC, currentPositionMs DESC, id DESC.
 */
private fun dedupeBooksByRootUri(db: SupportSQLiteDatabase) {
    // SQLite: correlated subquery picks the winner per rootUri.
    // Delete chapters of losing books first (FK cascade may not apply during raw migration).
    db.execSQL(
        """
        DELETE FROM chapters WHERE bookId IN (
            SELECT b.id FROM books b
            WHERE b.id NOT IN (
                SELECT b2.id FROM books b2
                WHERE b2.rootUri = b.rootUri
                ORDER BY
                    b2.lastPlayedAt DESC,
                    b2.listenedDurationMs DESC,
                    b2.currentPositionMs DESC,
                    b2.id DESC
                LIMIT 1
            )
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        DELETE FROM books WHERE id NOT IN (
            SELECT id FROM (
                SELECT b.id AS id
                FROM books b
                WHERE b.id = (
                    SELECT b2.id FROM books b2
                    WHERE b2.rootUri = b.rootUri
                    ORDER BY
                        b2.lastPlayedAt DESC,
                        b2.listenedDurationMs DESC,
                        b2.currentPositionMs DESC,
                        b2.id DESC
                    LIMIT 1
                )
            )
        )
        """.trimIndent(),
    )
}

/**
 * v3 → v4:
 * - book playback settings
 * - chapter rescan identity fields + completion placeholders
 * - bookmarks table
 *
 * Historical chapters keep stableKey = NULL until first successful rescan.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN playbackSpeed REAL")
        db.execSQL("ALTER TABLE books ADD COLUMN autoPlayNext INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE books ADD COLUMN lastScannedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE chapters ADD COLUMN relativePath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE chapters ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN documentId TEXT")
        db.execSQL("ALTER TABLE chapters ADD COLUMN mimeType TEXT")
        db.execSQL("ALTER TABLE chapters ADD COLUMN stableKey TEXT")
        db.execSQL("ALTER TABLE chapters ADD COLUMN customTitle TEXT")
        db.execSQL("ALTER TABLE chapters ADD COLUMN completionState INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE chapters ADD COLUMN completedAt INTEGER")

        // Backfill relativePath from fileName for old rows (SQLite default on ALTER is empty string).
        db.execSQL("UPDATE chapters SET relativePath = fileName WHERE relativePath = '' OR relativePath IS NULL")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_stableKey` ON `chapters` (`stableKey`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bookmarks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookId` INTEGER NOT NULL,
                `chapterId` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_bookId` ON `bookmarks` (`bookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_chapterId` ON `bookmarks` (`chapterId`)")
    }
}

/** v4 → v5: per-book chapter intro/outro skipping. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN skipIntroMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN skipOutroMs INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5 → v6: stable remote-source identifiers for online audiobooks. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL("ALTER TABLE books ADD COLUMN remoteBookId TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN remoteAudioBookId TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN remoteToneId TEXT")
        db.execSQL("ALTER TABLE chapters ADD COLUMN remoteItemId TEXT")
    }
}

/** v6 → v7: online book metadata (blurb, category, word count) kept on the shelf copy. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v7 → v8:
 * - chapters gain an optional embedded-chapter clip window (m4b)
 * - listen_sessions powers the listening-stats page
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN clipStartMs INTEGER")
        db.execSQL("ALTER TABLE chapters ADD COLUMN clipEndMs INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listen_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookId` INTEGER NOT NULL,
                `dayStartMs` INTEGER NOT NULL,
                `listenedMs` INTEGER NOT NULL,
                FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_sessions_bookId` ON `listen_sessions` (`bookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_sessions_dayStartMs` ON `listen_sessions` (`dayStartMs`)")
    }
}

/** v8 → v9: chapters remember whether their audio sits in the offline cache. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN isCached INTEGER NOT NULL DEFAULT 0")
    }
}

/** v9 → v10: local books can be linked to an online catalogue entry for metadata sync. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN metaSyncSourceId TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN metaSyncedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE books ADD COLUMN metaSyncBackup TEXT")
    }
}
