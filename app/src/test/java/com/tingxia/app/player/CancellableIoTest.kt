package com.tingxia.app.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CancellableIoTest {
    @Test
    fun cancellationSignalsWriterAndWaitsForItsCleanup() = runBlocking {
        withTimeout(5_000) {
            val started = CompletableDeferred<Unit>()
            val cancellation = CountDownLatch(1)
            val cleanup = CountDownLatch(1)
            val exited = AtomicBoolean(false)
            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                cancellableIo(cancel = { cancellation.countDown() }) {
                    started.complete(Unit)
                    try {
                        check(cancellation.await(3, TimeUnit.SECONDS))
                        check(cleanup.await(3, TimeUnit.SECONDS))
                    } finally {
                        exited.set(true)
                    }
                }
            }
            try {
                started.await()
                worker.cancel()
                assertTrue(withContext(Dispatchers.IO) { cancellation.await(1, TimeUnit.SECONDS) })
                assertFalse(worker.isCompleted)
                cleanup.countDown()
                worker.join()
                assertTrue(exited.get())
            } finally {
                cancellation.countDown()
                cleanup.countDown()
                worker.cancelAndJoin()
            }
        }
    }

    @Test
    fun successAndFailureAreReturnedToCaller() = runBlocking {
        var cancelled = false
        assertEquals(42, cancellableIo(cancel = { cancelled = true }) { 42 })
        assertFalse(cancelled)
        val error = IOException("read failed")
        val failure = runCatching {
            cancellableIo(cancel = {}) { throw error }
        }.exceptionOrNull()
        // Coroutine stack-trace recovery may copy the exception instance.
        assertTrue(failure is IOException)
        assertEquals(error.message, failure?.message)
    }
}
