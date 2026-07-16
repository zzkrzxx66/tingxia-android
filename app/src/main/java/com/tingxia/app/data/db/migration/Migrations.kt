package com.tingxia.app.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2:
 * - add books.listenedDurationMs
 * - backfill from chapters: sum(duration of chapters before current) + currentPositionMs
 * - unique index on rootUri to prevent concurrent duplicate imports
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE books ADD COLUMN listenedDurationMs INTEGER NOT NULL DEFAULT 0",
        )

        // Backfill cumulative progress for existing rows that have a current chapter.
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

                // Look up current chapter index
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
                    // Unknown chapter: keep position only
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
                val listened = before + pos
                db.execSQL(
                    "UPDATE books SET listenedDurationMs = ? WHERE id = ?",
                    arrayOf(listened, bookId),
                )
            }
        }

        // Prevent concurrent duplicate imports of the same SAF tree.
        // If legacy data already has duplicates, keep the lowest id and drop extras first.
        db.execSQL(
            """
            DELETE FROM books WHERE id NOT IN (
                SELECT MIN(id) FROM books GROUP BY rootUri
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_books_rootUri ON books(rootUri)",
        )
    }
}
