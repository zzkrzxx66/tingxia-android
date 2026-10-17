package com.tingxia.app.data.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteDurationPolicyTest {
    @Test
    fun firstDownloadedChapterDoesNotShrinkAnHourLongBook() {
        assertEquals(3_600_000L, remoteTotalDuration(3_600_000, 60_000, true))
    }

    @Test
    fun completeCatalogueUsesMeasuredTotalEvenWhenUpstreamEstimateWasHigher() {
        assertEquals(3_500_000L, remoteTotalDuration(3_600_000, 3_500_000, false))
    }

    @Test
    fun booksWithoutUpstreamDurationAccumulateKnownChapters() {
        assertEquals(60_000L, remoteTotalDuration(0, 60_000, true))
        assertEquals(120_000L, remoteTotalDuration(60_000, 120_000, true))
    }

    @Test
    fun knownChaptersCanOutgrowStaleUpstreamTotal() {
        assertEquals(3_700_000L, remoteTotalDuration(3_600_000, 3_700_000, true))
    }
}
