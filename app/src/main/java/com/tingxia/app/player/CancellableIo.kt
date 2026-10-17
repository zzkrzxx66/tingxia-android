package com.tingxia.app.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Signal cancellation immediately, but keep ownership until the IO worker has exited. */
internal suspend fun <T> cancellableIo(cancel: () -> Unit, block: () -> T): T = coroutineScope {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        launch(Dispatchers.IO) {
            try {
                continuation.resume(block())
            } catch (error: Exception) {
                continuation.resumeWithException(error)
            }
        }
    }
}
