package com.tingxia.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkRequestCacheTest {

    @Test
    fun coalescesConcurrentInFlightRequestsAndCachesSuccess() {
        var now = 1_000L
        val cache = ArtworkRequestCache<String>(
            maxEntries = 4,
            maxFailures = 4,
            failureTtlMs = 60_000L,
            nowMs = { now },
        )

        val first = cache.acquireOrStart("https://cdn.example/cover.jpg", "meta:cover|Book")
        assertTrue(first is ArtworkRequestCache.Lookup.Started)
        val started = first as ArtworkRequestCache.Lookup.Started

        // Second request while first is still running shares the exact same Future
        val second = cache.acquireOrStart("https://cdn.example/cover.jpg", "meta:cover|Book")
        assertTrue(second is ArtworkRequestCache.Lookup.InFlight)
        assertSame(started.future, (second as ArtworkRequestCache.Lookup.InFlight).future)

        cache.completeSuccess("https://cdn.example/cover.jpg", "meta:cover|Book", "BITMAP_A")
        started.future.set("BITMAP_A")

        val third = cache.acquireOrStart("https://cdn.example/cover.jpg", "meta:cover|Book")
        assertEquals(ArtworkRequestCache.Lookup.Hit("BITMAP_A"), third)
    }

    @Test
    fun cachesFailuresForShortTtlThenRetriesAfterExpiry() {
        var now = 10_000L
        val cache = ArtworkRequestCache<String>(
            maxEntries = 4,
            maxFailures = 4,
            failureTtlMs = 60_000L,
            nowMs = { now },
        )

        val initial = cache.acquireOrStart("https://offline.example/cover.jpg", "meta:offline")
        assertTrue(initial is ArtworkRequestCache.Lookup.Started)
        cache.completeFailure("https://offline.example/cover.jpg", "meta:offline")

        // Subsequent chapter switch 5 seconds later hits negative cache synchronously
        now += 5_000L
        val duringTtl = cache.acquireOrStart("https://offline.example/cover.jpg", "meta:offline")
        assertEquals(ArtworkRequestCache.Lookup.RecentFailure, duringTtl)

        // Once TTL expires (60s+), a fresh load is started so recovered network gets real art
        now += 56_000L
        val afterExpiry = cache.acquireOrStart("https://offline.example/cover.jpg", "meta:offline")
        assertTrue(afterExpiry is ArtworkRequestCache.Lookup.Started)
    }
}
