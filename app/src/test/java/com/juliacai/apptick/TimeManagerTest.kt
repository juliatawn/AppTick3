package com.juliacai.apptick

import com.juliacai.apptick.groups.AppLimitGroup
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import java.util.Calendar

class TimeManagerTest {

    @Test
    fun `test get time remaining`() {
        val group = AppLimitGroup(timeHrLimit = 1, timeMinLimit = 30)
        val timeManager = TimeManager(group)
        val timeRemaining = timeManager.getTimeRemaining()
        assertThat(timeRemaining).isEqualTo(5400000)
    }

    @Test
    fun `test get next reset time with periodic reset`() {
        // Periodic mode: resetMinutes > 0 → next reset = now + resetMinutes
        val group = AppLimitGroup(resetMinutes = 24 * 60)
        val timeManager = TimeManager(group)
        val nextResetTime = timeManager.getNextResetTime()

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, 24)
        val expectedNextResetTime = calendar.timeInMillis

        assertThat(nextResetTime).isAtLeast(expectedNextResetTime - 1000)
        assertThat(nextResetTime).isAtMost(expectedNextResetTime + 1000)
    }

    @Test
    fun `test get time remaining when limits are zero`() {
        val group = AppLimitGroup(timeHrLimit = 0, timeMinLimit = 0)
        val timeManager = TimeManager(group)

        assertThat(timeManager.getTimeRemaining()).isEqualTo(0L)
    }

    @Test
    fun `test get next reset time when resetMinutes is zero defaults to midnight`() {
        // Daily mode: resetMinutes == 0 → next reset = midnight tomorrow
        val group = AppLimitGroup(resetMinutes = 0)
        val timeManager = TimeManager(group)
        val nextResetTime = timeManager.getNextResetTime()

        val expectedMidnight = TimeManager.nextMidnight()
        assertThat(nextResetTime).isEqualTo(expectedMidnight)
    }

    // ── nextMidnight() tests ─────────────────────────────────────────────
    @Test
    fun `nextMidnight returns epoch millis at midnight tomorrow`() {
        val now = System.currentTimeMillis()
        val midnight = TimeManager.nextMidnight(now)

        val cal = Calendar.getInstance().apply { timeInMillis = midnight }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
        assertThat(cal.get(Calendar.SECOND)).isEqualTo(0)
        assertThat(cal.get(Calendar.MILLISECOND)).isEqualTo(0)
        // Must be strictly after now
        assertThat(midnight).isGreaterThan(now)
    }

    @Test
    fun `nextMidnight called just before midnight still returns NEXT midnight`() {
        // Simulate 11:59 PM today
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 0)
        }
        val midnight = TimeManager.nextMidnight(cal.timeInMillis)

        // Should be tomorrow 12:00 AM
        val resultCal = Calendar.getInstance().apply { timeInMillis = midnight }
        val inputDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val resultDayOfYear = resultCal.get(Calendar.DAY_OF_YEAR)

        assertThat(resultDayOfYear).isEqualTo((inputDayOfYear % 366) + 1)
        assertThat(resultCal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
    }

    @Test
    fun `nextMidnight called at midnight returns NEXT day midnight`() {
        // Simulate exactly 12:00 AM today
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnight = TimeManager.nextMidnight(cal.timeInMillis)

        // Should be tomorrow 12:00 AM (24 hours later)
        assertThat(midnight).isEqualTo(cal.timeInMillis + 24 * 60 * 60 * 1000L)
    }
}
