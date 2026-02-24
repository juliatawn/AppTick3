package com.juliacai.apptick.appLimit

import com.juliacai.apptick.getConfiguredTimeRanges
import com.juliacai.apptick.isNowWithinAnyTimeRange
import com.juliacai.apptick.groups.AppLimitGroup
import java.util.Calendar

object AppLimitEvaluator {
    fun shouldCheckLimit(group: AppLimitGroup, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (group.paused) return false
        if (!isWithinActiveDays(group, nowMillis)) return false
        if (!group.useTimeRange) return true
        return isWithinTimeRange(group, nowMillis)
    }

    fun isWithinActiveDays(group: AppLimitGroup, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (group.weekDays.isEmpty()) return true
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayMondayOne = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        return group.weekDays.contains(dayMondayOne)
    }

    fun isWithinTimeRange(group: AppLimitGroup, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!group.useTimeRange) return true
        return isNowWithinAnyTimeRange(group.getConfiguredTimeRanges(), nowMillis)
    }

    fun shouldBlockOutsideTimeRange(
        group: AppLimitGroup,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!group.useTimeRange) return false
        if (!group.blockOutsideTimeRange) return false
        return !isWithinTimeRange(group, nowMillis)
    }

    fun isLimitReached(group: AppLimitGroup): Boolean = group.timeRemaining <= 0L
}
