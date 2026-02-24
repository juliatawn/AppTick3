package com.juliacai.apptick.groups

import java.io.Serializable

data class TimeRange(
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 23,
    val endMinute: Int = 59
) : Serializable
