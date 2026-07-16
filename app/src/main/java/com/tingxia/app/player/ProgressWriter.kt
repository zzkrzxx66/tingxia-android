package com.tingxia.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Serial progress sink: all writes go through a single consumer.
 *
 * [closeWithFinal] enqueues the final write last, closes the channel, and waits
 * for that write to succeed. On timeout it cancels the old writer (so stale
 * in-flight items cannot complete later) and performs one direct final save.
 */
class ProgressWriter(
    private val scope: CoroutineScope,
    private val save: suspend (bookId: Long, chapterId: Long, positionMs: Long) -> Unit,
    private val writerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val finalMaxAttempts: Int = 3,
    private val retryDelayMs: Long = 50,
) {
    data class Write(
        val seq: Long,
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
        val isFinal: Boolean = false,
        val ack: Channel<Boolean>? = null,
    )

    private val channel = Channel<Write>(capacity = Channel.UNLIMITED)
    private val seqGen = AtomicLong(0)
    private val closed = AtomicBoolean(false)

    private val writerJob: Job = scope.launch(writerDispatcher) {
        for (write in channel) {
            val ok = try {
                if (write.isFinal) {
                    saveWithRetry(write.bookId, write.chapterId, write.positionMs)
                } else {
                    save(write.bookId, write.chapterId, write.positionMs)
                    true
                }
            } catch (_: CancellationException) {
                write.ack?.trySend(false)
                write.ack?.close()
                throw CancellationException()
            } catch (_: Exception) {
                false
            }
            write.ack?.trySend(ok)
            write.ack?.close()
        }
    }

    fun enqueue(bookId: Long, chapterId: Long, positionMs: Long) {
        if (closed.get()) return
        channel.trySend(
            Write(
                seq = seqGen.incrementAndGet(),
                bookId = bookId,
                chapterId = chapterId,
                positionMs = positionMs.coerceAtLeast(0L),
            ),
        )
    }

    /**
     * @return true only if the final progress was saved successfully
     *         (or no final write was needed and the queue drained).
     */
    suspend fun closeWithFinal(
        bookId: Long?,
        chapterId: Long?,
        positionMs: Long,
        timeoutMs: Long = 1_500,
    ): Boolean {
        val finalBookId = bookId
        val finalChapterId = chapterId
        val finalPos = positionMs.coerceAtLeast(0L)

        if (!closed.compareAndSet(false, true)) {
            // Already closed: best-effort join only.
            return withTimeoutOrNull(timeoutMs) {
                writerJob.join()
                true
            } ?: false
        }

        val needFinal = finalBookId != null && finalChapterId != null
        val ack = if (needFinal) Channel<Boolean>(1) else null
        if (needFinal && ack != null) {
            channel.trySend(
                Write(
                    seq = seqGen.incrementAndGet(),
                    bookId = finalBookId!!,
                    chapterId = finalChapterId!!,
                    positionMs = finalPos,
                    isFinal = true,
                    ack = ack,
                ),
            )
        }
        channel.close()

        val drainedOk = withTimeoutOrNull(timeoutMs) {
            val finalOk = if (ack != null) {
                ack.receiveCatching().getOrNull() ?: false
            } else {
                true
            }
            writerJob.join()
            finalOk
        }

        if (drainedOk == true) return true

        // Timeout or failure: stop old writer so no stale save can complete later.
        writerJob.cancel()
        withTimeoutOrNull(500) { writerJob.join() }

        if (!needFinal) return drainedOk == true

        // One last direct attempt after the queue is dead — cannot be overtaken.
        return try {
            withContext(writerDispatcher) {
                saveWithRetry(finalBookId!!, finalChapterId!!, finalPos)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun cancel() {
        closed.set(true)
        channel.close()
        writerJob.cancel()
    }

    private suspend fun saveWithRetry(
        bookId: Long,
        chapterId: Long,
        positionMs: Long,
    ): Boolean {
        var last: Exception? = null
        repeat(finalMaxAttempts) { attempt ->
            try {
                save(bookId, chapterId, positionMs)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (attempt < finalMaxAttempts - 1) {
                    delay(retryDelayMs)
                }
            }
        }
        return false
    }
}
