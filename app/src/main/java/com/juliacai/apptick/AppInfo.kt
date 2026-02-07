package com.juliacai.apptick

import android.graphics.drawable.Drawable
import java.io.Serializable
import java.util.concurrent.TimeUnit

data class AppInfo(
    var appName: String,
    var appPackage: String?,
    var appIcon: Drawable? = null,
    var appUse: String? = null,
    var appTimeUse: Long = 0,
    var isSelected: Boolean = false,
    var timeStamp: Long = System.currentTimeMillis(),
    var timeUsed: Int = 0, // in minutes
    var lastTimeStamp: Long = System.currentTimeMillis()
) : Serializable {

    fun updateTimeUse(additionalTime: Long) {
        appTimeUse += additionalTime
    }

    fun resetTimeUse() {
        appTimeUse = 0
    }

    val formattedTimeUse: String
        get() {
            val hours = TimeUnit.MILLISECONDS.toHours(appTimeUse)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(appTimeUse) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(appTimeUse) % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

    val elapsedTime: Long
        get() = System.currentTimeMillis() - timeStamp
}
