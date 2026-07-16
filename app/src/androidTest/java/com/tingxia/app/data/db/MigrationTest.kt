package com.tingxia.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.data.db.migration.MIGRATION_1_2
import com.tingxia.app.data.db.migration.MIGRATION_2_3
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
}
