package com.juliacai.apptick.premiumMode

import com.juliacai.apptick.LockdownType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LockdownSummaryFormatter {
    fun formatTarget(
        lockdownType: LockdownType,
        lockdownEndTimeMillis: Long,
        recurringDays: List<Int>,
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return if (lockdownType == LockdownType.ONE_TIME) {
            val targetMillis = if (lockdownEndTimeMillis > nowMillis) {
                lockdownEndTimeMillis
            } else {
                Calendar.getInstance(timeZone, locale).apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            }
            SimpleDateFormat("EEE, MMM d h:mm a", locale)
                .apply { this.timeZone = timeZone }
                .format(Date(targetMillis))
        } else {
            val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            val names = recurringDays
                .filter { it in 1..7 }
                .distinct()
                .sorted()
                .map { dayNames[it - 1] }
            if (names.isEmpty()) {
                "the next selected day"
            } else {
                "the next ${names.joinToString(", ")}"
            }
        }
    }
}
