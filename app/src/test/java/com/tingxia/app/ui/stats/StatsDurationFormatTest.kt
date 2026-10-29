package com.tingxia.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsDurationFormatTest {

    @Test
    fun formatsZeroAndSubMinuteInMinutes() {
        assertEquals(CompactStatDuration("0", StatDurationUnit.MINUTES), formatStatsDurationParts(0L))
        assertEquals(CompactStatDuration("0", StatDurationUnit.MINUTES), formatStatsDurationParts(-500L))
        assertEquals(CompactStatDuration("<1", StatDurationUnit.MINUTES), formatStatsDurationParts(1_000L))
        assertEquals(CompactStatDuration("<1", StatDurationUnit.MINUTES), formatStatsDurationParts(59_999L))
    }

    @Test
    fun formatsMinutesUnderOneHour() {
        assertEquals(CompactStatDuration("1", StatDurationUnit.MINUTES), formatStatsDurationParts(60_000L))
        assertEquals(CompactStatDuration("42", StatDurationUnit.MINUTES), formatStatsDurationParts(42 * 60_000L))
        assertEquals(CompactStatDuration("59", StatDurationUnit.MINUTES), formatStatsDurationParts(59 * 60_000L + 59_000L))
    }

    @Test
    fun formatsHoursWithOptionalSingleDecimalUnderHundredHours() {
        assertEquals(CompactStatDuration("1", StatDurationUnit.HOURS), formatStatsDurationParts(3_600_000L))
        assertEquals(CompactStatDuration("12.5", StatDurationUnit.HOURS), formatStatsDurationParts(12 * 3_600_000L + 30 * 60_000L))
        // 99h 58m stays 99.9 rather than rounding to 5-character "100.0"
        assertEquals(CompactStatDuration("99.9", StatDurationUnit.HOURS), formatStatsDurationParts(99 * 3_600_000L + 58 * 60_000L))
    }

    @Test
    fun formatsHundredPlusHoursAsCompactWholeHours() {
        assertEquals(CompactStatDuration("100", StatDurationUnit.HOURS), formatStatsDurationParts(100 * 3_600_000L))
        assertEquals(CompactStatDuration("128", StatDurationUnit.HOURS), formatStatsDurationParts(128 * 3_600_000L + 45 * 60_000L))
        assertEquals(CompactStatDuration("1250", StatDurationUnit.HOURS), formatStatsDurationParts(1250 * 3_600_000L))
    }

    @Test
    fun formatsCompactChartBarLabels() {
        assertEquals("", formatCompactChartDuration(0L))
        assertEquals("<1m", formatCompactChartDuration(30_000L))
        assertEquals("45m", formatCompactChartDuration(45 * 60_000L))
        assertEquals("2h", formatCompactChartDuration(2 * 3_600_000L))
        assertEquals("1.5h", formatCompactChartDuration(90 * 60_000L))
        assertEquals("120h", formatCompactChartDuration(120 * 3_600_000L))
    }

    @Test
    fun resolvesOverviewLayoutAcrossWidthsAndFontScales() {
        // 360dp phone (288dp inner width) at normal 1.0x font scale -> 3 columns
        assertEquals(StatsOverviewLayout.THREE_COLUMNS, resolveStatsOverviewLayout(288f, 1.0f))
        // 320dp phone (248dp inner width) at normal 1.0x font scale -> hero + 2 columns
        assertEquals(StatsOverviewLayout.HERO_PLUS_TWO, resolveStatsOverviewLayout(248f, 1.0f))
        // 320dp phone at 200% font scale (248 / 2.0 = 124 < 190) -> all 3 stacked vertically
        assertEquals(StatsOverviewLayout.SINGLE_COLUMN, resolveStatsOverviewLayout(248f, 2.0f))
        // 360dp phone at 175% font scale (288 / 1.75 = 164.5 < 190) -> all 3 stacked vertically
        assertEquals(StatsOverviewLayout.SINGLE_COLUMN, resolveStatsOverviewLayout(288f, 1.75f))
    }
}
