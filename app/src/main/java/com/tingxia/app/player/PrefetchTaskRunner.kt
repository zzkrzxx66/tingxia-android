package com.tingxia.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Called on the service's main dispatcher. Only the latest request may finish the service. */
internal class PrefetchTaskRunner(private val scope: CoroutineScope) {
    private val execution = Mutex()
    private class Request(var job: Job? = null)
    private var current: Request? = null

    // Keep ownership through cancellation until the underlying work has actually exited.
    val isRunning: Boolean get() = current != null

    fun replace(onFinished: (Throwable?) -> Unit, work: suspend () -> Unit) {
        val previous = current
        val request = Request()
        current = request
        previous?.job?.cancel()
        // Enter try/finally immediately so cancellation before the first dispatch
        // still delivers completion. Store ownership before synchronous work runs.
        request.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var failure: Throwable? = null
            try {
                // A third request must also wait for a still-unwinding first request,
                // even when the second request was cancelled while waiting.
                execution.withLock {
                    currentCoroutineContext().ensureActive()
                    work()
                }
            } catch (error: CancellationException) {
                failure = error
                throw error
            } catch (error: Exception) {
                failure = error
            } finally {
                if (current === request) {
                    current = null
                    if (scope.isActive) onFinished(failure)
                }
            }
        }
    }

    fun cancel() {
        current?.job?.cancel()
    }
}
