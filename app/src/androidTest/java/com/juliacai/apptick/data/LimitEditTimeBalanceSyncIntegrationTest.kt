package com.juliacai.apptick.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.newAppLimit.normalizeGroupForPersistence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Integration tests verifying that editing an app limit syncs the time balance:
 * - timeRemaining resets to the new limit
 * - perAppUsage is cleared
 * - nextResetTime is recalculated for periodic groups
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class LimitEditTimeBalanceSyncIntegrationTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppTickDatabase::class.java
        ).build()
        dao = database.appLimitGroupDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun editLimit_fullRoundTrip_syncsBalanceAndClearsUsage() = runTest {
        // Insert a group with 5-min limit, partially used (1 min left, 4 min used)
        val original = AppLimitGroupEntity(
            id = 1,
            name = "Social",
            timeHrLimit = 0,
            timeMinLimit = 5,
            timeRemaining = 60_000L, // 1 min left
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            perAppUsage = listOf(AppUsageStat("com.instagram.android", 240_000L)),
            nextResetTime = System.currentTimeMillis() + 3_600_000L
        )
        dao.insertAppLimitGroup(original)

        // Simulate user editing: fetch from DB, change limit to 30 min
        val previousEntity = dao.getGroup(1)!!
        val previousDomain = previousEntity.toDomainModel()

        val editedDomain = previousDomain.copy(
            timeHrLimit = 0,
            timeMinLimit = 30
        )

        // Normalize with previous group for limit-change detection
        val allowedPackages = editedDomain.apps.map { it.appPackage }.toSet()
        val normalizedUsage = editedDomain.perAppUsage
            .filter { it.appPackage in allowedPackages }
            .map { it.copy(usedMillis = maxOf(0L, it.usedMillis)) }
        val normalized = normalizeGroupForPersistence(
            group = editedDomain,
            normalizedUsage = normalizedUsage,
            previousGroup = previousDomain
        )

        // Persist the edit
        dao.updateAppLimitGroup(normalized.toEntity())

        // Verify
        val updated = dao.getGroup(1)!!
        assertEquals("timeRemaining should be full new limit",
            1_800_000L, updated.timeRemaining) // 30 min
        assertTrue("perAppUsage should be cleared",
            updated.perAppUsage.isEmpty())
    }

    @Test
    fun editLimit_periodicReset_recalculatesNextResetTime() = runTest {
        val oldResetTime = System.currentTimeMillis() + 7_200_000L // 2 hours away
        val original = AppLimitGroupEntity(
            id = 1,
            name = "Periodic",
            timeHrLimit = 0,
            timeMinLimit = 10,
            resetMinutes = 120,
            timeRemaining = 60_000L,
            nextResetTime = oldResetTime,
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")),
            perAppUsage = listOf(AppUsageStat("com.google.android.youtube", 540_000L))
        )
        dao.insertAppLimitGroup(original)

        val previousDomain = dao.getGroup(1)!!.toDomainModel()
        val editedDomain = previousDomain.copy(timeMinLimit = 30)

        val before = System.currentTimeMillis()
        val normalized = normalizeGroupForPersistence(
            group = editedDomain,
            previousGroup = previousDomain
        )
        val after = System.currentTimeMillis()

        dao.updateAppLimitGroup(normalized.toEntity())

        val updated = dao.getGroup(1)!!
        // nextResetTime should be ~2 hours from now (recalculated), not the old value
        val twoHoursMs = 120 * 60 * 1000L
        assertTrue("nextResetTime should be recalculated from now",
            updated.nextResetTime >= before + twoHoursMs - 100)
        assertTrue("nextResetTime should be recalculated from now",
            updated.nextResetTime <= after + twoHoursMs + 100)
        assertTrue("nextResetTime should differ from old value",
            updated.nextResetTime != oldResetTime)
    }

    @Test
    fun noLimitChange_preservesExistingBalance() = runTest {
        val resetTime = System.currentTimeMillis() + 3_600_000L
        val original = AppLimitGroupEntity(
            id = 1,
            name = "Social",
            timeHrLimit = 0,
            timeMinLimit = 10,
            timeRemaining = 300_000L, // 5 min left
            nextResetTime = resetTime,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            perAppUsage = listOf(AppUsageStat("com.instagram.android", 300_000L))
        )
        dao.insertAppLimitGroup(original)

        val previousDomain = dao.getGroup(1)!!.toDomainModel()
        // Edit name only — no limit change
        val editedDomain = previousDomain.copy(name = "Renamed")

        val normalized = normalizeGroupForPersistence(
            group = editedDomain,
            normalizedUsage = editedDomain.perAppUsage,
            previousGroup = previousDomain
        )

        dao.updateAppLimitGroup(normalized.toEntity())

        val updated = dao.getGroup(1)!!
        assertEquals("timeRemaining should be preserved when limit unchanged",
            300_000L, updated.timeRemaining)
        assertEquals("perAppUsage should be preserved when limit unchanged",
            1, updated.perAppUsage.size)
        assertEquals("nextResetTime should be preserved when limit unchanged",
            resetTime, updated.nextResetTime)
    }
}
