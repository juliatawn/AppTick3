package com.juliacai.apptick.deviceApps

import android.app.usage.UsageStatsManager
import android.content.Context
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.DailyUsageStatsDao
import java.text.DateFormatSymbols
import java.util.Calendar

enum class UsagePeriod(val label: String, val calendarField: Int, val calendarAmount: Int) {
    DAY("Day", Calendar.DAY_OF_YEAR, -1),
    WEEK("Week", Calendar.WEEK_OF_YEAR, -1),
    MONTH("Month", Calendar.MONTH, -1),
    YEAR("Year", Calendar.YEAR, -1)
}

/** A single day's usage data. */
data class DailyUsage(
    val year: Int,
    val month: Int,   // 0-based (Calendar.JANUARY = 0)
    val dayOfMonth: Int,
    val dayOfWeek: Int, // Calendar.SUNDAY=1 .. Calendar.SATURDAY=7
    val usageMillis: Long
)

/** A single month's usage data. */
data class MonthlyUsage(
    val year: Int,
    val month: Int,   // 0-based
    val usageMillis: Long
)

object AppUsageStats {

    private var usageStatsManager: UsageStatsManager? = null
    private var dailyUsageStatsDao: DailyUsageStatsDao? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        dailyUsageStatsDao = AppTickDatabase.getDatabase(context).dailyUsageStatsDao()
        appContext = context.applicationContext
    }

    fun getUsageTime(packageName: String): Long {
        return getUsageForPeriod(packageName, UsagePeriod.DAY)
    }

    /** Whether the user has enabled long-term stats in settings. */
    private fun isLongTermStatsEnabled(): Boolean {
        val prefs = appContext?.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            ?: return false
        return prefs.getBoolean("storeLongTermUsageStats", true)
    }

    /** Convert epoch millis to ISO date string "yyyy-MM-dd" (1-based month). */
    internal fun toDateString(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // ── Period total ───────────────────────────────────────────────────────

    /**
     * Non-suspend version for backward compatibility (used by getUsageTime etc).
     * Queries Android's UsageStatsManager directly.
     */
    fun getUsageForPeriod(packageName: String, period: UsagePeriod, offset: Int = 0): Long {
        val (startTime, endTime) = periodRange(period, offset)

        val interval = if (period == UsagePeriod.DAY) {
            UsageStatsManager.INTERVAL_DAILY
        } else {
            UsageStatsManager.INTERVAL_BEST
        }

        val stats = usageStatsManager?.queryUsageStats(
            interval, startTime, endTime
        ) ?: emptyList()

        return stats
            .filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    /**
     * Suspend version that prefers local DB data when available, falls back to Android.
     */
    suspend fun getUsageForPeriodLocal(packageName: String, period: UsagePeriod, offset: Int = 0): Long {
        val dao = dailyUsageStatsDao
        if (dao != null && isLongTermStatsEnabled()) {
            val (startTime, endTime) = periodRange(period, offset)
            val startDate = toDateString(startTime)
            val endDate = toDateString(endTime)
            val count = dao.countEntriesInRange(startDate, endDate)
            if (count > 0) {
                return dao.getTotalForAppInRange(packageName, startDate, endDate)
            }
        }
        return getUsageForPeriod(packageName, period, offset)
    }

    /**
     * Counts the number of days with recorded usage for a specific app in a period.
     * Returns 0 if local DB has no data (caller should fall back to a fixed divisor).
     */
    suspend fun countDaysWithData(packageName: String, period: UsagePeriod, offset: Int = 0): Int {
        val dao = dailyUsageStatsDao ?: return 0
        if (!isLongTermStatsEnabled()) return 0
        val (startTime, endTime) = periodRange(period, offset)
        val startDate = toDateString(startTime)
        val endDate = toDateString(endTime)
        return dao.countDaysWithDataForApp(packageName, startDate, endDate)
    }

    // ── Period ranges ──────────────────────────────────────────────────────

    /**
     * Returns the start/end epoch millis for a given period and offset.
     *
     * - **DAY**: midnight to 23:59:59 of that day
     * - **WEEK**: Monday 00:00 to Sunday 23:59:59 (ISO week)
     * - **MONTH**: 1st 00:00 to last day 23:59:59 of that calendar month
     * - **YEAR**: Jan 1 00:00 to Dec 31 23:59:59 of that calendar year
     */
    fun periodRange(period: UsagePeriod, offset: Int = 0): Pair<Long, Long> {
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance()

        when (period) {
            UsagePeriod.DAY -> {
                startCal.add(Calendar.DAY_OF_YEAR, -offset)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
            UsagePeriod.WEEK -> {
                val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                startCal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                startCal.add(Calendar.WEEK_OF_YEAR, -offset)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.DAY_OF_YEAR, 6)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
            UsagePeriod.MONTH -> {
                startCal.add(Calendar.MONTH, -offset)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
            UsagePeriod.YEAR -> {
                startCal.add(Calendar.YEAR, -offset)
                startCal.set(Calendar.MONTH, Calendar.JANUARY)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.set(Calendar.MONTH, Calendar.DECEMBER)
                endCal.set(Calendar.DAY_OF_MONTH, 31)
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
            }
        }

        return startCal.timeInMillis to endCal.timeInMillis
    }

    // ── Breakdowns (Android data) ──────────────────────────────────────────

    fun getWeeklyDailyBreakdown(packageName: String, offset: Int = 0): List<DailyUsage> {
        val (startTime, endTime) = periodRange(UsagePeriod.WEEK, offset)
        return getDailyBreakdownFromAndroid(packageName, startTime, endTime)
    }

    fun getMonthlyDailyBreakdown(packageName: String, offset: Int = 0): List<DailyUsage> {
        val (startTime, endTime) = periodRange(UsagePeriod.MONTH, offset)
        return getDailyBreakdownFromAndroid(packageName, startTime, endTime)
    }

    fun getYearlyMonthlyBreakdown(packageName: String, offset: Int = 0): List<MonthlyUsage> {
        val (startTime, endTime) = periodRange(UsagePeriod.YEAR, offset)
        return getMonthlyBreakdownFromAndroid(packageName, startTime, endTime)
    }

    // ── Breakdowns (local DB preferred) ────────────────────────────────────

    /**
     * Suspend version: prefers local DB for daily breakdown, falls back to Android.
     */
    suspend fun getWeeklyDailyBreakdownLocal(packageName: String, offset: Int = 0): List<DailyUsage> {
        val (startTime, endTime) = periodRange(UsagePeriod.WEEK, offset)
        return getDailyBreakdownLocal(packageName, startTime, endTime)
            ?: getDailyBreakdownFromAndroid(packageName, startTime, endTime)
    }

    suspend fun getMonthlyDailyBreakdownLocal(packageName: String, offset: Int = 0): List<DailyUsage> {
        val (startTime, endTime) = periodRange(UsagePeriod.MONTH, offset)
        return getDailyBreakdownLocal(packageName, startTime, endTime)
            ?: getDailyBreakdownFromAndroid(packageName, startTime, endTime)
    }

    suspend fun getYearlyMonthlyBreakdownLocal(packageName: String, offset: Int = 0): List<MonthlyUsage> {
        val dao = dailyUsageStatsDao
        if (dao != null && isLongTermStatsEnabled()) {
            val (startTime, endTime) = periodRange(UsagePeriod.YEAR, offset)
            val startDate = toDateString(startTime)
            val endDate = toDateString(endTime)
            val count = dao.countEntriesInRange(startDate, endDate)
            if (count > 0) {
                // Aggregate local daily data into monthly buckets
                val rows = dao.getForAppInRange(packageName, startDate, endDate)
                val monthlyMap = mutableMapOf<String, Long>()
                for (row in rows) {
                    // dateString is "yyyy-MM-dd", extract "yyyy-MM"
                    val monthKey = row.dateString.substring(0, 7)
                    monthlyMap[monthKey] = (monthlyMap[monthKey] ?: 0L) + row.totalForegroundMs
                }
                // Build Jan-Dec
                val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
                val result = mutableListOf<MonthlyUsage>()
                val mCal = Calendar.getInstance().apply { timeInMillis = startTime }
                for (i in 0 until 12) {
                    val key = "%04d-%02d".format(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH) + 1)
                    result.add(
                        MonthlyUsage(
                            year = mCal.get(Calendar.YEAR),
                            month = mCal.get(Calendar.MONTH),
                            usageMillis = monthlyMap[key] ?: 0L
                        )
                    )
                    mCal.add(Calendar.MONTH, 1)
                }
                return result
            }
        }
        return getYearlyMonthlyBreakdown(packageName, offset)
    }

    /**
     * Queries local DB for daily breakdown. Returns null if no local data exists
     * for the range (caller should fall back to Android).
     */
    private suspend fun getDailyBreakdownLocal(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<DailyUsage>? {
        val dao = dailyUsageStatsDao ?: return null
        if (!isLongTermStatsEnabled()) return null

        val startDate = toDateString(startTime)
        val endDate = toDateString(endTime)
        val count = dao.countEntriesInRange(startDate, endDate)
        if (count == 0) return null

        val rows = dao.getForAppInRange(packageName, startDate, endDate)
        val localMap = mutableMapOf<String, Long>()
        for (row in rows) {
            localMap[row.dateString] = row.totalForegroundMs
        }

        // Build list for each day in range
        val result = mutableListOf<DailyUsage>()
        val dayCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
        while (!dayCal.after(endCal)) {
            val isoDate = "%04d-%02d-%02d".format(
                dayCal.get(Calendar.YEAR),
                dayCal.get(Calendar.MONTH) + 1,
                dayCal.get(Calendar.DAY_OF_MONTH)
            )
            result.add(
                DailyUsage(
                    year = dayCal.get(Calendar.YEAR),
                    month = dayCal.get(Calendar.MONTH),
                    dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH),
                    dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK),
                    usageMillis = localMap[isoDate] ?: 0L
                )
            )
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    // ── Android-only queries ───────────────────────────────────────────────

    /**
     * Gets daily usage from Android's INTERVAL_DAILY (~7-10 day retention).
     * Returns entries in chronological order (earliest first).
     */
    private fun getDailyBreakdownFromAndroid(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<DailyUsage> {
        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        ) ?: emptyList()

        // Bucket by day using 0-based month keys (internal only)
        val dailyMap = mutableMapOf<String, Long>()
        for (stat in stats) {
            if (stat.packageName != packageName) continue
            val dayCal = Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
            val key = "%04d-%02d-%02d".format(
                dayCal.get(Calendar.YEAR),
                dayCal.get(Calendar.MONTH),
                dayCal.get(Calendar.DAY_OF_MONTH)
            )
            dailyMap[key] = (dailyMap[key] ?: 0L) + stat.totalTimeInForeground
        }

        val result = mutableListOf<DailyUsage>()
        val dayCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
        while (!dayCal.after(endCal)) {
            val key = "%04d-%02d-%02d".format(
                dayCal.get(Calendar.YEAR),
                dayCal.get(Calendar.MONTH),
                dayCal.get(Calendar.DAY_OF_MONTH)
            )
            result.add(
                DailyUsage(
                    year = dayCal.get(Calendar.YEAR),
                    month = dayCal.get(Calendar.MONTH),
                    dayOfMonth = dayCal.get(Calendar.DAY_OF_MONTH),
                    dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK),
                    usageMillis = dailyMap[key] ?: 0L
                )
            )
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    /**
     * Gets monthly usage from Android's INTERVAL_MONTHLY (~6 month retention).
     */
    private fun getMonthlyBreakdownFromAndroid(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): List<MonthlyUsage> {
        val stats = usageStatsManager?.queryUsageStats(
            UsageStatsManager.INTERVAL_MONTHLY, startTime, endTime
        ) ?: emptyList()

        val monthlyMap = mutableMapOf<String, Long>()
        for (stat in stats) {
            if (stat.packageName != packageName) continue
            val mCal = Calendar.getInstance().apply { timeInMillis = stat.firstTimeStamp }
            val key = "%04d-%02d".format(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH))
            monthlyMap[key] = (monthlyMap[key] ?: 0L) + stat.totalTimeInForeground
        }

        val result = mutableListOf<MonthlyUsage>()
        val mCal = Calendar.getInstance().apply { timeInMillis = startTime }
        for (i in 0 until 12) {
            val key = "%04d-%02d".format(mCal.get(Calendar.YEAR), mCal.get(Calendar.MONTH))
            result.add(
                MonthlyUsage(
                    year = mCal.get(Calendar.YEAR),
                    month = mCal.get(Calendar.MONTH),
                    usageMillis = monthlyMap[key] ?: 0L
                )
            )
            mCal.add(Calendar.MONTH, 1)
        }
        return result
    }

    // ── Labels ─────────────────────────────────────────────────────────────

    fun periodLabel(period: UsagePeriod, offset: Int): String {
        if (offset == 0) {
            return when (period) {
                UsagePeriod.DAY -> "Today"
                UsagePeriod.WEEK -> "This Week"
                UsagePeriod.MONTH -> "This Month"
                UsagePeriod.YEAR -> "This Year"
            }
        }
        if (offset == 1) {
            return when (period) {
                UsagePeriod.DAY -> "Yesterday"
                UsagePeriod.WEEK -> "Last Week"
                UsagePeriod.MONTH -> "Last Month"
                UsagePeriod.YEAR -> "Last Year"
            }
        }
        return when (period) {
            UsagePeriod.DAY -> "$offset Days Ago"
            UsagePeriod.WEEK -> "$offset Weeks Ago"
            UsagePeriod.MONTH -> "$offset Months Ago"
            UsagePeriod.YEAR -> "$offset Years Ago"
        }
    }

    /**
     * Returns a date range string for a period at a given offset.
     */
    fun periodDateRange(period: UsagePeriod, offset: Int): String {
        val (startTime, endTime) = periodRange(period, offset)
        val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
        val shortMonths = DateFormatSymbols.getInstance().shortMonths
        val weekdays = DateFormatSymbols.getInstance().weekdays

        return when (period) {
            UsagePeriod.DAY -> {
                val dayName = weekdays[startCal.get(Calendar.DAY_OF_WEEK)]
                "$dayName, ${shortMonths[startCal.get(Calendar.MONTH)]} ${startCal.get(Calendar.DAY_OF_MONTH)}"
            }
            UsagePeriod.WEEK -> {
                "Mon ${shortMonths[startCal.get(Calendar.MONTH)]} ${startCal.get(Calendar.DAY_OF_MONTH)} - Sun ${shortMonths[endCal.get(Calendar.MONTH)]} ${endCal.get(Calendar.DAY_OF_MONTH)}"
            }
            UsagePeriod.MONTH -> {
                val monthNames = DateFormatSymbols.getInstance().months
                "${monthNames[startCal.get(Calendar.MONTH)]} ${startCal.get(Calendar.YEAR)}"
            }
            UsagePeriod.YEAR -> {
                "${startCal.get(Calendar.YEAR)}"
            }
        }
    }
}
