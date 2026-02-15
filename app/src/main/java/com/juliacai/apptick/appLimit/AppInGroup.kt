package com.juliacai.apptick.appLimit

import java.io.Serializable

data class AppInGroup(
    val appName: String,
    val appPackage: String,
    val appIcon: String?
) : Serializable
