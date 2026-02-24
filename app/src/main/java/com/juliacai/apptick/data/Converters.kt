package com.juliacai.apptick.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.TimeRange

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        val listType = object : TypeToken<List<Int>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromAppInGroupList(value: List<AppInGroup>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toAppInGroupList(value: String): List<AppInGroup> {
        val listType = object : TypeToken<List<AppInGroup>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromAppUsageStatList(value: List<AppUsageStat>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toAppUsageStatList(value: String): List<AppUsageStat> {
        val listType = object : TypeToken<List<AppUsageStat>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromTimeRangeList(value: List<TimeRange>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toTimeRangeList(value: String): List<TimeRange> {
        val listType = object : TypeToken<List<TimeRange>>() {}.type
        return Gson().fromJson(value, listType)
    }
}
