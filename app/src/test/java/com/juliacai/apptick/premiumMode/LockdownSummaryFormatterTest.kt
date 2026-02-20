package com.juliacai.apptick.premiumMode

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.LockdownType
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class LockdownSummaryFormatterTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val locale = Locale.US

    @Test
    fun oneTime_usesConfiguredFutureDate() {
        val now = utcMillis(2026, Calendar.FEBRUARY, 18, 8, 0)
        val end = utcMillis(2026, Calendar.FEBRUARY, 20, 14, 30)

        val result = LockdownSummaryFormatter.formatTarget(
            lockdownType = LockdownType.ONE_TIME,
            lockdownEndTimeMillis = end,
            recurringDays = emptyList(),
            nowMillis = now,
            locale = locale,
            timeZone = tz
        )

        assertThat(result).isEqualTo("Fri, Feb 20 2:30 PM")
    }

    @Test
    fun oneTime_whenMissingOrPast_usesNextDay() {
        val now = utcMillis(2026, Calendar.FEBRUARY, 18, 8, 0)

        val result = LockdownSummaryFormatter.formatTarget(
            lockdownType = LockdownType.ONE_TIME,
            lockdownEndTimeMillis = 0L,
            recurringDays = emptyList(),
            nowMillis = now,
            locale = locale,
            timeZone = tz
        )

        assertThat(result).isEqualTo("Thu, Feb 19 8:00 AM")
    }

    @Test
    fun recurring_formatsSelectedDaysSortedDistinct() {
        val result = LockdownSummaryFormatter.formatTarget(
            lockdownType = LockdownType.RECURRING,
            lockdownEndTimeMillis = 0L,
            recurringDays = listOf(4, 1, 4, 7),
            locale = locale,
            timeZone = tz
        )

        assertThat(result).isEqualTo("the next Monday, Thursday, Sunday")
    }

    @Test
    fun recurring_whenNoValidDays_usesFallbackText() {
        val result = LockdownSummaryFormatter.formatTarget(
            lockdownType = LockdownType.RECURRING,
            lockdownEndTimeMillis = 0L,
            recurringDays = listOf(0, 9),
            locale = locale,
            timeZone = tz
        )

        assertThat(result).isEqualTo("the next selected day")
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(tz, locale).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
