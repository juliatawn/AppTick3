package com.juliacai.apptick.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.TimeRange

@Entity(tableName = "app_limit_groups")
data class AppLimitGroupEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName(value = "id", alternate = ["a"])
    var id: Long = 0,
    @SerializedName(value = "name", alternate = ["b"])
    var name: String? = null,
    @SerializedName(value = "timeHrLimit", alternate = ["c"])
    var timeHrLimit: Int = 0,
    @SerializedName(value = "timeMinLimit", alternate = ["d"])
    var timeMinLimit: Int = 0,
    @SerializedName(value = "limitEach", alternate = ["e"])
    var limitEach: Boolean = false,
    @ColumnInfo(name = "resetHours")
    @SerializedName(value = "resetMinutes", alternate = ["f", "resetHours"])
    var resetMinutes: Int = 0,
    @SerializedName(value = "weekDays", alternate = ["g"])
    var weekDays: List<Int> = emptyList(),
    @SerializedName(value = "apps", alternate = ["h"])
    var apps: List<AppInGroup> = emptyList(),
    @SerializedName(value = "paused", alternate = ["i"])
    var paused: Boolean = false,
    @SerializedName(value = "useTimeRange", alternate = ["j"])
    var useTimeRange: Boolean = false,
    @SerializedName(value = "blockOutsideTimeRange", alternate = ["k"])
    var blockOutsideTimeRange: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'")
    @SerializedName(value = "timeRanges", alternate = ["l"])
    var timeRanges: List<TimeRange> = emptyList(),
    @SerializedName(value = "startHour", alternate = ["m"])
    var startHour: Int = 0,
    @SerializedName(value = "startMinute", alternate = ["n"])
    var startMinute: Int = 0,
    @SerializedName(value = "endHour", alternate = ["o"])
    var endHour: Int = 0,
    @SerializedName(value = "endMinute", alternate = ["p"])
    var endMinute: Int = 0,
    @SerializedName(value = "cumulativeTime", alternate = ["q"])
    var cumulativeTime: Boolean = false,
    @SerializedName(value = "timeRemaining", alternate = ["r"])
    var timeRemaining: Long = 0,
    @SerializedName(value = "nextResetTime", alternate = ["s"])
    var nextResetTime: Long = 0,
    @SerializedName(value = "nextAddTime", alternate = ["t"])
    var nextAddTime: Long = 0,
    @ColumnInfo(defaultValue = "'[]'")
    @SerializedName(value = "perAppUsage", alternate = ["u"])
    var perAppUsage: List<AppUsageStat> = emptyList(),
    @ColumnInfo(defaultValue = "1")
    @SerializedName(value = "isExpanded", alternate = ["v"])
    var isExpanded: Boolean = true
)
