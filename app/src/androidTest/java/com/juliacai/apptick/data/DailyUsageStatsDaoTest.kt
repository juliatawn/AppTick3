package com.juliacai.apptick.data

import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyUsageStatsDaoTest {

    private lateinit var db: AppTickDatabase
    private lateinit var dao: DailyUsageStatsDao

    @Before
    fun setUp() {
        assumeTrue(
            "Room tests are stable on emulator",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppTickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyUsageStatsDao()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun upsert_insertsNewRow() = runTest {
        val entity = DailyUsageStatsEntity(
            dateString = "2026-04-02",
            packageName = "com.example.app",
            appName = "Example",
            totalForegroundMs = 60_000L
        )
        dao.upsert(entity)

        val result = dao.getForAppOnDate("2026-04-02", "com.example.app")
        assertThat(result).isNotNull()
        assertThat(result!!.totalForegroundMs).isEqualTo(60_000L)
        assertThat(result.appName).isEqualTo("Example")
    }

    @Test
    fun upsert_replacesExistingRow() = runTest {
        dao.upsert(DailyUsageStatsEntity("2026-04-02", "com.example.app", "Example", 60_000L))
        dao.upsert(DailyUsageStatsEntity("2026-04-02", "com.example.app", "Example", 120_000L))

        val result = dao.getForAppOnDate("2026-04-02", "com.example.app")
        assertThat(result!!.totalForegroundMs).isEqualTo(120_000L)
    }

    @Test
    fun upsertAll_insertsBatch() = runTest {
        val entities = listOf(
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-01", "com.b", "B", 20_000L),
            DailyUsageStatsEntity("2026-04-02", "com.a", "A", 30_000L)
        )
        dao.upsertAll(entities)

        assertThat(dao.getAllForDate("2026-04-01")).hasSize(2)
        assertThat(dao.getAllForDate("2026-04-02")).hasSize(1)
    }

    @Test
    fun getForAppInRange_returnsChronologicalResults() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-03", "com.a", "A", 30_000L),
            DailyUsageStatsEntity("2026-04-02", "com.a", "A", 20_000L),
            DailyUsageStatsEntity("2026-04-02", "com.b", "B", 50_000L) // different app
        ))

        val result = dao.getForAppInRange("com.a", "2026-04-01", "2026-04-03")
        assertThat(result).hasSize(3)
        assertThat(result[0].dateString).isEqualTo("2026-04-01")
        assertThat(result[1].dateString).isEqualTo("2026-04-02")
        assertThat(result[2].dateString).isEqualTo("2026-04-03")
    }

    @Test
    fun getForAppInRange_excludesOutOfRange() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-03-31", "com.a", "A", 5_000L),
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-07", "com.a", "A", 70_000L),
            DailyUsageStatsEntity("2026-04-08", "com.a", "A", 80_000L)
        ))

        val result = dao.getForAppInRange("com.a", "2026-04-01", "2026-04-07")
        assertThat(result).hasSize(2)
    }

    @Test
    fun getTotalForAppInRange_sumsCorrectly() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-02", "com.a", "A", 20_000L),
            DailyUsageStatsEntity("2026-04-03", "com.a", "A", 30_000L)
        ))

        val total = dao.getTotalForAppInRange("com.a", "2026-04-01", "2026-04-03")
        assertThat(total).isEqualTo(60_000L)
    }

    @Test
    fun getTotalForAppInRange_returnsZeroForNoData() = runTest {
        val total = dao.getTotalForAppInRange("com.nonexistent", "2026-04-01", "2026-04-30")
        assertThat(total).isEqualTo(0L)
    }

    @Test
    fun countEntriesInRange_countsAllApps() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-01", "com.b", "B", 20_000L),
            DailyUsageStatsEntity("2026-04-02", "com.a", "A", 30_000L)
        ))

        assertThat(dao.countEntriesInRange("2026-04-01", "2026-04-02")).isEqualTo(3)
        assertThat(dao.countEntriesInRange("2026-04-03", "2026-04-05")).isEqualTo(0)
    }

    @Test
    fun deleteOlderThan_removesOldEntries() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-01-15", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-03-01", "com.a", "A", 20_000L),
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 30_000L)
        ))

        dao.deleteOlderThan("2026-03-01")

        val remaining = dao.getForAppInRange("com.a", "2026-01-01", "2026-12-31")
        assertThat(remaining).hasSize(2)
        assertThat(remaining[0].dateString).isEqualTo("2026-03-01")
        assertThat(remaining[1].dateString).isEqualTo("2026-04-01")
    }

    @Test
    fun dateStringSortOrder_isLexicographic() = runTest {
        // Verify that BETWEEN works correctly with our date format
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2025-12-31", "com.a", "A", 1L),
            DailyUsageStatsEntity("2026-01-01", "com.a", "A", 2L),
            DailyUsageStatsEntity("2026-01-31", "com.a", "A", 3L),
            DailyUsageStatsEntity("2026-02-01", "com.a", "A", 4L)
        ))

        // Jan only
        val janResults = dao.getForAppInRange("com.a", "2026-01-01", "2026-01-31")
        assertThat(janResults).hasSize(2)

        // Cross-year
        val crossYear = dao.getForAppInRange("com.a", "2025-12-31", "2026-01-01")
        assertThat(crossYear).hasSize(2)
    }

    @Test
    fun deleteAll_removesEverything() = runTest {
        dao.upsertAll(listOf(
            DailyUsageStatsEntity("2026-04-01", "com.a", "A", 10_000L),
            DailyUsageStatsEntity("2026-04-02", "com.b", "B", 20_000L),
            DailyUsageStatsEntity("2026-04-03", "com.c", "C", 30_000L)
        ))

        dao.deleteAll()

        assertThat(dao.countEntriesInRange("2026-01-01", "2026-12-31")).isEqualTo(0)
    }
}
