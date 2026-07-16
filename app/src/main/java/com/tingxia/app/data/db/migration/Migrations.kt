package com.tingxia.app.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Historical v1 → v2 (shipped):
 * - add books.listenedDurationMs
 * - backfill cumulative progress from chapters
 *
 * Note: unique rootUri index was incorrectly folded into this migration in a later
 * commit while still advertising version=2. New installs/upgrades use:
 * - 1→2 via this migration (no unique index)
 * - 2→3 via [MIGRATION_2_3] (dedupe + unique index)
 * For brand-new paths Room may also apply 1→2→3.
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
 * Historical v2 (listenedDurationMs only) → v3 (unique rootUri index).
 * Safe for users who already upgraded to the intermediate "v2 without index".
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // If a buggy intermediate build already created the unique index on v2,
        // CREATE UNIQUE INDEX IF NOT EXISTS is a no-op after dedupe.
        dedupeBooksByRootUri(db)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_rootUri` ON `books` (`rootUri`)",
        )
    }
}

/** Optional combined path documentation — Room uses stepwise migrations. */

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

private fun dedupeBooksByRootUri(db: SupportSQLiteDatabase) {
    // Keep lowest id per rootUri; chapters cascade-deleted via FK if present.
    // Chapters table has ON DELETE CASCADE in Room schema for bookId.
    db.execSQL(
        """
        DELETE FROM chapters WHERE bookId NOT IN (
            SELECT MIN(id) FROM books GROUP BY rootUri
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        DELETE FROM books WHERE id NOT IN (
            SELECT MIN(id) FROM books GROUP BY rootUri
        )
        """.trimIndent(),
    )
}
