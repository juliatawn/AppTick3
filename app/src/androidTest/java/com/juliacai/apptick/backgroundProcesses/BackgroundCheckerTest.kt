package com.juliacai.apptick.backgroundProcesses

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@MediumTest
class BackgroundCheckerTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var database: AppTickDatabase
    private lateinit var dao: AppLimitGroupDao
    private lateinit var context: Context

    @Before
    fun setup() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        BackgroundChecker.disableBackgroundLoopForTesting = true
        database = AppTickDatabase.getDatabase(context)
        dao = database.appLimitGroupDao()
        clearAllGroups()
    }

    @After
    @Throws(IOException::class)
    fun tearDown() = runTest {
        clearAllGroups()
        BackgroundChecker.disableBackgroundLoopForTesting = false
    }

    @Test
    @Throws(Exception::class)
    fun testServiceDoesNotBlockWhenTimeIsNotUp() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 1,
            timeRemaining = 3600000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        service.checkAppLimits("com.instagram.android")

        val updatedGroup = dao.getGroup(1)
        assertEquals(group.timeRemaining - 1000, updatedGroup?.timeRemaining)
    }

    @Test
    @Throws(Exception::class)
    fun testServiceBlocksWhenTimeIsUp() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 1,
            timeRemaining = 1000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        service.checkAppLimits("com.instagram.android")

        val updatedGroup = dao.getGroup(1)
        assertEquals(0L, updatedGroup?.timeRemaining)
    }

    @Test
    @Throws(Exception::class)
    fun testServiceTracksTimeAndBlocks() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Social Media",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 60000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        // Simulate app usage for 30 seconds
        for (i in 0..29) {
            service.checkAppLimits("com.instagram.android")
        }

        var updatedGroup = dao.getGroup(1)
        assertEquals(30000L, updatedGroup?.timeRemaining)

        // Simulate app usage for another 30 seconds
        for (i in 0..29) {
            service.checkAppLimits("com.instagram.android")
        }

        updatedGroup = dao.getGroup(1)
        assertEquals(0L, updatedGroup?.timeRemaining)
    }

    @Test
    @Throws(Exception::class)
    fun testPerAppUsageAccumulatesOnlyForForegroundApp() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Two Apps",
            timeHrLimit = 0,
            timeMinLimit = 10,
            limitEach = true,
            timeRemaining = 600000,
            apps = listOf(
                AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"),
                AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        repeat(3) { service.checkAppLimits("com.instagram.android") }
        repeat(2) { service.checkAppLimits("com.google.android.youtube") }

        val updatedGroup = dao.getGroup(1)
        val usage = updatedGroup?.perAppUsage?.associate { it.appPackage to it.usedMillis } ?: emptyMap()

        assertEquals(3000L, usage["com.instagram.android"])
        assertEquals(2000L, usage["com.google.android.youtube"])
    }

    @Test
    @Throws(Exception::class)
    fun testLimitEachUsesPerAppUsageThreshold() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Limit Each",
            timeHrLimit = 0,
            timeMinLimit = 1,
            limitEach = true,
            timeRemaining = 60000,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        repeat(60) { service.checkAppLimits("com.instagram.android") }
        val atLimit = dao.getGroup(1)
        val usedAtLimit = atLimit?.perAppUsage?.firstOrNull { it.appPackage == "com.instagram.android" }?.usedMillis
        assertEquals(60000L, usedAtLimit)
        assertEquals(0L, atLimit?.timeRemaining)

        // Once limit is reached, checker should not increase usage further.
        service.checkAppLimits("com.instagram.android")
        val afterLimit = dao.getGroup(1)
        val usedAfterLimit = afterLimit?.perAppUsage?.firstOrNull { it.appPackage == "com.instagram.android" }?.usedMillis
        assertEquals(60000L, usedAfterLimit)
    }

    private suspend fun clearAllGroups() {
        dao.getAllAppLimitGroupsImmediate().forEach { dao.deleteAppLimitGroup(it) }
    }

    private fun bindService(): BackgroundChecker {
        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        return (binder as BackgroundChecker.LocalBinder).getService()
    }

    // ── Navigate-home-before-block tests ─────────────────────────────────

    @Test
    @Throws(Exception::class)
    fun testNavigateHomeCalledWhenTimeExhausted() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Home Nav Test",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 1_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(2_000L)

        service.checkAppLimits("com.instagram.android")

        assertEquals(
            "navigateHome should be called once when block triggers",
            1, service.navigateHomeCallCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNavigateHomeCalledWhenZeroLimit() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Zero Limit Test",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        service.checkAppLimits("com.instagram.android")

        assertEquals(
            "navigateHome should be called for zero-limit block",
            1, service.navigateHomeCallCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNavigateHomeNotCalledWhenTimeRemaining() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "No Block Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        service.checkAppLimits("com.instagram.android")

        assertEquals(
            "navigateHome should NOT be called when time remains",
            0, service.navigateHomeCallCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNavigateHomeCalledOnOutsideTimeRange() = runTest {
        // Create a group with time range that excludes the current time
        // Use a range from 03:00-03:01 — guaranteed to be outside this range
        // during any normal test execution
        val group = AppLimitGroup(
            id = 1,
            name = "Time Range Block Test",
            timeHrLimit = 1,
            timeMinLimit = 0,
            timeRemaining = 3_600_000L,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            timeRanges = listOf(
                com.juliacai.apptick.groups.TimeRange(
                    startHour = 3,
                    startMinute = 0,
                    endHour = 3,
                    endMinute = 1
                )
            ),
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        service.checkAppLimits("com.instagram.android")

        assertEquals(
            "navigateHome should be called for outside-time-range block",
            1, service.navigateHomeCallCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun testNavigateHomeCalledOnEachBlock() = runTest {
        // Two groups that both block the same app — navigateHome should be called twice
        val group1 = AppLimitGroup(
            id = 1,
            name = "Group A",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        val group2 = AppLimitGroup(
            id = 2,
            name = "Group B",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group1)
        dao.insertAppLimitGroup(group2)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        service.checkAppLimits("com.instagram.android")

        assertTrue(
            "navigateHome should be called at least once across multiple blocking groups",
            service.navigateHomeCallCount >= 1
        )
    }

    // ── Paused group tests ─────────────────────────────────────────────────

    @Test
    @Throws(Exception::class)
    fun testPausedGroupDoesNotDecrementTimeRemaining() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Paused Timer Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            paused = true,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        repeat(10) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        assertEquals(
            "Paused group timeRemaining must not decrement",
            3_600_000L, updated.timeRemaining
        )
        assertTrue(
            "Paused group must remain paused",
            updated.paused
        )
    }

    @Test
    @Throws(Exception::class)
    fun testPausedGroupDoesNotAccumulatePerAppUsage() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Paused Usage Test",
            timeHrLimit = 0,
            timeMinLimit = 10,
            limitEach = true,
            timeRemaining = 600_000L,
            paused = true,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            perAppUsage = listOf(
                com.juliacai.apptick.groups.AppUsageStat("com.instagram.android", 0L)
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        val usage = updated.perAppUsage.firstOrNull { it.appPackage == "com.instagram.android" }
        assertEquals(
            "Paused group perAppUsage must not accumulate",
            0L, usage?.usedMillis ?: -1L
        )
    }

    @Test
    @Throws(Exception::class)
    fun testActiveGroupUpdateDoesNotOverwritePausedGroupState() = runTest {
        // Simulate the race: group is active in cache, user pauses via ViewModel,
        // then BackgroundChecker tick updates the group — must not overwrite paused=true.
        val activeGroup = AppLimitGroup(
            id = 1,
            name = "Race Condition Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            paused = false,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(activeGroup)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        // First tick: group is active, timer decrements
        service.checkAppLimits("com.instagram.android")
        val afterFirstTick = dao.getGroup(1)!!
        assertEquals(3_599_000L, afterFirstTick.timeRemaining)

        // User pauses the group (simulates ViewModel.togglePause)
        dao.updateAppLimitGroup(afterFirstTick.copy(paused = true))
        val afterPause = dao.getGroup(1)!!
        assertTrue("Group should be paused after toggle", afterPause.paused)

        // Next ticks: group is paused, timer must NOT decrement AND paused must stay true
        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val afterTicks = dao.getGroup(1)!!
        assertTrue(
            "Paused state must not be overwritten by BackgroundChecker",
            afterTicks.paused
        )
        assertEquals(
            "Timer must not decrement while paused",
            3_599_000L, afterTicks.timeRemaining
        )
    }

    // ── Screen-off tests ──────────────────────────────────────────────────

    @Test
    @Throws(Exception::class)
    fun testScreenOffDoesNotDecrementTimerWithoutAccessibility() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Screen Off Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)
        service.setScreenOnForTesting(false)

        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        assertEquals(
            "Timer must not decrement when screen is off (no accessibility)",
            3_600_000L, updated.timeRemaining
        )

        service.setScreenOnForTesting(true)
    }

    @Test
    @Throws(Exception::class)
    fun testScreenOffDoesNotDecrementTimerWithAccessibility() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Screen Off Accessibility Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        // Simulate accessibility detecting the app
        AppTickAccessibilityService.simulateForTesting("com.instagram.android", running = true)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)
        service.setScreenOnForTesting(false)

        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        assertEquals(
            "Timer must not decrement when screen is off (with accessibility)",
            3_600_000L, updated.timeRemaining
        )

        service.setScreenOnForTesting(true)
        AppTickAccessibilityService.resetForTesting()
    }

    @Test
    @Throws(Exception::class)
    fun testScreenOffDoesNotAccumulatePerAppUsage() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Screen Off Usage Test",
            timeHrLimit = 0,
            timeMinLimit = 10,
            limitEach = true,
            timeRemaining = 600_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            perAppUsage = listOf(
                com.juliacai.apptick.groups.AppUsageStat("com.instagram.android", 0L)
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)
        service.setScreenOnForTesting(false)

        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        val usage = updated.perAppUsage.firstOrNull { it.appPackage == "com.instagram.android" }
        assertEquals(
            "perAppUsage must not accumulate when screen is off",
            0L, usage?.usedMillis ?: -1L
        )

        service.setScreenOnForTesting(true)
    }

    @Test
    @Throws(Exception::class)
    fun testTimerResumesWhenScreenTurnsBackOn() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Screen Resume Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val service = bindService()
        service.setFixedElapsedForTesting(1_000L)

        // Screen off: no decrement
        service.setScreenOnForTesting(false)
        repeat(5) { service.checkAppLimits("com.instagram.android") }

        val afterOff = dao.getGroup(1)!!
        assertEquals(3_600_000L, afterOff.timeRemaining)

        // Screen on: decrement resumes
        service.setScreenOnForTesting(true)
        repeat(3) { service.checkAppLimits("com.instagram.android") }

        val afterOn = dao.getGroup(1)!!
        assertEquals(
            "Timer should decrement by 3s after screen turns back on",
            3_597_000L, afterOn.timeRemaining
        )
    }

    // ── Daily / Periodic reset integration tests ─────────────────────────

    @Test
    @Throws(Exception::class)
    fun testDailyResetResetsUsageAndAdvancesToMidnight() = runTest {
        // Group with an expired nextResetTime (1 hour ago) and some used time
        val now = System.currentTimeMillis()
        val group = AppLimitGroup(
            id = 1,
            name = "Daily Reset Test",
            timeHrLimit = 1,
            timeMinLimit = 0,
            timeRemaining = 10_000L,  // almost exhausted
            nextResetTime = now - 3_600_000L,  // expired 1 hour ago
            resetMinutes = 0,  // daily mode
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android")),
            perAppUsage = listOf(
                com.juliacai.apptick.groups.AppUsageStat("com.instagram.android", 3_590_000L)
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        // This call should trigger a reset
        service.checkAppLimits("com.other.app")

        val updated = dao.getGroup(1)!!
        // timeRemaining should be restored to the full 1-hour limit
        assertEquals(3_600_000L, updated.timeRemaining)
        // Per-app usage should be zeroed
        val igUsage = updated.perAppUsage.firstOrNull { it.appPackage == "com.instagram.android" }
        assertEquals(0L, igUsage?.usedMillis ?: -1L)
        // nextResetTime should be in the future (midnight tomorrow)
        assertTrue(updated.nextResetTime > System.currentTimeMillis())
    }

    @Test
    @Throws(Exception::class)
    fun testPeriodicResetAdvancesByInterval() = runTest {
        // Group with periodic reset (every 2 hours) and expired nextResetTime
        val now = System.currentTimeMillis()
        val group = AppLimitGroup(
            id = 1,
            name = "Periodic Reset Test",
            timeHrLimit = 0,
            timeMinLimit = 30,
            timeRemaining = 5_000L,
            nextResetTime = now - 60_000L,  // expired 1 minute ago
            resetMinutes = 120,  // periodic: every 2 hours (120 minutes)
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")),
            perAppUsage = listOf(
                com.juliacai.apptick.groups.AppUsageStat("com.google.android.youtube", 1_795_000L)
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        service.checkAppLimits("com.other.app")

        val updated = dao.getGroup(1)!!
        // timeRemaining should be restored to full 30-minute limit
        assertEquals(1_800_000L, updated.timeRemaining)
        // Usage zeroed
        val ytUsage = updated.perAppUsage.firstOrNull { it.appPackage == "com.google.android.youtube" }
        assertEquals(0L, ytUsage?.usedMillis ?: -1L)
        // nextResetTime should be roughly 2 hours from now
        val twoHoursMs = 2 * 60 * 60 * 1000L
        assertTrue(updated.nextResetTime >= now + twoHoursMs - 5000)
        assertTrue(updated.nextResetTime <= now + twoHoursMs + 5000)
    }

    @Test
    @Throws(Exception::class)
    fun testPeriodicCumulativeResetAddsCarryOverOnlyWhenEnabled() = runTest {
        val now = System.currentTimeMillis()
        val group = AppLimitGroup(
            id = 1,
            name = "Periodic Cumulative Reset Test",
            timeHrLimit = 0,
            timeMinLimit = 30,
            timeRemaining = 600_000L, // 10 min carried over before reset
            nextResetTime = now - 60_000L,
            resetMinutes = 120,
            cumulativeTime = true,
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")),
            perAppUsage = listOf(
                com.juliacai.apptick.groups.AppUsageStat("com.google.android.youtube", 1_200_000L)
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1000L)

        service.checkAppLimits("com.other.app")

        val updated = dao.getGroup(1)!!
        // 30 min interval limit + 10 min carry-over = 40 min remaining
        assertEquals(2_400_000L, updated.timeRemaining)
        val ytUsage = updated.perAppUsage.firstOrNull { it.appPackage == "com.google.android.youtube" }
        assertEquals(0L, ytUsage?.usedMillis ?: -1L)
        assertTrue(updated.nextAddTime > now)
    }

    @Test
    @Throws(Exception::class)
    fun testCrossingExpiryInSingleTickBlocksImmediately() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Immediate Block",
            timeHrLimit = 0,
            timeMinLimit = 1,
            limitEach = false,
            timeRemaining = 1_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(2_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("Expected block window when expiry is crossed in one tick", blockedActivity)
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        val updatedGroup = dao.getGroup(1)!!
        assertEquals(0L, updatedGroup.timeRemaining)
    }
}
