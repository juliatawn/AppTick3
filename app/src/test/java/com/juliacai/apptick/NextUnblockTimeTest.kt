package com.juliacai.apptick

import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class NextUnblockTimeTest {

    // ── Helper: create a Calendar at a specific time today ──────────────────
    private fun calendarAt(hour: Int, minute: Int, dayOffset: Int = 0): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (dayOffset != 0) add(Calendar.DAY_OF_YEAR, dayOffset)
        }
    }

    private fun todayIso(): Int {
        val cal = Calendar.getInstance()
        return ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    }

    // ── Blocked outside time range: shows next range start ──────────────────

    @Test
    fun `outside range - overnight range 2230-0600 blocked at 2300 shows 0600 next day`() {
        // User's original bug: blocked during 10:30pm-6am, should show 6am not midnight
        val now = calendarAt(23, 0).timeInMillis
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 22, startMinute = 30, endHour = 6, endMinute = 0))
        )
        // At 11pm we're inside the 22:30-06:00 range, so this would NOT be blockedForOutsideRange.
        // The scenario where we're blocked outside range is when we're OUTSIDE the range.
        // With range 22:30-06:00, outside would be 06:01-22:29.
        // Let's test being at 10am (outside the range).
        val nowOutside = calendarAt(10, 0).timeInMillis
        val result = TimeManager.computeNextUnblockTime(group, nowOutside, blockedForOutsideRange = true)

        // Next range start is 22:30 today (since 10am < 22:30)
        val expected = calendarAt(22, 30).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `outside range - range 0600-1800 blocked at 2000 shows 0600 tomorrow`() {
        // Range is 6am-6pm. At 8pm we're outside. Next entry = 6am tomorrow.
        val now = calendarAt(20, 0).timeInMillis
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = true)

        val expected = calendarAt(6, 0, dayOffset = 1).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `outside range - multiple ranges picks earliest start`() {
        // Ranges: 8am-10am and 14pm-16pm. At 12pm (outside both).
        val now = calendarAt(12, 0).timeInMillis
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(
                TimeRange(startHour = 8, startMinute = 0, endHour = 10, endMinute = 0),
                TimeRange(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0)
            )
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = true)

        // 8am already passed, 14pm is next
        val expected = calendarAt(14, 0).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `outside range - respects active days skips to next active day`() {
        val today = todayIso()
        // Pick 2 days after today for active day
        val activeDay = ((today + 1 - 1) % 7) + 1  // tomorrow in ISO

        val now = calendarAt(20, 0).timeInMillis
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = true,
            weekDays = listOf(activeDay),
            timeRanges = listOf(TimeRange(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = true)

        // 9am already passed today, and today might not be active anyway.
        // Should be 9am on the next active day.
        val expectedCal = calendarAt(9, 0, dayOffset = 1)
        // Verify it's on the active day
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        val resultDay = ((resultCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        assertThat(resultDay).isEqualTo(activeDay)
        assertThat(resultCal.get(Calendar.HOUR_OF_DAY)).isEqualTo(9)
        assertThat(resultCal.get(Calendar.MINUTE)).isEqualTo(0)
    }

    // ── Zero limit with time range: shows range end ─────────────────────────

    @Test
    fun `zero limit in range - overnight 2230-0600 at 2300 shows 0600 tomorrow`() {
        // Blocked 10:30pm-6am with zero limit. At 11pm, shows 6am.
        val now = calendarAt(23, 0).timeInMillis
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 0,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 22, startMinute = 30, endHour = 6, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)

        val expected = calendarAt(6, 0, dayOffset = 1).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `zero limit in range - daytime 0600-1800 at 1000 shows 1800 today`() {
        val now = calendarAt(10, 0).timeInMillis
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 0,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)

        val expected = calendarAt(18, 0).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `zero limit no time range - returns not scheduled`() {
        val now = calendarAt(10, 0).timeInMillis
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 0,
            useTimeRange = false
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `zero limit with blockOutsideTimeRange true - returns not scheduled`() {
        // Always blocked: zero limit inside range + blocked outside range
        val now = calendarAt(10, 0).timeInMillis
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 0,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(0L)
    }

    // ── Limit reached: shows earliest of reset or range end ─────────────────

    @Test
    fun `limit reached - daily reset no time range shows next reset time`() {
        val now = calendarAt(14, 0).timeInMillis
        val nextReset = TimeManager.nextMidnight(now)
        val group = AppLimitGroup(
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0,
            timeRemaining = 0,
            nextResetTime = nextReset,
            useTimeRange = false
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(nextReset)
    }

    @Test
    fun `limit reached - periodic reset shows next reset time`() {
        val now = calendarAt(14, 0).timeInMillis
        val nextReset = now + 90 * 60 * 1000L  // 1hr 30min from now
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 5,
            resetMinutes = 90,
            timeRemaining = 0,
            nextResetTime = nextReset,
            useTimeRange = false
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(nextReset)
    }

    @Test
    fun `limit reached - range end sooner than reset shows range end`() {
        // Time range 6am-6pm, limit used up at 5:30pm, daily reset at midnight.
        // Range ends at 6pm (30 min), reset at midnight (6.5 hr). Show 6pm.
        val now = calendarAt(17, 30).timeInMillis
        val nextReset = TimeManager.nextMidnight(now)
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 5,
            resetMinutes = 0,
            timeRemaining = 0,
            nextResetTime = nextReset,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)

        val expected = calendarAt(18, 0).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `limit reached - reset sooner than range end shows reset`() {
        // Time range 6am-6pm, limit used up at 10am, periodic reset every 30 min.
        // Reset at 10:30am (30 min), range end at 6pm (8hr). Show 10:30am.
        val now = calendarAt(10, 0).timeInMillis
        val nextReset = now + 30 * 60 * 1000L
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 5,
            resetMinutes = 30,
            timeRemaining = 0,
            nextResetTime = nextReset,
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(nextReset)
    }

    @Test
    fun `limit reached with blockOutsideTimeRange true ignores range end`() {
        // blockOutsideTimeRange=true means leaving the range doesn't help.
        // Time range 6am-6pm, limit used up at 5:30pm, reset at midnight.
        val now = calendarAt(17, 30).timeInMillis
        val nextReset = TimeManager.nextMidnight(now)
        val group = AppLimitGroup(
            timeHrLimit = 0,
            timeMinLimit = 5,
            resetMinutes = 0,
            timeRemaining = 0,
            nextResetTime = nextReset,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 6, startMinute = 0, endHour = 18, endMinute = 0))
        )
        val result = TimeManager.computeNextUnblockTime(group, now, blockedForOutsideRange = false)
        assertThat(result).isEqualTo(nextReset)
    }

    // ── Helper method tests ─────────────────────────────────────────────────

    @Test
    fun `nextOccurrenceOfTime - time later today returns today`() {
        val now = calendarAt(8, 0).timeInMillis
        val result = TimeManager.nextOccurrenceOfTime(14, 0, now, emptyList())

        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertThat(resultCal.get(Calendar.HOUR_OF_DAY)).isEqualTo(14)
        assertThat(resultCal.get(Calendar.MINUTE)).isEqualTo(0)
        // Same day
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        assertThat(resultCal.get(Calendar.DAY_OF_YEAR)).isEqualTo(nowCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun `nextOccurrenceOfTime - time already passed returns tomorrow`() {
        val now = calendarAt(16, 0).timeInMillis
        val result = TimeManager.nextOccurrenceOfTime(8, 0, now, emptyList())

        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertThat(resultCal.get(Calendar.HOUR_OF_DAY)).isEqualTo(8)
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        assertThat(resultCal.get(Calendar.DAY_OF_YEAR))
            .isEqualTo((nowCal.get(Calendar.DAY_OF_YEAR) % 366) + 1)
    }

    @Test
    fun `currentTimeRangeEnd - overnight range before midnight returns next day end`() {
        val now = calendarAt(23, 30).timeInMillis
        val ranges = listOf(TimeRange(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0))
        val result = TimeManager.currentTimeRangeEnd(ranges, now)

        val expected = calendarAt(6, 0, dayOffset = 1).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `currentTimeRangeEnd - overnight range after midnight returns same day end`() {
        val now = calendarAt(3, 0).timeInMillis
        val ranges = listOf(TimeRange(startHour = 22, startMinute = 0, endHour = 6, endMinute = 0))
        val result = TimeManager.currentTimeRangeEnd(ranges, now)

        val expected = calendarAt(6, 0).timeInMillis
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `currentTimeRangeEnd - not in any range returns zero`() {
        val now = calendarAt(12, 0).timeInMillis
        val ranges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 10, endMinute = 0))
        val result = TimeManager.currentTimeRangeEnd(ranges, now)
        assertThat(result).isEqualTo(0L)
    }
}
