package com.juliacai.apptick

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/**
 * Formats a wall-clock time using the device's 12/24-hour preference and locale.
 */
fun formatClockTime(context: Context, hourOfDay: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
