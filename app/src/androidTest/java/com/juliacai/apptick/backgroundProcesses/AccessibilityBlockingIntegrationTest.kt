package com.juliacai.apptick.backgroundProcesses

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.block.BlockWindowActivity
import com.juliacai.apptick.data.AppLimitGroupDao
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests verifying that the BackgroundChecker blocking flow works
 * correctly when the AccessibilityService is providing foreground app data
 * versus when it falls back to UsageStatsManager.
 *
 * These tests exercise the full path from checkAppLimits() through to database
 * updates and BlockWindowActivity launch, validating:
 * - Time tracking with accessibility-detected apps
 * - Blocking triggers when limits are reached
 * - Per-app and group-wide limit enforcement
 * - Fallback behavior when accessibility data is unavailable
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@MediumTest
class AccessibilityBlockingIntegrationTest {

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
        // Reset accessibility service state via reflection
        resetAccessibilityState()
    }

    @After
    fun tearDown() = runTest {
        clearAllGroups()
        BackgroundChecker.disableBackgroundLoopForTesting = false
        resetAccessibilityState()
    }

    // ── Accessibility-aware time tracking ─────────────────────────────────

    @Test
    fun testTimeDecrementWorksWithAccessibilityDetectedApp() = runTest {
        // Setup: group with 5-minute limit, full time remaining
        val group = AppLimitGroup(
            id = 1,
            name = "Accessibility Test",
            timeHrLimit = 0,
            timeMinLimit = 5,
            timeRemaining = 300_000L,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        // Simulate accessibility service detecting Amazon as foreground
        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(2_000L)

        // Run several check cycles
        repeat(5) { service.checkAppLimits("com.amazon.mShop.android.shopping") }

        val updated = dao.getGroup(1)!!
        // 5 ticks * 2 seconds = 10 seconds used, 290 seconds remaining
        assertEquals(290_000L, updated.timeRemaining)
    }

    @Test
    fun testBlockingTriggersWhenLimitReachedWithAccessibility() = runTest {
        // Setup: group with only 2 seconds remaining
        val group = AppLimitGroup(
            id = 1,
            name = "Block Test",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 2_000L,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(2_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            // This should exhaust the limit and trigger blocking
            service.checkAppLimits("com.amazon.mShop.android.shopping")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("BlockWindowActivity should launch when limit is reached", blockedActivity)
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        val updated = dao.getGroup(1)!!
        assertEquals(0L, updated.timeRemaining)
    }

    // ── Per-app limit enforcement ─────────────────────────────────────────

    @Test
    fun testPerAppLimitTrackingWithAccessibility() = runTest {
        // Setup: limit-each group with two apps
        val group = AppLimitGroup(
            id = 1,
            name = "Multi App Test",
            timeHrLimit = 0,
            timeMinLimit = 10,
            limitEach = true,
            timeRemaining = 600_000L,
            apps = listOf(
                AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"),
                AppInGroup("Twitter", "com.twitter.android", "com.twitter.android")
            )
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        // Simulate using Amazon (detected via accessibility) for 3 seconds
        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")
        repeat(3) { service.checkAppLimits("com.amazon.mShop.android.shopping") }

        // Simulate switching to Twitter for 2 seconds
        simulateAccessibilityDetection("com.twitter.android")
        repeat(2) { service.checkAppLimits("com.twitter.android") }

        val updated = dao.getGroup(1)!!
        val usage = updated.perAppUsage.associate { it.appPackage to it.usedMillis }
        assertEquals(
            "Amazon should have 3 seconds of usage",
            3_000L, usage["com.amazon.mShop.android.shopping"]
        )
        assertEquals(
            "Twitter should have 2 seconds of usage",
            2_000L, usage["com.twitter.android"]
        )
    }

    // ── Fallback behavior ─────────────────────────────────────────────────

    @Test
    fun testTrackingWorksWithoutAccessibilityService() = runTest {
        // Do NOT call simulateAccessibilityDetection — service is "not running"
        // This tests that the existing UsageStats fallback path still works

        val group = AppLimitGroup(
            id = 1,
            name = "Fallback Test",
            timeHrLimit = 0,
            timeMinLimit = 5,
            timeRemaining = 300_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        // checkAppLimits is called with the foreground app directly,
        // so tracking should work regardless of accessibility state
        repeat(10) { service.checkAppLimits("com.instagram.android") }

        val updated = dao.getGroup(1)!!
        assertEquals(
            "Time should decrement even without accessibility service",
            290_000L, updated.timeRemaining
        )
    }

    @Test
    fun testBlockingWorksWithoutAccessibilityService() = runTest {
        // Accessibility service NOT running — should still block via UsageStats path

        val group = AppLimitGroup(
            id = 1,
            name = "Fallback Block",
            timeHrLimit = 0,
            timeMinLimit = 1,
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
            assertNotNull("BlockWindowActivity should launch even without accessibility", blockedActivity)
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        val updated = dao.getGroup(1)!!
        assertEquals(0L, updated.timeRemaining)
    }

    // ── Paused group should not track ─────────────────────────────────────

    @Test
    fun testPausedGroupDoesNotTrackTimeWithAccessibility() = runTest {
        val group = AppLimitGroup(
            id = 1,
            name = "Paused Group",
            timeHrLimit = 0,
            timeMinLimit = 5,
            timeRemaining = 300_000L,
            paused = true,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        repeat(5) { service.checkAppLimits("com.amazon.mShop.android.shopping") }

        val updated = dao.getGroup(1)!!
        assertEquals(
            "Paused group should not have its time decremented",
            300_000L, updated.timeRemaining
        )
    }

    // ── Dismiss floating window fallback tests ──────────────────────────

    @Test
    fun testDismissFloatingWindowFallsBackToHomeWhenNoServiceInstance() = runTest {
        // Accessibility is "simulated" as running (for foreground detection),
        // but there is no real service instance — tryCloseFloatingWindow returns false,
        // so dismissFloatingWindow should fall back to the home navigation path.
        val group = AppLimitGroup(
            id = 1,
            name = "Dismiss Fallback Test",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 1_000L,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(2_000L)

        service.checkAppLimits("com.amazon.mShop.android.shopping")

        // navigateHomeCallCount increments in dismissFloatingWindow
        assertEquals(
            "dismissFloatingWindow should increment counter even via fallback path",
            1, service.navigateHomeCallCount
        )
        val updated = dao.getGroup(1)!!
        assertEquals(0L, updated.timeRemaining)
    }

    @Test
    fun testDismissFloatingWindowCalledForZeroLimitWithAccessibility() = runTest {
        // Zero-limit group with accessibility running — should still trigger dismiss
        val group = AppLimitGroup(
            id = 1,
            name = "Zero Limit Dismiss",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.amazon.mShop.android.shopping")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull("BlockWindowActivity should launch for zero-limit block", blockedActivity)
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        assertEquals(
            "dismissFloatingWindow should be called for zero-limit block with accessibility",
            1, service.navigateHomeCallCount
        )
    }

    @Test
    fun testDismissFloatingWindowNotCalledWhenTimeRemains() = runTest {
        // Plenty of time remaining — dismissFloatingWindow should NOT be called
        val group = AppLimitGroup(
            id = 1,
            name = "No Dismiss Test",
            timeHrLimit = 1,
            timeRemaining = 3_600_000L,
            apps = listOf(AppInGroup("Amazon", "com.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        simulateAccessibilityDetection("com.amazon.mShop.android.shopping")

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        service.checkAppLimits("com.amazon.mShop.android.shopping")

        assertEquals(
            "dismissFloatingWindow should NOT be called when time remains",
            0, service.navigateHomeCallCount
        )
    }

    // ── Block screen launches before floating window dismissal ──────────

    @Test
    fun testBlockScreenLaunchesForRegularFullscreenApp() = runTest {
        // A regular fullscreen app should always trigger the block screen (not home navigation).
        val group = AppLimitGroup(
            id = 1,
            name = "Fullscreen Block Test",
            timeHrLimit = 0,
            timeMinLimit = 1,
            timeRemaining = 1_000L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        // Simulate accessibility detecting a fullscreen (non-floating) app
        AppTickAccessibilityService.simulateForTesting(
            "com.instagram.android", running = true, floating = false
        )

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(2_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull(
                "BlockWindowActivity should launch for fullscreen app being blocked",
                blockedActivity
            )
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        val updated = dao.getGroup(1)!!
        assertEquals(0L, updated.timeRemaining)
    }

    @Test
    fun testBlockScreenLaunchesOnEveryCheckCycle() = runTest {
        // When the app is blocked and user returns to it, each check cycle should
        // re-launch the block screen to persist the block.
        val group = AppLimitGroup(
            id = 1,
            name = "Persistent Block Test",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "com.instagram.android"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        AppTickAccessibilityService.simulateForTesting(
            "com.instagram.android", running = true, floating = false
        )

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // First check cycle — should block
        val monitor1 = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")
            val activity1 = instrumentation.waitForMonitorWithTimeout(monitor1, 3_000L)
            assertNotNull("First block screen should appear", activity1)
            activity1?.finish()
        } finally {
            instrumentation.removeMonitor(monitor1)
        }

        // Second check cycle — should re-block (persistent)
        val monitor2 = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.instagram.android")
            val activity2 = instrumentation.waitForMonitorWithTimeout(monitor2, 3_000L)
            assertNotNull("Block screen should re-launch on next check cycle", activity2)
            activity2?.finish()
        } finally {
            instrumentation.removeMonitor(monitor2)
        }

        // dismissFloatingWindow should be called each time
        assertEquals(
            "dismissFloatingWindow should be called for each block",
            2, service.navigateHomeCallCount
        )
    }

    @Test
    fun testDismissCalledAfterBlockScreenForFloatingApp() = runTest {
        // When a floating app is blocked, the block screen should still launch
        // and dismissFloatingWindow should be called.
        val group = AppLimitGroup(
            id = 1,
            name = "Floating Block Test",
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 0L,
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube"))
        ).toEntity()
        dao.insertAppLimitGroup(group)

        // Simulate a floating (PiP) YouTube window
        AppTickAccessibilityService.simulateForTesting(
            "com.google.android.youtube", running = true, floating = true
        )

        val intent = Intent(context, BackgroundChecker::class.java)
        val binder = serviceRule.bindService(intent)
        val service = (binder as BackgroundChecker.LocalBinder).getService()
        service.setFixedElapsedForTesting(1_000L)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(BlockWindowActivity::class.java.name, null, false)
        try {
            service.checkAppLimits("com.google.android.youtube")

            val blockedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000L)
            assertNotNull(
                "BlockWindowActivity should launch even for floating apps",
                blockedActivity
            )
            blockedActivity?.finish()
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        assertEquals(
            "dismissFloatingWindow should be called for floating app block",
            1, service.navigateHomeCallCount
        )
    }

    // ── Visible apps / PiP bubble tracking ───────────────────────────────

    @Test
    fun testVisiblePackagesIncludesPiPApp() = runTest {
        // When a PiP app is visible alongside the foreground app,
        // getVisiblePackages should return both.
        val visibleApps = setOf("com.android.launcher3", "com.google.android.youtube")
        AppTickAccessibilityService.simulateForTesting(
            "com.android.launcher3", running = true, floating = false,
            visiblePackages = visibleApps
        )

        val packages = AppTickAccessibilityService.getVisiblePackages()
        assertTrue(
            "Visible packages should include PiP YouTube",
            packages.contains("com.google.android.youtube")
        )
        assertTrue(
            "Visible packages should include foreground app",
            packages.contains("com.android.launcher3")
        )
    }

    // ── Helper methods ────────────────────────────────────────────────────

    private suspend fun clearAllGroups() {
        dao.getAllAppLimitGroupsImmediate().forEach { dao.deleteAppLimitGroup(it) }
    }

    /**
     * Simulates the AccessibilityService detecting a foreground app.
     * Mimics what onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED) would do.
     */
    private fun simulateAccessibilityDetection(packageName: String) {
        AppTickAccessibilityService.simulateForTesting(packageName, running = true)
    }

    /**
     * Resets accessibility service companion state to defaults (not running).
     */
    private fun resetAccessibilityState() {
        AppTickAccessibilityService.resetForTesting()
    }
}
