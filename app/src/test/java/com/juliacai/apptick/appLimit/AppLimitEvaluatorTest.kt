package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.AppLimitGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AppLimitEvaluatorTest {

    @Test
    fun `shouldCheckLimit returns false when group is paused`() {
        val group = AppLimitGroup(paused = true)
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `shouldCheckLimit returns true when inactive settings`() {
        val group = AppLimitGroup(paused = false, weekDays = emptyList(), useTimeRange = false)
        assertTrue(AppLimitEvaluator.shouldCheckLimit(group))
    }

    @Test
    fun `shouldCheckLimit respects days of week`() {
        // Monday = 2
        val group = AppLimitGroup(weekDays = listOf(Calendar.MONDAY))
        
        val monday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        assertTrue(AppLimitEvaluator.shouldCheckLimit(group, monday.timeInMillis))

        val tuesday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
        }
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group, tuesday.timeInMillis))
    }

    @Test
    fun `shouldCheckLimit respects time range`() {
        val group = AppLimitGroup(
            useTimeRange = true,
            startHour = 10, startMinute = 0,
            endHour = 12, endMinute = 0
        )

        val _9am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group, _9am.timeInMillis))

        val _11am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(AppLimitEvaluator.shouldCheckLimit(group, _11am.timeInMillis))
        
        val _1pm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(AppLimitEvaluator.shouldCheckLimit(group, _1pm.timeInMillis))
    }
    
    @Test
    fun `isLimitReached returns true when timeRemaining is 0 or less`() {
        assertTrue(AppLimitEvaluator.isLimitReached(AppLimitGroup(timeRemaining = 0)))
        assertTrue(AppLimitEvaluator.isLimitReached(AppLimitGroup(timeRemaining = -100)))
        assertFalse(AppLimitEvaluator.isLimitReached(AppLimitGroup(timeRemaining = 100)))
    }
    @Test
    fun `isLimitReached returns false when limit is reset (timeRemaining positive)`() {
        // Simulate a group that was blocked (timeRemaining = 0)
        val group = AppLimitGroup(timeRemaining = 0)
        
        // Use AppLimitEvaluator...
        // Now simulate reset
        group.timeRemaining = 60000 // 1 minute
        
        assertFalse("App should not be blocked after reset", AppLimitEvaluator.isLimitReached(group))
    }
}
