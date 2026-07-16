package com.tingxia.app.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressWriterOrderingTest {

    data class Save(val bookId: Long, val chapterId: Long, val positionMs: Long)

    @Test
    fun fifo_processesInEnqueueOrder_despiteSlowSaves() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                // Simulate variable IO latency; later items must not overtake earlier ones.
                delay(if (c == 1L) 50 else 5)
                saved += Save(b, c, p)
            },
            writerDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        writer.enqueue(1, 1, 100)
        writer.enqueue(1, 1, 200)
        writer.enqueue(1, 2, 0) // chapter transition

        assertTrue(writer.closeWithFinal(1, 2, 10, timeoutMs = 5_000))

        assertEquals(
            listOf(
                Save(1, 1, 100),
                Save(1, 1, 200),
                Save(1, 2, 0),
                Save(1, 2, 10), // final
            ),
            saved.toList(),
        )
    }

    @Test
    fun closeWithFinal_waitsForPendingStaleThenFinal() = runTest {
        val saved = CopyOnWriteArrayList<Save>()
        val gate = AtomicInteger(0)
        val writer = ProgressWriter(
            scope = this,
            save = { b, c, p ->
                // First (stale) write is slow.
                if (gate.getAndIncrement() == 0) delay(80)
                saved += Save(b, c, p)
            },
            writerDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        writer.enqueue(1, 1, 999) // stale old chapter already in queue
        // Final current chapter must be last after queue drain
        val ok = writer.closeWithFinal(1, 2, 0, timeoutMs = 5_000)
        assertTrue(ok)
        assertEquals(listOf(Save(1, 1, 999), Save(1, 2, 0)), saved.toList())
    }

    @Test
    fun transitionEnterPolicy_pointsAtNewChapter() {
        val enter = ProgressLeavePolicy.progressOnEnter(1, 11, 0)
        assertEquals(11L, enter!!.chapterId)
        assertEquals(0L, enter.positionMs)
    }
}
