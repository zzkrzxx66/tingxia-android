package com.tingxia.app.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressWriterOrderingTest {

    data class Save(val bookId: Long, val chapterId: Long, val positionMs: Long)

    @Test
    fun fifo_andFinalLast_despiteSlowSaves() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                delay(if (c == 1L) 50 else 5)
                saved += Save(b, c, p)
            },
            writerDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        writer.enqueue(1, 1, 100)
        writer.enqueue(1, 1, 200)
        writer.enqueue(1, 2, 0)

        assertTrue(writer.closeWithFinal(1, 2, 10, timeoutMs = 5_000))
        assertEquals(
            listOf(
                Save(1, 1, 100),
                Save(1, 1, 200),
                Save(1, 2, 0),
                Save(1, 2, 10),
            ),
            saved.toList(),
        )
    }

    @Test
    fun closeWithFinal_returnsFalseWhenSaveAlwaysFails() = runTest {
        val attempts = AtomicInteger(0)
        val writer = ProgressWriter(
            scope = this,
            save = { _, _, _ ->
                attempts.incrementAndGet()
                error("db down")
            },
            writerDispatcher = UnconfinedTestDispatcher(testScheduler),
            finalMaxAttempts = 3,
            retryDelayMs = 1,
        )
        writer.enqueue(1, 1, 1)
        val ok = writer.closeWithFinal(1, 2, 0, timeoutMs = 5_000)
        assertFalse(ok)
        assertTrue("expected retries, got ${attempts.get()}", attempts.get() >= 3)
    }

    @Test
    fun closeWithFinal_retriesUntilSuccess() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val attempts = AtomicInteger(0)
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                val n = attempts.incrementAndGet()
                if (c == 99L && n <= 2) error("transient")
                saved += Save(b, c, p)
            },
            writerDispatcher = UnconfinedTestDispatcher(testScheduler),
            finalMaxAttempts = 3,
            retryDelayMs = 1,
        )
        assertTrue(writer.closeWithFinal(1, 99, 7, timeoutMs = 5_000))
        assertEquals(listOf(Save(1, 99, 7)), saved.toList())
        assertEquals(3, attempts.get())
    }

    @Test
    fun timeout_cancelsStaleAndDoesDirectFinalSave() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                if (c == 1L) {
                    delay(10_000) // stale item blocks queue
                }
                saved += Save(b, c, p)
            },
            writerDispatcher = dispatcher,
            finalMaxAttempts = 1,
            retryDelayMs = 1,
        )

        writer.enqueue(1, 1, 111)
        runCurrent() // writer starts processing stale item

        val deferred = async {
            writer.closeWithFinal(1, 2, 0, timeoutMs = 100)
        }
        // exceed close timeout so fallback path runs
        advanceTimeBy(150)
        runCurrent()
        val ok = deferred.await()

        assertTrue(ok)
        // Stale chapter must not win; only final chapter 2 from direct save.
        assertEquals(listOf(Save(1, 2, 0)), saved.toList())
    }

    @Test
    fun timeout_doesNotDirectSaveUntilNonCooperativeOldWriterStops() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                if (c == 1L) {
                    withContext(NonCancellable) { delay(10_000) }
                }
                saved += Save(b, c, p)
            },
            writerDispatcher = dispatcher,
            finalMaxAttempts = 1,
        )

        writer.enqueue(1, 1, 111)
        runCurrent()

        val deferred = async {
            writer.closeWithFinal(1, 2, 0, timeoutMs = 100)
        }
        advanceTimeBy(700)
        runCurrent()

        assertFalse(deferred.await())
        assertTrue("final direct save must not race the old writer", saved.none { it.chapterId == 2L })
    }
}
