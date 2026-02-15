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
    fun `test get next reset time`() {
        val group = AppLimitGroup(resetHours = 24)
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
    fun `test get next reset time when resetHours is zero`() {
        val before = System.currentTimeMillis()
        val group = AppLimitGroup(resetHours = 0)
        val timeManager = TimeManager(group)
        val nextResetTime = timeManager.getNextResetTime()
        val after = System.currentTimeMillis()

        assertThat(nextResetTime).isAtLeast(before - 1000L)
        assertThat(nextResetTime).isAtMost(after + 1000L)
    }
}
