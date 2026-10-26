package com.tingxia.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StatsDayTest {
    @Test fun localDaysFollowBothDaylightSavingTransitions() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            fun date(month: Int, day: Int) = Calendar.getInstance().apply { clear(); set(2026, month - 1, day) }.timeInMillis
            val spring = date(3, 8)
            val autumn = date(11, 1)
            assertEquals(23 * 3600000L, StatsRepository.shiftDay(spring, 1) - spring)
            assertEquals(25 * 3600000L, StatsRepository.shiftDay(autumn, 1) - autumn)
            assertEquals(date(3, 7), StatsRepository.shiftDay(date(3, 13), -6))
            assertEquals(spring, StatsRepository.dayStartMs(spring + 12 * 3600000L))
        } finally { TimeZone.setDefault(original) }
    }
}
