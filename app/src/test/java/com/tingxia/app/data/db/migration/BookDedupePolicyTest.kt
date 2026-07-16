package com.tingxia.app.data.db.migration

import org.junit.Assert.assertEquals
import org.junit.Test

class BookDedupePolicyTest {
    @Test
    fun prefersMostRecentlyPlayed() {
        val keep = BookDedupePolicy.selectKeeper(
            listOf(
                BookDedupePolicy.Candidate(1, lastPlayedAt = 10, listenedDurationMs = 9999, currentPositionMs = 0),
                BookDedupePolicy.Candidate(2, lastPlayedAt = 100, listenedDurationMs = 1, currentPositionMs = 0),
            ),
        )
        assertEquals(2L, keep.id)
    }

    @Test
    fun thenPrefersFurtherListened() {
        val keep = BookDedupePolicy.selectKeeper(
            listOf(
                BookDedupePolicy.Candidate(1, lastPlayedAt = 50, listenedDurationMs = 100, currentPositionMs = 0),
                BookDedupePolicy.Candidate(2, lastPlayedAt = 50, listenedDurationMs = 500, currentPositionMs = 0),
            ),
        )
        assertEquals(2L, keep.id)
    }

    @Test
    fun thenPrefersHigherPositionThenHigherId() {
        val keep = BookDedupePolicy.selectKeeper(
            listOf(
                BookDedupePolicy.Candidate(1, 0, 0, 10),
                BookDedupePolicy.Candidate(3, 0, 0, 10),
                BookDedupePolicy.Candidate(2, 0, 0, 20),
            ),
        )
        assertEquals(2L, keep.id)
    }
}
