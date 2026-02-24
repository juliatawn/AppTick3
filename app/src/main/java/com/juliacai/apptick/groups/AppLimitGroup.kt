package com.juliacai.apptick.groups

import com.juliacai.apptick.appLimit.AppInGroup
import java.io.Serializable

data class AppLimitGroup(
    var id: Long = 0,
    var name: String? = null,
    var timeHrLimit: Int = 0,
    var timeMinLimit: Int = 0,
    var limitEach: Boolean = false,
    var resetMinutes: Int = 0,
    var weekDays: List<Int> = emptyList(),
    var apps: List<AppInGroup> = emptyList(),
    var paused: Boolean = false,
    var useTimeRange: Boolean = false,
    var blockOutsideTimeRange: Boolean = false,
    var timeRanges: List<TimeRange> = emptyList(),
    var startHour: Int = 0,
    var startMinute: Int = 0,
    var endHour: Int = 0,
    var endMinute: Int = 0,
    var cumulativeTime: Boolean = false,
    var timeRemaining: Long = 0,
    var nextResetTime: Long = 0,
    var nextAddTime: Long = 0,
    var perAppUsage: List<AppUsageStat> = emptyList(),
    var isExpanded: Boolean = true
) : Serializable
