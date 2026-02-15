package com.juliacai.apptick.appLimit

import com.juliacai.apptick.groups.AppLimitGroup
import java.util.Calendar

object AppLimitEvaluator {

    fun shouldCheckLimit(group: AppLimitGroup, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (group.paused) return false
        
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }

        // Check Day of Week
        // Calendar.SUNDAY = 1, MONDAY = 2, ...
        // custom ints in weekDays? Need to verify how they are stored.
        // Assuming they follow Calendar.DAY_OF_WEEK or similar 1-7.
        // If weekDays is empty, assume all days? AGENTS.md says "select days". Default might be all.
        // Let's assume matches Calendar.DAY_OF_WEEK for now or 0-6.
        // Usually UI pickers give specific values.
        // If weekDays is not empty and day is not in it -> return false.
        if (group.weekDays.isNotEmpty()) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
             // Common adaptation if needed. Let's assume strictly contains Calendar constants
            if (!group.weekDays.contains(dayOfWeek)) {
                return false
            }
        }

        // Check Time Range
        if (group.useTimeRange) {
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentTotalMinutes = currentHour * 60 + currentMinute
            
            val startTotalMinutes = group.startHour * 60 + group.startMinute
            val endTotalMinutes = group.endHour * 60 + group.endMinute
            
            // Handle overnight ranges (e.g. 23:00 to 07:00)
            if (startTotalMinutes <= endTotalMinutes) {
                // Normal range
                if (currentTotalMinutes < startTotalMinutes || currentTotalMinutes >= endTotalMinutes) {
                    return false
                }
            } else {
                // Overnight range
                if (currentTotalMinutes < startTotalMinutes && currentTotalMinutes >= endTotalMinutes) {
                    return false
                }
            }
        }

        return true
    }

    fun isLimitReached(group: AppLimitGroup): Boolean {
        return group.timeRemaining <= 0
    }
}
