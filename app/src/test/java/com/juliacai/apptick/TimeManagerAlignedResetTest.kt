package com.juliacai.apptick

import com.juliacai.apptick.groups.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimeManagerAlignedResetTest {

    private fun calendarAt(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun todayAt(hour: Int, minute: Int): Long = calendarAt(hour, minute)

    // ── No time range: aligned to midnight ──────────────────────────────

    @Test
    fun `no time range aligns to midnight grid - next reset at 1am when now is 12-25am`() {
        val now = todayAt(0, 25) // 12:25 AM
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = false,
            timeRanges = emptyList(),
            nowMillis = now
        )
        val expected = todayAt(1, 0) // 1:00 AM
        assertEquals(expected, result)
    }

    @Test
    fun `no time range aligns to midnight grid - next reset at 3am when now is 2-30am with 60min interval`() {
        val now = todayAt(2, 30) // 2:30 AM
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = false,
            timeRanges = emptyList(),
            nowMillis = now
        )
        val expected = todayAt(3, 0) // 3:00 AM
        assertEquals(expected, result)
    }

    @Test
    fun `no time range 30min interval at 1-15am gives 1-30am`() {
        val now = todayAt(1, 15)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 30,
            useTimeRange = false,
            timeRanges = emptyList(),
            nowMillis = now
        )
        val expected = todayAt(1, 30)
        assertEquals(expected, result)
    }

    // ── With time range: aligned to range start ─────────────────────────

    @Test
    fun `time range 8am-5pm hourly reset at 8-25am gives 9am`() {
        val now = todayAt(8, 25)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
            nowMillis = now
        )
        val expected = todayAt(9, 0) // aligned to 8:00 grid → 9:00, 10:00, etc.
        assertEquals(expected, result)
    }

    @Test
    fun `time range 8am-5pm hourly reset at 10-00am gives 11am`() {
        val now = todayAt(10, 0)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
            nowMillis = now
        )
        val expected = todayAt(11, 0)
        assertEquals(expected, result)
    }

    @Test
    fun `time range 9-30am start with 60min interval at 10-15am gives 10-30am`() {
        val now = todayAt(10, 15)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 9, startMinute = 30, endHour = 17, endMinute = 0)),
            nowMillis = now
        )
        // Grid: 9:30, 10:30, 11:30, ...
        val expected = todayAt(10, 30)
        assertEquals(expected, result)
    }

    @Test
    fun `time range 8am start with 2hr interval at 9-00am gives 10am`() {
        val now = todayAt(9, 0)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 120,
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
            nowMillis = now
        )
        // Grid: 8:00, 10:00, 12:00, ...
        val expected = todayAt(10, 0)
        assertEquals(expected, result)
    }

    // ── Before time range start ─────────────────────────────────────────

    @Test
    fun `before time range start gives range start plus interval`() {
        val now = todayAt(6, 0) // before 8am range
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
            nowMillis = now
        )
        // Anchor 8:00 is in the future → first reset at 8:00 + 60min = 9:00
        val expected = todayAt(9, 0)
        assertEquals(expected, result)
    }

    // ── Multiple time ranges: uses earliest start ───────────────────────

    @Test
    fun `multiple ranges uses earliest start as anchor`() {
        val now = todayAt(10, 15)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = true,
            timeRanges = listOf(
                TimeRange(startHour = 14, startMinute = 0, endHour = 18, endMinute = 0),
                TimeRange(startHour = 8, startMinute = 0, endHour = 12, endMinute = 0)
            ),
            nowMillis = now
        )
        // Earliest start is 8:00 → grid: 8:00, 9:00, 10:00, 11:00, ...
        val expected = todayAt(11, 0)
        assertEquals(expected, result)
    }

    // ── Edge: exactly on a grid point ───────────────────────────────────

    @Test
    fun `exactly on grid point advances to next`() {
        val now = todayAt(9, 0) // exactly on grid: midnight + 9 * 60min
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 60,
            useTimeRange = false,
            timeRanges = emptyList(),
            nowMillis = now
        )
        val expected = todayAt(10, 0)
        assertEquals(expected, result)
    }

    // ── Zero interval falls back to nextMidnight ────────────────────────

    @Test
    fun `zero interval returns next midnight`() {
        val now = todayAt(15, 0)
        val result = TimeManager.nextAlignedResetTime(
            resetIntervalMinutes = 0,
            useTimeRange = false,
            timeRanges = emptyList(),
            nowMillis = now
        )
        val expected = TimeManager.nextMidnight(now)
        assertEquals(expected, result)
    }

    // ── Result is always in the future ──────────────────────────────────

    @Test
    fun `result is always after now for various times of day`() {
        val intervals = listOf(15, 30, 60, 120, 180)
        for (hour in 0..23) {
            for (interval in intervals) {
                val now = todayAt(hour, 17)
                val result = TimeManager.nextAlignedResetTime(
                    resetIntervalMinutes = interval,
                    useTimeRange = false,
                    timeRanges = emptyList(),
                    nowMillis = now
                )
                assertTrue(
                    "Reset at ${hour}:17 with ${interval}min interval should be in the future",
                    result > now
                )
            }
        }
    }
}
