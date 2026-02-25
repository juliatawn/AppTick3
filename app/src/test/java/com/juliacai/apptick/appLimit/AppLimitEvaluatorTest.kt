package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AppLimitEvaluatorTest {

    @Test
    fun `shouldCheckLimit returns true when no specific days set`() {
        val group = AppLimitGroup(weekDays = emptyList())
        assertTrue(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `shouldCheckLimit returns true when current day is in list`() {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert to Mon=1 ... Sun=7 format used by AppLimitEvaluator
        val dayMondayOne = ((currentDayOfWeek + 5) % 7) + 1
        
        val group = AppLimitGroup(weekDays = listOf(dayMondayOne))
        assertTrue(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `shouldCheckLimit returns false when current day is NOT in list`() {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayMondayOne = ((currentDayOfWeek + 5) % 7) + 1
        
        // Pick a different day
        val otherDay = if (dayMondayOne == 7) 1 else dayMondayOne + 1
        val group = AppLimitGroup(weekDays = listOf(otherDay))
        
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `isWithinTimeRange returns true when now matches any configured range`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 15)
        }
        val group = AppLimitGroup(
            useTimeRange = true,
            timeRanges = listOf(
                TimeRange(startHour = 8, startMinute = 0, endHour = 9, endMinute = 0),
                TimeRange(startHour = 13, startMinute = 0, endHour = 14, endMinute = 0)
            )
        )

        assertTrue(AppLimitEvaluator.isWithinTimeRange(group, calendar.timeInMillis))
    }

    @Test
    fun `isWithinTimeRange supports overnight ranges`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 30)
        }
        val group = AppLimitGroup(
            useTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 22, startMinute = 0, endHour = 2, endMinute = 0))
        )

        assertTrue(AppLimitEvaluator.isWithinTimeRange(group, calendar.timeInMillis))
    }

    @Test
    fun `outside range with Block Apps enabled returns shouldBlockOutsideTimeRange true`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(TimeRange(startHour = 20, startMinute = 0, endHour = 22, endMinute = 0))
        )

        assertTrue(AppLimitEvaluator.shouldBlockOutsideTimeRange(group, calendar.timeInMillis))
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group, calendar.timeInMillis))
    }

    @Test
    fun `outside range with Allow No Limits returns no block and no limit enforcement`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val group = AppLimitGroup(
            useTimeRange = true,
            blockOutsideTimeRange = false,
            timeRanges = listOf(TimeRange(startHour = 20, startMinute = 0, endHour = 22, endMinute = 0))
        )

        assertFalse(AppLimitEvaluator.shouldBlockOutsideTimeRange(group, calendar.timeInMillis))
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group, calendar.timeInMillis))
    }

    @Test
    fun `shouldCheckLimit returns false when group is paused`() {
        val group = AppLimitGroup(paused = true, weekDays = emptyList())

        assertFalse(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `isLimitReached returns true only when remaining is zeroOrLess`() {
        val reached = AppLimitGroup(timeRemaining = 0L)
        val overUsed = AppLimitGroup(timeRemaining = -1L)
        val notReached = AppLimitGroup(timeRemaining = 1L)

        assertTrue(AppLimitEvaluator.isLimitReached(reached))
        assertTrue(AppLimitEvaluator.isLimitReached(overUsed))
        assertFalse(AppLimitEvaluator.isLimitReached(notReached))
    }
}
