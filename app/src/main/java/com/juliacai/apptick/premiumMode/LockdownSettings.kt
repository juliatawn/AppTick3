package com.juliacai.apptick.premiumMode

import java.io.Serializable

data class LockdownSettings(
    val endDate: Long? = null,
    val weeklyResetDay: Int? = null, // 1-7 for Monday-Sunday
    val weeklyResetHour: Int? = null,
    val weeklyResetMinute: Int? = null,
    val allowOneTimeChange: Boolean = false
) : Serializable
