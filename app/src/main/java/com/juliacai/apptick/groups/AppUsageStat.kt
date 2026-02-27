package com.juliacai.apptick.groups

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class AppUsageStat(
    @SerializedName(value = "appPackage", alternate = ["a"])
    val appPackage: String,
    @SerializedName(value = "usedMillis", alternate = ["b"])
    val usedMillis: Long
) : Serializable
