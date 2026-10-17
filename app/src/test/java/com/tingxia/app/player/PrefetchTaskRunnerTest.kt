package com.tingxia.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchTaskRunnerTest {
    @Test
    fun replacingQueueWaitsForOldCleanupAndOnlyFinishesLatestRequest() = runTest {
        val runner = PrefetchTaskRunner(this)
        val cleanup = CompletableDeferred<Unit>()
        val latest = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        runner.replace({ events += "old finished" }) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
                events += "old cleaned"
            }
        }
        runner.replace({ events += "latest finished" }) {
            events += "latest started"
            latest.await()
        }
        runCurrent()
        assertTrue(events.isEmpty())
        assertTrue(runner.isRunning)

        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("old cleaned", "latest started"), events)
        assertTrue(runner.isRunning)
        latest.complete(Unit)
        runCurrent()
        assertEquals(listOf("old cleaned", "latest started", "latest finished"), events)
        assertFalse(runner.isRunning)
    }

    @Test
    fun rapidThirdRequestCannotOvertakeFirstRequestsCleanup() = runTest {
        val runner = PrefetchTaskRunner(this)
        val cleanup = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        runner.replace({ events += "first finished" }) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
                events += "first cleaned"
            }
        }
        runner.replace({ events += "second finished" }) { events += "second started" }
        runner.replace({ events += "third finished" }) { events += "third started" }
        runCurrent()
        assertTrue(events.isEmpty())
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(listOf("first cleaned", "third started", "third finished"), events)
        assertFalse(runner.isRunning)
    }

    @Test
    fun cancellationBeforeFirstDispatchFinishesOnce() = runTest {
        val runner = PrefetchTaskRunner(this)
        val failures = mutableListOf<Throwable?>()
        runner.replace({ failures += it }) { awaitCancellation() }
        runner.cancel()
        runner.cancel()
        runCurrent()
        assertEquals(1, failures.size)
        assertTrue(failures.single() is CancellationException)
        assertFalse(runner.isRunning)
    }

    @Test
    fun synchronousCompletionAndFailureReleaseOwnership() = runTest {
        val runner = PrefetchTaskRunner(this)
        val failures = mutableListOf<Throwable?>()
        runner.replace({ failures += it }) { }
        assertFalse(runner.isRunning)
        val error = IllegalStateException("network unavailable")
        runner.replace({ failures += it }) { throw error }
        assertFalse(runner.isRunning)
        assertEquals(listOf(null, error), failures)
    }
}
