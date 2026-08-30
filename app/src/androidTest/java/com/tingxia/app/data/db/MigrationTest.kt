package com.tingxia.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.data.db.migration.MIGRATION_1_2
import com.tingxia.app.data.db.migration.MIGRATION_2_3
import com.tingxia.app.data.db.migration.MIGRATION_3_4
import com.tingxia.app.data.db.migration.MIGRATION_4_5
import com.tingxia.app.data.db.migration.MIGRATION_7_8
import com.tingxia.app.data.db.migration.MIGRATION_8_9
import com.tingxia.app.data.db.migration.MIGRATION_9_10
import com.tingxia.app.data.db.migration.MIGRATION_10_11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TingXiaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsListenedDurationAndBackfills_withoutUniqueIndex() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, createdAt, needsReauth
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/x', 6000, 1,
                    2, 500, 0, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (id, bookId, title, uri, `index`, durationMs, fileName)
                VALUES
                    (1, 1, 'c1', 'u1', 0, 1000, '01.mp3'),
                    (2, 1, 'c2', 'u2', 1, 2000, '02.mp3'),
                    (3, 1, 'c3', 'u3', 2, 3000, '03.mp3')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2).apply {
            query("SELECT listenedDurationMs FROM books WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1500L, c.getLong(0))
            }
            query("PRAGMA index_list('books')").use { c ->
                var foundRoot = false
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndex("name"))
                    if (name != null && name.contains("rootUri")) foundRoot = true
                }
                assertFalse("v2 must not require rootUri unique index", foundRoot)
            }
            close()
        }
    }

    @Test
    fun migrate2To3_keepsMostRecentlyPlayedDuplicate() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt, needsReauth
                ) VALUES
                    (1, 'old', NULL, NULL, 'content://tree/dup', 0, 10, 10, 1, 10, 0, 0),
                    (2, 'current', NULL, NULL, 'content://tree/dup', 0, 999, 11, 500, 1500, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (id, bookId, title, uri, `index`, durationMs, fileName)
                VALUES
                    (10, 1, 'c', 'u', 0, 1000, 'a.mp3'),
                    (11, 2, 'c', 'u', 0, 1000, 'b.mp3')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3).apply {
            query("SELECT COUNT(*) FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            query("SELECT id, title, listenedDurationMs FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2L, c.getLong(0))
                assertEquals("current", c.getString(1))
                assertEquals(1500L, c.getLong(2))
            }
            query("SELECT COUNT(*) FROM chapters").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_whenUniqueIndexAlreadyPresent_isIdempotent() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt, needsReauth
                ) VALUES (1, 'only', NULL, NULL, 'content://tree/z', 0, 1, NULL, 0, 0, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_rootUri` ON `books` (`rootUri`)",
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3).apply {
            query("SELECT COUNT(*) FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_appliesAllDedupeTieBreakers() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt, needsReauth
                ) VALUES
                    (1, 'older-play', NULL, NULL, 'content://tree/tie', 0, 90, NULL, 9999, 9999, 0, 0),
                    (2, 'more-listened', NULL, NULL, 'content://tree/tie', 0, 100, NULL, 10, 2000, 0, 0),
                    (3, 'higher-position', NULL, NULL, 'content://tree/tie', 0, 100, NULL, 20, 2000, 0, 0),
                    (4, 'higher-id', NULL, NULL, 'content://tree/tie', 0, 100, NULL, 20, 2000, 0, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3).apply {
            query("SELECT id, title FROM books").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(4L, c.getLong(0))
                assertEquals("higher-id", c.getString(1))
                assertFalse(c.moveToNext())
            }
            close()
        }
    }

    @Test
    fun migrate1To3_viaFullChain() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, createdAt, needsReauth
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/y', 1000, 1,
                    1, 100, 0, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (id, bookId, title, uri, `index`, durationMs, fileName)
                VALUES (1, 1, 'c1', 'u1', 0, 1000, '01.mp3')
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_1_2, MIGRATION_2_3).apply {
            query("SELECT listenedDurationMs FROM books WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(100L, c.getLong(0))
            }
            close()
        }
    }

    @Test
    fun migrate3To4_addsBookmarksAndChapterIdentityFields() {
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt, needsReauth
                ) VALUES (1, 't', NULL, NULL, 'content://tree/z', 1000, 1, 1, 10, 10, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (id, bookId, title, uri, `index`, durationMs, fileName)
                VALUES (1, 1, 'c1', 'u1', 0, 1000, '01.mp3')
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4).apply {
            query("SELECT relativePath, stableKey, playbackSpeed, autoPlayNext FROM books, chapters WHERE books.id=1 AND chapters.id=1").use { c ->
                // just ensure query works; relativePath backfilled
            }
            query("SELECT relativePath FROM chapters WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("01.mp3", c.getString(0))
            }
            query("SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'").use { c ->
                assertTrue(c.moveToFirst())
            }
            close()
        }
    }

    @Test
    fun migrate4To5_addsSkipOffsetsWithZeroDefaults() {
        helper.createDatabase(testDb, 4).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt,
                    needsReauth, playbackSpeed, autoPlayNext, lastScannedAt
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/z', 1000, 1,
                    NULL, 10, 10, 0, 0, NULL, 1, 0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 5, true, MIGRATION_4_5).apply {
            query("SELECT skipIntroMs, skipOutroMs FROM books WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
                assertEquals(0L, c.getLong(1))
            }
            close()
        }
    }

    @Test
    fun migrate7To8_addsChapterClipsAndListenSessions() {
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt,
                    needsReauth, playbackSpeed, autoPlayNext, lastScannedAt, skipIntroMs,
                    skipOutroMs, sourceType, description, category, wordCount
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/m4b', 1000, 1,
                    NULL, 0, 0, 0, 0, NULL, 1, 0, 0, 0, 'LOCAL', NULL, NULL, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (
                    id, bookId, title, uri, `index`, durationMs, fileName, relativePath,
                    fileSize, completionState
                ) VALUES (1, 1, 'c1', 'u1', 0, 1000, 'book.m4b', 'book.m4b', 100, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 8, true, MIGRATION_7_8).apply {
            query("SELECT clipStartMs, clipEndMs FROM chapters WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertTrue(c.isNull(1))
            }
            // listen_sessions must accept writes after migration.
            execSQL(
                "INSERT INTO listen_sessions (bookId, dayStartMs, listenedMs) VALUES (1, 0, 5000)",
            )
            query("SELECT listenedMs FROM listen_sessions WHERE bookId = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(5000L, c.getLong(0))
            }
            close()
        }
    }

    @Test
    fun migrate8To9_addsCachedFlag() {
        helper.createDatabase(testDb, 8).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt,
                    needsReauth, playbackSpeed, autoPlayNext, lastScannedAt, skipIntroMs,
                    skipOutroMs, sourceType, description, category, wordCount
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/x', 1000, 1,
                    NULL, 0, 0, 0, 0, NULL, 1, 0, 0, 0, 'LOCAL', NULL, NULL, 0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO chapters (
                    id, bookId, title, uri, `index`, durationMs, fileName, relativePath,
                    fileSize, completionState
                ) VALUES (1, 1, 'c1', 'u1', 0, 1000, 'a.mp3', 'a.mp3', 100, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 9, true, MIGRATION_8_9).apply {
            query("SELECT isCached FROM chapters WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            execSQL("UPDATE chapters SET isCached = 1 WHERE id = 1")
            query("SELECT isCached FROM chapters WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate9To10_addsOnlineMetaSyncColumns() {
        helper.createDatabase(testDb, 9).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt,
                    needsReauth, playbackSpeed, autoPlayNext, lastScannedAt, skipIntroMs,
                    skipOutroMs, sourceType, description, category, wordCount
                ) VALUES (
                    1, 't', NULL, NULL, 'content://tree/x', 1000, 1,
                    NULL, 0, 0, 0, 0, NULL, 1, 0, 0, 0, 'LOCAL', NULL, NULL, 0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 10, true, MIGRATION_9_10).apply {
            query("SELECT metaSyncSourceId, metaSyncedAt, metaSyncBackup FROM books WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertEquals(0L, c.getLong(1))
                assertTrue(c.isNull(2))
            }
            execSQL(
                "UPDATE books SET metaSyncSourceId = '7143', metaSyncedAt = 99, " +
                    "metaSyncBackup = 'tx-meta-backup/1' WHERE id = 1",
            )
            query("SELECT metaSyncSourceId, metaSyncedAt FROM books WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("7143", c.getString(0))
                assertEquals(99L, c.getLong(1))
            }
            close()
        }
    }

    @Test
    fun migrate10To11_addsRemoteCatalogueColumns() {
        helper.createDatabase(testDb, 10).apply {
            execSQL(
                """
                INSERT INTO books (
                    id, title, author, coverPath, rootUri, totalDurationMs, lastPlayedAt,
                    currentChapterId, currentPositionMs, listenedDurationMs, createdAt,
                    needsReauth, playbackSpeed, autoPlayNext, lastScannedAt, skipIntroMs,
                    skipOutroMs, sourceType, description, category, wordCount,
                    metaSyncSourceId, metaSyncedAt, metaSyncBackup
                ) VALUES (
                    1, 't', NULL, NULL, 'fqnovel://7088', 1000, 1,
                    NULL, 0, 0, 0, 0, NULL, 1, 0, 0, 0, 'FQNOVEL', NULL, NULL, 0,
                    NULL, 0, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 11, true, MIGRATION_10_11).apply {
            query(
                "SELECT remoteScore, remoteListenCount, remoteFinished, remoteLastChapterTitle, " +
                    "remoteUpdateCheckedAt, remoteNewChapterCount FROM books WHERE id = 1",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertEquals(0L, c.getLong(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
                assertEquals(0L, c.getLong(4))
                assertEquals(0, c.getInt(5))
            }
            execSQL(
                "UPDATE books SET remoteScore = '9.4', remoteListenCount = 552683, " +
                    "remoteFinished = 1, remoteLastChapterTitle = '番外 007', " +
                    "remoteUpdateCheckedAt = 1700, remoteNewChapterCount = 3 WHERE id = 1",
            )
            query(
                "SELECT remoteScore, remoteListenCount, remoteFinished, remoteNewChapterCount " +
                    "FROM books WHERE id = 1",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("9.4", c.getString(0))
                assertEquals(552683L, c.getLong(1))
                assertEquals(1, c.getInt(2))
                assertEquals(3, c.getInt(3))
            }
            close()
        }
    }
}
