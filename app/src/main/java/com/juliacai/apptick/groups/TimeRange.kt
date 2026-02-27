package com.juliacai.apptick.groups

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class TimeRange(
    @SerializedName(value = "startHour", alternate = ["a"])
    val startHour: Int = 0,
    @SerializedName(value = "startMinute", alternate = ["b"])
    val startMinute: Int = 0,
    @SerializedName(value = "endHour", alternate = ["c"])
    val endHour: Int = 23,
    @SerializedName(value = "endMinute", alternate = ["d"])
    val endMinute: Int = 59
) : Serializable
