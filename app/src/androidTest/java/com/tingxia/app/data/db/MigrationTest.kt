package com.tingxia.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.data.db.migration.MIGRATION_1_2
import org.junit.Assert.assertEquals
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
    fun migrate1To2_addsColumnAndBackfillsListenedDuration() {
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
                // before chapter2 (1000) + 500 position
                assertEquals(1500L, c.getLong(0))
            }
            // unique index exists
            query("PRAGMA index_list('books')").use { c ->
                var found = false
                while (c.moveToNext()) {
                    val name = c.getString(c.getColumnIndex("name"))
                    if (name != null && name.contains("rootUri")) found = true
                }
                assertTrue("expected rootUri unique index", found)
            }
            close()
        }
    }
}
