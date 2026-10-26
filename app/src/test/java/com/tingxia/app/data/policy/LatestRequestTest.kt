package com.tingxia.app.data.policy

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestRequestTest {
    @Test fun lateUncancellableCompletionCannotOverwriteNewChapter() = runBlocking {
        val requests = LatestRequest<String>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldTicket = requests.begin("A")
        var displayed = ""
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { releaseOld.await() }
            if (requests.accepts(oldTicket)) displayed = "A"
        }
        old.cancel()
        val currentTicket = requests.begin("C")
        if (requests.accepts(currentTicket)) displayed = "C"
        releaseOld.complete(Unit)
        old.join()
        assertEquals("C", displayed)
    }
    @Test fun repeatingSameQueryAndDismissStillInvalidatesOldResults() {
        val requests = LatestRequest<String>()
        val first = requests.begin("same")
        val second = requests.begin("same")
        assertEquals(false, requests.accepts(first))
        requests.clear()
        assertEquals(false, requests.accepts(second))
    }
}
