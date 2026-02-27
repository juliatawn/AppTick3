package com.juliacai.apptick.appLimit

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class AppInGroup(
    @SerializedName(value = "appName", alternate = ["a"])
    val appName: String,
    @SerializedName(value = "appPackage", alternate = ["b"])
    val appPackage: String,
    @SerializedName(value = "appIcon", alternate = ["c"])
    val appIcon: String?
) : Serializable
