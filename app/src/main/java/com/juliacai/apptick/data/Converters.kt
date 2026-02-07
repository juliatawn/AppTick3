package com.juliacai.apptick.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.juliacai.apptick.appLimit.AppInGroup

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
}
