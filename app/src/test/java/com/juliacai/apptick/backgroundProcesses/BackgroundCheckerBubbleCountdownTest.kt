package com.juliacai.apptick.backgroundProcesses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCheckerBubbleCountdownTest {
    private val subject = BackgroundChecker()

    @Test
    fun formatBubbleCountdown_underOneMinute_showsSeconds() {
        assertEquals("00:59", subject.formatBubbleCountdown(59_000L))
        assertEquals("00:01", subject.formatBubbleCountdown(1_000L))
        assertEquals("00:00", subject.formatBubbleCountdown(0L))
    }

    @Test
    fun formatBubbleCountdown_exactlyOneMinute_showsSixtySecondsBoundary() {
        assertEquals("01:00", subject.formatBubbleCountdown(60_000L))
    }

    @Test
    fun formatBubbleCountdown_aboveOneMinute_showsHoursMinutes() {
        assertEquals("00:01", subject.formatBubbleCountdown(61_000L))
        assertEquals("01:30", subject.formatBubbleCountdown(5_400_000L))
    }

    @Test
    fun shouldHideFloatingBubbleForForegroundApp_hidesForNullAndAppTickOnly() {
        assertTrue(
            BackgroundChecker.shouldHideFloatingBubbleForForegroundApp(
                currentApp = null,
                appTickPackage = "com.juliacai.apptick"
            )
        )
        assertTrue(
            BackgroundChecker.shouldHideFloatingBubbleForForegroundApp(
                currentApp = "com.juliacai.apptick",
                appTickPackage = "com.juliacai.apptick"
            )
        )
        assertFalse(
            BackgroundChecker.shouldHideFloatingBubbleForForegroundApp(
                currentApp = "com.android.chrome",
                appTickPackage = "com.juliacai.apptick"
            )
        )
    }
}
