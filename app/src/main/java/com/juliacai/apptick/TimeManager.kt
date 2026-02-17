package com.juliacai.apptick

import com.juliacai.apptick.groups.AppLimitGroup
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimeManager(private val group: AppLimitGroup) {

    fun getTimeRemaining(): Long {
        val limitInMinutes = group.timeHrLimit * 60 + group.timeMinLimit
        return TimeUnit.MINUTES.toMillis(limitInMinutes.toLong())
    }

    /**
     * Returns the next reset time for this group.
     * - If a periodic reset interval is set (`resetMinutes > 0`), returns `now + resetMinutes` in millis.
     * - Otherwise, defaults to midnight (12:00 AM) tomorrow in the device's timezone.
     */
    fun getNextResetTime(): Long {
        return if (group.resetMinutes > 0) {
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(group.resetMinutes.toLong())
        } else {
            nextMidnight()
        }
    }

    companion object {
        /**
         * Returns epoch millis for 12:00 AM tomorrow in the device's default timezone.
         * Uses [Calendar] which inherits the device locale and timezone automatically.
         */
        fun nextMidnight(nowMillis: Long = System.currentTimeMillis()): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
