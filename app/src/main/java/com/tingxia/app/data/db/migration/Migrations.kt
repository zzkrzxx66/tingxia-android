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
