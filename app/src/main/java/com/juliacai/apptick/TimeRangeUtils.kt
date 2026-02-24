package com.juliacai.apptick

import android.content.Context
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import java.util.Calendar

fun AppLimitGroup.getConfiguredTimeRanges(): List<TimeRange> {
    if (timeRanges.isNotEmpty()) return timeRanges
    if (!useTimeRange) return emptyList()
    return listOf(
        TimeRange(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )
    )
}

fun isNowWithinTimeRange(range: TimeRange, nowMinutes: Int): Boolean {
    val startMinutes = range.startHour * 60 + range.startMinute
    val endMinutes = range.endHour * 60 + range.endMinute
    return if (startMinutes <= endMinutes) {
        nowMinutes in startMinutes..endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes <= endMinutes
    }
}

fun isNowWithinAnyTimeRange(ranges: List<TimeRange>, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (ranges.isEmpty()) return true
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return ranges.any { isNowWithinTimeRange(it, nowMinutes) }
}

fun formatTimeRanges(context: Context, group: AppLimitGroup): String {
    return group.getConfiguredTimeRanges()
        .joinToString(", ") { range ->
            "${formatClockTime(context, range.startHour, range.startMinute)} - ${
                formatClockTime(context, range.endHour, range.endMinute)
            }"
        }
}
