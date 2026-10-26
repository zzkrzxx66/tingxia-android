package com.tingxia.app.player

import org.junit.Assert.*
import org.junit.Test

class ListeningTrackerTest {
    @Test fun shortPlaybackAndPauseGapsAreCountedExactly() {
        val tracker = ListeningTracker()
        assertNull(tracker.update(1, false, 0, 1000))
        assertNull(tracker.update(1, true, 100, 1100))
        assertEquals(ListeningTracker.Segment(1, 150, 1250), tracker.update(1, false, 250, 1250))
        assertNull(tracker.update(1, true, 10000, 11000))
        assertEquals(200L, tracker.update(1, false, 10200, 11200)?.elapsedMs)
    }
    @Test fun switchChargesPreviousBookAndBufferingDoesNotCount() {
        val tracker = ListeningTracker()
        tracker.update(1, true, 100, 1000)
        assertEquals(1L, tracker.update(2, true, 600, 1500)?.bookId)
        assertEquals(ListeningTracker.Segment(2, 300, 1800), tracker.update(2, false, 900, 1800))
        assertNull(tracker.update(2, true, 2000, 2900))
        assertEquals(100L, tracker.update(null, false, 2100, 3000)?.elapsedMs)
        assertNull(tracker.update(null, false, 2200, 3100))
    }
    @Test fun wallClockChangeDoesNotChangeListeningDuration() {
        val tracker = ListeningTracker()
        tracker.update(1, true, 100, 100000)
        assertEquals(1000L, tracker.update(1, false, 1100, 500)?.elapsedMs)
    }
}
