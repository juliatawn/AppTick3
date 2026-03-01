package com.juliacai.apptick.backgroundProcesses

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AppTickAccessibilityService] companion object logic.
 *
 * These tests verify the foreground package detection helper behavior including
 * staleness checks, service-running state gating, and null-safety.
 */
class AppTickAccessibilityServiceTest {

    @Before
    fun setUp() {
        AppTickAccessibilityService.resetForTesting()
    }

    @After
    fun tearDown() {
        AppTickAccessibilityService.resetForTesting()
    }

    // ── getForegroundPackage() tests ────────────────────────────────────────

    @Test
    fun `getForegroundPackage returns null when service is not running`() {
        // Service is not running but data exists
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = false)

        assertNull(
            "Should return null when service is not running",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns null when data is stale`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)
        // Set update time to 15 seconds ago (exceeds MAX_STALENESS_MS of 10s)
        AppTickAccessibilityService.setLastUpdateTimeForTesting(System.currentTimeMillis() - 15_000L)

        assertNull(
            "Should return null when data is older than staleness threshold",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns package when running and fresh`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)

        assertEquals(
            "Should return the current foreground package",
            "com.example.app",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns null when package is null`() {
        AppTickAccessibilityService.simulateForTesting(null, running = true)

        assertNull(
            "Should return null when no foreground package has been set",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns data at exactly staleness boundary`() {
        AppTickAccessibilityService.simulateForTesting("com.example.boundary", running = true)
        // Set to 9 seconds ago (just under 10s threshold)
        AppTickAccessibilityService.setLastUpdateTimeForTesting(System.currentTimeMillis() - 9_000L)

        assertEquals(
            "Should return package when data is just under staleness threshold",
            "com.example.boundary",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns null past staleness threshold`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)
        // Set to 10.001 seconds ago (just past threshold)
        AppTickAccessibilityService.setLastUpdateTimeForTesting(System.currentTimeMillis() - 10_001L)

        assertNull(
            "Should return null when data is past the staleness threshold",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage reflects latest package after update`() {
        AppTickAccessibilityService.simulateForTesting("com.first.app", running = true)
        assertEquals("com.first.app", AppTickAccessibilityService.getForegroundPackage())

        // Simulate switching to a different app
        AppTickAccessibilityService.simulateForTesting("com.second.app", running = true)

        assertEquals(
            "Should reflect the most recent foreground package",
            "com.second.app",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    // ── isRunning state tests ───────────────────────────────────────────────

    @Test
    fun `isRunning defaults to false`() {
        assertFalse(
            "isRunning should default to false",
            AppTickAccessibilityService.isRunning
        )
    }

    @Test
    fun `isRunning reflects connected state`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)
        assertTrue("isRunning should be true after service connects", AppTickAccessibilityService.isRunning)

        AppTickAccessibilityService.resetForTesting()
        assertFalse("isRunning should be false after reset", AppTickAccessibilityService.isRunning)
    }

    // ── Service destroy clears state ────────────────────────────────────────

    @Test
    fun `clearing state on destroy makes getForegroundPackage return null`() {
        // Simulate an active service with fresh data
        AppTickAccessibilityService.simulateForTesting("com.example.active", running = true)
        assertEquals("com.example.active", AppTickAccessibilityService.getForegroundPackage())

        // Simulate onDestroy behavior
        AppTickAccessibilityService.resetForTesting()

        assertNull(
            "After service destroy, getForegroundPackage should return null",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    // ── Fallback scenario tests ─────────────────────────────────────────────

    @Test
    fun `getForegroundPackage returns null for BackgroundChecker fallback when service never started`() {
        // Default state: never started, no data
        assertNull(
            "Should return null so BackgroundChecker falls back to UsageStats",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }

    @Test
    fun `getForegroundPackage returns null when service was killed without onDestroy`() {
        // After process restart, all companion statics reset to defaults
        // This is the default setUp state (resetForTesting called)
        assertNull(
            "After process kill and restart, should return null for fallback",
            AppTickAccessibilityService.getForegroundPackage()
        )
    }
}
