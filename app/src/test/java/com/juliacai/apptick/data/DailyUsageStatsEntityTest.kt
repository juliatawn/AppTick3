package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.deviceApps.AppUsageStats
import org.junit.Test
import java.util.Calendar

class DailyUsageStatsEntityTest {

    @Test
    fun `entity stores all fields correctly`() {
        val entity = DailyUsageStatsEntity(
            dateString = "2026-04-02",
            packageName = "com.example.app",
            appName = "Example App",
            totalForegroundMs = 3_600_000L
        )
        assertThat(entity.dateString).isEqualTo("2026-04-02")
        assertThat(entity.packageName).isEqualTo("com.example.app")
        assertThat(entity.appName).isEqualTo("Example App")
        assertThat(entity.totalForegroundMs).isEqualTo(3_600_000L)
    }

    @Test
    fun `toDateString produces ISO format with 1-based month`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY) // 0-based
            set(Calendar.DAY_OF_MONTH, 5)
        }
        val result = AppUsageStats.toDateString(cal.timeInMillis)
        assertThat(result).isEqualTo("2026-01-05")
    }

    @Test
    fun `toDateString pads single digits`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH) // month 2 (0-based) → 03
            set(Calendar.DAY_OF_MONTH, 9)
        }
        val result = AppUsageStats.toDateString(cal.timeInMillis)
        assertThat(result).isEqualTo("2026-03-09")
    }

    @Test
    fun `toDateString handles December`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
        }
        val result = AppUsageStats.toDateString(cal.timeInMillis)
        assertThat(result).isEqualTo("2025-12-31")
    }

    @Test
    fun `composite key uniqueness — same date different app`() {
        val entity1 = DailyUsageStatsEntity("2026-04-02", "com.a", "A", 100L)
        val entity2 = DailyUsageStatsEntity("2026-04-02", "com.b", "B", 200L)
        assertThat(entity1).isNotEqualTo(entity2)
    }

    @Test
    fun `composite key uniqueness — same app different date`() {
        val entity1 = DailyUsageStatsEntity("2026-04-01", "com.a", "A", 100L)
        val entity2 = DailyUsageStatsEntity("2026-04-02", "com.a", "A", 200L)
        assertThat(entity1).isNotEqualTo(entity2)
    }
}
