package com.tingxia.app.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Serial progress sink: all writes go through a single consumer.
 * Call [closeAndJoin] on teardown so the final write is not overtaken by stale items.
 */
class ProgressWriter(
    private val scope: CoroutineScope,
    private val save: suspend (bookId: Long, chapterId: Long, positionMs: Long) -> Unit,
    writerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    data class Write(
        val seq: Long,
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
        val isFinal: Boolean = false,
        val ack: Channel<Unit>? = null,
    )

    private val channel = Channel<Write>(capacity = Channel.UNLIMITED)
    private val seqGen = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    private val writerJob: Job = scope.launch(writerDispatcher) {
        for (write in channel) {
            try {
                save(write.bookId, write.chapterId, write.positionMs)
            } catch (_: Exception) {
            } finally {
                write.ack?.trySend(Unit)
                write.ack?.close()
            }
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
     * Enqueue a final progress write, close the channel, wait for drain.
     * Never bypasses the queue with a direct DB write.
     *
     * @return true if the final write was processed (or no final write needed).
     */
    suspend fun closeWithFinal(
        bookId: Long?,
        chapterId: Long?,
        positionMs: Long,
        timeoutMs: Long = 1_500,
    ): Boolean {
        if (!closed.compareAndSet(false, true)) {
            // already closed
            return withTimeoutOrNull(timeoutMs) {
                writerJob.join()
                true
            } ?: false
        }
        val finalBookId = bookId
        val finalChapterId = chapterId
        val ack = if (finalBookId != null && finalChapterId != null) Channel<Unit>(1) else null
        if (finalBookId != null && finalChapterId != null && ack != null) {
            channel.trySend(
                Write(
                    seq = seqGen.incrementAndGet(),
                    bookId = finalBookId,
                    chapterId = finalChapterId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    isFinal = true,
                    ack = ack,
                ),
            )
        }
        channel.close()
        return withTimeoutOrNull(timeoutMs) {
            if (ack != null) {
                ack.receiveCatching()
            }
            writerJob.join()
            true
        } ?: run {
            writerJob.cancel()
            false
        }
    }

    fun cancel() {
        closed.set(true)
        channel.close()
        writerJob.cancel()
    }
}
