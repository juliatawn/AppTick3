package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.AppLimitGroup
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
}
