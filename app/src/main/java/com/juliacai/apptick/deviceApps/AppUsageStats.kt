package com.juliacai.apptick.deviceApps

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object AppUsageStats {

    private var usageStatsManager: UsageStatsManager? = null

    fun initialize(context: Context) {
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    fun getUsageTime(packageName: String): Long {
        val cal = Calendar.getInstance()
        val endTime = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startTime = cal.timeInMillis

        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        return stats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0
    }
}
