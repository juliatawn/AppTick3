package com.juliacai.apptick

import com.juliacai.apptick.groups.AppLimitGroup
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimeManager(private val group: AppLimitGroup) {

    fun getTimeRemaining(): Long {
        val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
        return TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
    }

    fun getNextResetTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, group.resetHours)
        return calendar.timeInMillis
    }
}
