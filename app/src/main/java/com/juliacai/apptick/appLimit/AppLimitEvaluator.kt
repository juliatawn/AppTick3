package com.juliacai.apptick.appLimit

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
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = group.startHour * 60 + group.startMinute
        val endMinutes = group.endHour * 60 + group.endMinute

        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    fun isLimitReached(group: AppLimitGroup): Boolean = group.timeRemaining <= 0L
}
