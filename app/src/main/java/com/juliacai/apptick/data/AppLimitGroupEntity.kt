package com.juliacai.apptick.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.TimeRange

@Entity(tableName = "app_limit_groups")
data class AppLimitGroupEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String? = null,
    var timeHrLimit: Int = 0,
    var timeMinLimit: Int = 0,
    var limitEach: Boolean = false,
    @ColumnInfo(name = "resetHours")
    var resetMinutes: Int = 0,
    var weekDays: List<Int> = emptyList(),
    var apps: List<AppInGroup> = emptyList(),
    var paused: Boolean = false,
    var useTimeRange: Boolean = false,
    var blockOutsideTimeRange: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'")
    var timeRanges: List<TimeRange> = emptyList(),
    var startHour: Int = 0,
    var startMinute: Int = 0,
    var endHour: Int = 0,
    var endMinute: Int = 0,
    var cumulativeTime: Boolean = false,
    var timeRemaining: Long = 0,
    var nextResetTime: Long = 0,
    var nextAddTime: Long = 0,
    @ColumnInfo(defaultValue = "'[]'")
    var perAppUsage: List<AppUsageStat> = emptyList(),
    @ColumnInfo(defaultValue = "1")
    var isExpanded: Boolean = true
)
