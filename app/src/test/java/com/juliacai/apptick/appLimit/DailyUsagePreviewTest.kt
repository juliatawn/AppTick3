package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsagePreviewTest {

    // ── totalTimeRangeMinutes ────────────────────────────────────────────

    @Test
    fun `totalTimeRangeMinutes normal range 8am to 5pm is 540 minutes`() {
        val ranges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0))
        assertEquals(540, DailyUsagePreview.totalTimeRangeMinutes(ranges))
    }

    @Test
    fun `totalTimeRangeMinutes overnight range 10pm to 6am is 480 minutes`() {
        val ranges = listOf(TimeRange(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0))
        assertEquals(480, DailyUsagePreview.totalTimeRangeMinutes(ranges))
    }

    @Test
    fun `totalTimeRangeMinutes full day 0-0 to 23-59 is 1439 minutes`() {
        val ranges = listOf(TimeRange(startHour = 0, startMinute = 0, endHour = 23, endMinute = 59))
        assertEquals(1439, DailyUsagePreview.totalTimeRangeMinutes(ranges))
    }

    @Test
    fun `totalTimeRangeMinutes multiple ranges sum correctly`() {
        val ranges = listOf(
            TimeRange(startHour = 8, startMinute = 0, endHour = 12, endMinute = 0),  // 240 min
            TimeRange(startHour = 14, startMinute = 0, endHour = 18, endMinute = 0)  // 240 min
        )
        assertEquals(480, DailyUsagePreview.totalTimeRangeMinutes(ranges))
    }

    @Test
    fun `totalTimeRangeMinutes empty list returns 0`() {
        assertEquals(0, DailyUsagePreview.totalTimeRangeMinutes(emptyList()))
    }

    @Test
    fun `totalTimeRangeMinutes same start and end is 0`() {
        val ranges = listOf(TimeRange(startHour = 10, startMinute = 0, endHour = 10, endMinute = 0))
        assertEquals(0, DailyUsagePreview.totalTimeRangeMinutes(ranges))
    }

    // ── User's example: 8am-5pm, 1hr reset, 1min limit ─────────────────

    @Test
    fun `user example 8am-5pm hourly reset 1min limit gives 9min per day`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 1,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
            cumulativeTime = false
        )
        // 540 min window / 60 min interval = 9 full periods, each with 1 min limit
        assertEquals(9, result.totalMinutes)
        assertEquals(9, result.periodCount)
        assertEquals(1, result.minutesPerPeriod)
        assertEquals(540, result.activeWindowMinutes)
    }

    // ── No time range (full day) ────────────────────────────────────────

    @Test
    fun `full day hourly reset 10min limit gives 240min per day`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 10,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = false,
            blockOutsideTimeRange = false,
            timeRanges = emptyList(),
            cumulativeTime = false
        )
        // 1440 min / 60 = 24 periods × 10 min = 240 min
        assertEquals(240, result.totalMinutes)
        assertEquals(24, result.periodCount)
    }

    // ── Limit exceeds interval: capped at active window ─────────────────

    @Test
    fun `limit per period exceeding interval caps at active window`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 2,
            timeLimitMinutes = 0,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 12, endMinute = 0)),
            cumulativeTime = false
        )
        // 240 min window / 60 = 4 periods × 120 min = 480, capped to 240
        assertEquals(240, result.totalMinutes)
        assertEquals(240, result.activeWindowMinutes)
    }

    // ── No periodic reset ───────────────────────────────────────────────

    @Test
    fun `no periodic reset returns single period limit`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 1,
            timeLimitMinutes = 30,
            useTimeLimit = true,
            resetIntervalMinutes = 0,
            useTimeRange = false,
            blockOutsideTimeRange = false,
            timeRanges = emptyList(),
            cumulativeTime = false
        )
        assertEquals(90, result.totalMinutes)
        assertEquals(1, result.periodCount)
        assertEquals(90, result.minutesPerPeriod)
    }

    // ── Time limit disabled ─────────────────────────────────────────────

    @Test
    fun `time limit disabled returns 0 minutes`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 1,
            timeLimitMinutes = 0,
            useTimeLimit = false,
            resetIntervalMinutes = 60,
            useTimeRange = false,
            blockOutsideTimeRange = false,
            timeRanges = emptyList(),
            cumulativeTime = false
        )
        assertEquals(0, result.totalMinutes)
    }

    // ── Partial period at end of window ─────────────────────────────────

    @Test
    fun `partial period at end of window gives partial limit`() {
        // 5hr window = 300min, 2hr (120min) interval → 2 full + 60min remainder
        // 2 × 30min + min(30, 60) = 60 + 30 = 90min
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 30,
            useTimeLimit = true,
            resetIntervalMinutes = 120,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 13, endMinute = 0)),
            cumulativeTime = false
        )
        assertEquals(90, result.totalMinutes)
        assertEquals(3, result.periodCount) // 2 full + 1 partial
    }

    @Test
    fun `partial period where limit exceeds remainder`() {
        // 90min window, 60min interval → 1 full + 30min remainder
        // limit is 45min, so: 1 × 45 + min(45, 30) = 45 + 30 = 75min
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 45,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)),
            cumulativeTime = false
        )
        assertEquals(75, result.totalMinutes)
        assertEquals(2, result.periodCount)
    }

    // ── No limits outside range ─────────────────────────────────────────

    @Test
    fun `no limits outside range still calculates in-range usage`() {
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 5,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)),
            cumulativeTime = false
        )
        // 480 min / 60 = 8 periods × 5 min = 40 min during the range
        assertEquals(40, result.totalMinutes)
        assertEquals(8, result.periodCount)
    }

    // ── formatPreview ───────────────────────────────────────────────────

    @Test
    fun `formatPreview with hours and minutes`() {
        val result = DailyUsagePreview.PreviewResult(
            totalMinutes = 90,
            periodCount = 3,
            minutesPerPeriod = 30,
            activeWindowMinutes = 360
        )
        assertEquals("1hr 30min/day", DailyUsagePreview.formatPreview(result))
    }

    @Test
    fun `formatPreview with only minutes`() {
        val result = DailyUsagePreview.PreviewResult(
            totalMinutes = 45,
            periodCount = 3,
            minutesPerPeriod = 15,
            activeWindowMinutes = 360
        )
        assertEquals("45min/day", DailyUsagePreview.formatPreview(result))
    }

    @Test
    fun `formatPreview with only hours`() {
        val result = DailyUsagePreview.PreviewResult(
            totalMinutes = 120,
            periodCount = 4,
            minutesPerPeriod = 30,
            activeWindowMinutes = 480
        )
        assertEquals("2hr/day", DailyUsagePreview.formatPreview(result))
    }

    @Test
    fun `formatPreview zero minutes`() {
        val result = DailyUsagePreview.PreviewResult(
            totalMinutes = 0,
            periodCount = 0,
            minutesPerPeriod = 0,
            activeWindowMinutes = 0
        )
        assertEquals("0 min/day", DailyUsagePreview.formatPreview(result))
    }

    // ── Edge case: interval larger than window ──────────────────────────

    @Test
    fun `interval larger than window gives 0 full periods and 1 partial`() {
        // 60min window, 120min interval → 0 full periods + 60min remainder
        // limit is 10min, so: min(10, 60) = 10min
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 10,
            useTimeLimit = true,
            resetIntervalMinutes = 120,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 9, endMinute = 0)),
            cumulativeTime = false
        )
        assertEquals(10, result.totalMinutes)
        assertEquals(1, result.periodCount) // 0 full + 1 partial
    }

    // ── Overnight range with periodic reset ─────────────────────────────

    @Test
    fun `overnight range with periodic reset calculates correctly`() {
        // 10pm to 6am = 480min, hourly reset, 5min limit
        val result = DailyUsagePreview.calculate(
            timeLimitHours = 0,
            timeLimitMinutes = 5,
            useTimeLimit = true,
            resetIntervalMinutes = 60,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0)),
            cumulativeTime = false
        )
        // 480 / 60 = 8 periods × 5min = 40min
        assertEquals(40, result.totalMinutes)
        assertEquals(8, result.periodCount)
    }
}
