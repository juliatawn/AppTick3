package com.juliacai.apptick.data

import androidx.room.Entity

/**
 * Stores per-app daily usage data locally, bypassing Android's ~7-10 day
 * INTERVAL_DAILY retention limit. One row per app per day.
 *
 * [dateString] uses ISO format "yyyy-MM-dd" (1-based months) for correct
 * lexicographic sorting in SQL BETWEEN queries.
 */
@Entity(
    tableName = "daily_usage_stats",
    primaryKeys = ["dateString", "packageName"]
)
data class DailyUsageStatsEntity(
    val dateString: String,        // "2026-04-02" (yyyy-MM-dd, 1-based month)
    val packageName: String,
    val appName: String,
    val totalForegroundMs: Long
)
