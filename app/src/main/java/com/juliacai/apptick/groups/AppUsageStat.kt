package com.juliacai.apptick.groups

import java.io.Serializable

data class AppUsageStat(
    val appPackage: String,
    val usedMillis: Long
) : Serializable
