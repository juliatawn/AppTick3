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

    // ── tryCloseFloatingWindow tests ────────────────────────────────────────

    @Test
    fun `tryCloseFloatingWindow returns NOT_FLOATING when service instance is null`() {
        // Default state: no instance available (service never connected)
        assertEquals(
            "Should return NOT_FLOATING when no service instance is available",
            AppTickAccessibilityService.FloatingCloseResult.NOT_FLOATING,
            AppTickAccessibilityService.tryCloseFloatingWindow("com.example.blocked")
        )
    }

    @Test
    fun `tryCloseFloatingWindow returns NOT_FLOATING even when simulated as running`() {
        // simulateForTesting sets isRunning=true but does NOT set the instance reference,
        // so tryCloseFloatingWindow should still return NOT_FLOATING (no real service to perform BACK)
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)

        assertEquals(
            "Should return NOT_FLOATING when service is simulated but no real instance exists",
            AppTickAccessibilityService.FloatingCloseResult.NOT_FLOATING,
            AppTickAccessibilityService.tryCloseFloatingWindow("com.example.app")
        )
    }

    @Test
    fun `tryCloseFloatingWindow returns NOT_FLOATING after resetForTesting`() {
        // Ensure resetForTesting clears the instance reference
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true)
        AppTickAccessibilityService.resetForTesting()

        assertEquals(
            "Should return NOT_FLOATING after resetForTesting clears instance",
            AppTickAccessibilityService.FloatingCloseResult.NOT_FLOATING,
            AppTickAccessibilityService.tryCloseFloatingWindow("com.example.app")
        )
    }

    // ── isCurrentAppFloating state tests ────────────────────────────────────

    @Test
    fun `isCurrentAppFloating defaults to false`() {
        assertFalse(
            "isCurrentAppFloating should default to false",
            AppTickAccessibilityService.isCurrentAppFloating
        )
    }

    @Test
    fun `isCurrentAppFloating is true when simulated as floating`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true, floating = true)
        assertTrue(
            "isCurrentAppFloating should be true when simulated as floating",
            AppTickAccessibilityService.isCurrentAppFloating
        )
    }

    @Test
    fun `isCurrentAppFloating is false when simulated as fullscreen`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true, floating = false)
        assertFalse(
            "isCurrentAppFloating should be false for fullscreen apps",
            AppTickAccessibilityService.isCurrentAppFloating
        )
    }

    @Test
    fun `isCurrentAppFloating resets to false on resetForTesting`() {
        AppTickAccessibilityService.simulateForTesting("com.example.app", running = true, floating = true)
        assertTrue(AppTickAccessibilityService.isCurrentAppFloating)

        AppTickAccessibilityService.resetForTesting()
        assertFalse(
            "isCurrentAppFloating should be false after reset",
            AppTickAccessibilityService.isCurrentAppFloating
        )
    }

    // ── getVisiblePackages tests ─────────────────────────────────────────────

    @Test
    fun `getVisiblePackages returns empty when service is not running`() {
        AppTickAccessibilityService.simulateForTesting(
            "com.example.app", running = false,
            visiblePackages = setOf("com.example.app")
        )
        assertEquals(
            "Should return empty set when service is not running",
            emptySet<String>(),
            AppTickAccessibilityService.getVisiblePackages()
        )
    }

    @Test
    fun `getVisiblePackages returns empty when data is stale`() {
        AppTickAccessibilityService.simulateForTesting(
            "com.example.app", running = true,
            visiblePackages = setOf("com.example.app", "com.example.other")
        )
        AppTickAccessibilityService.setLastUpdateTimeForTesting(System.currentTimeMillis() - 15_000L)
        assertEquals(
            "Should return empty set when data is stale",
            emptySet<String>(),
            AppTickAccessibilityService.getVisiblePackages()
        )
    }

    @Test
    fun `getVisiblePackages returns packages when running and fresh`() {
        val visible = setOf("com.example.app", "com.example.pip")
        AppTickAccessibilityService.simulateForTesting(
            "com.example.app", running = true,
            visiblePackages = visible
        )
        assertEquals(
            "Should return visible packages for split-screen detection",
            visible,
            AppTickAccessibilityService.getVisiblePackages()
        )
    }

    @Test
    fun `getVisiblePackages resets to empty on resetForTesting`() {
        AppTickAccessibilityService.simulateForTesting(
            "com.example.app", running = true,
            visiblePackages = setOf("com.example.app", "com.example.other")
        )
        AppTickAccessibilityService.resetForTesting()
        assertEquals(
            "Should return empty set after reset",
            emptySet<String>(),
            AppTickAccessibilityService.getVisiblePackages()
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
