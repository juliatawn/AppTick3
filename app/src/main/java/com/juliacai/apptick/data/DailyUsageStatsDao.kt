package com.juliacai.apptick.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyUsageStatsDao {

    /** Upsert a single day's usage for one app. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyUsageStatsEntity)

    /** Bulk upsert for backfill / batch recording. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DailyUsageStatsEntity>)

    /** Single day, single app. */
    @Query("SELECT * FROM daily_usage_stats WHERE dateString = :dateString AND packageName = :packageName")
    suspend fun getForAppOnDate(dateString: String, packageName: String): DailyUsageStatsEntity?

    /** All apps for a single day. */
    @Query("SELECT * FROM daily_usage_stats WHERE dateString = :dateString")
    suspend fun getAllForDate(dateString: String): List<DailyUsageStatsEntity>

    /** Date range for a single app (for week/month breakdowns). Sorted chronologically. */
    @Query(
        "SELECT * FROM daily_usage_stats " +
        "WHERE packageName = :packageName AND dateString BETWEEN :startDate AND :endDate " +
        "ORDER BY dateString ASC"
    )
    suspend fun getForAppInRange(
        packageName: String,
        startDate: String,
        endDate: String
    ): List<DailyUsageStatsEntity>

    /** Sum usage for a date range (for period totals). */
    @Query(
        "SELECT COALESCE(SUM(totalForegroundMs), 0) FROM daily_usage_stats " +
        "WHERE packageName = :packageName AND dateString BETWEEN :startDate AND :endDate"
    )
    suspend fun getTotalForAppInRange(
        packageName: String,
        startDate: String,
        endDate: String
    ): Long

    /** Check if any local data exists for a date range (to decide DB vs Android fallback). */
    @Query("SELECT COUNT(*) FROM daily_usage_stats WHERE dateString BETWEEN :startDate AND :endDate")
    suspend fun countEntriesInRange(startDate: String, endDate: String): Int

    /** Delete data older than a given date (optional cleanup). */
    @Query("DELETE FROM daily_usage_stats WHERE dateString < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    /** Count days with data for a specific app in a range. */
    @Query(
        "SELECT COUNT(*) FROM daily_usage_stats " +
        "WHERE packageName = :packageName AND dateString BETWEEN :startDate AND :endDate " +
        "AND totalForegroundMs > 0"
    )
    suspend fun countDaysWithDataForApp(
        packageName: String,
        startDate: String,
        endDate: String
    ): Int

    /** Delete all stored usage data. */
    @Query("DELETE FROM daily_usage_stats")
    suspend fun deleteAll()
}
