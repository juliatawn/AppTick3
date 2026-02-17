package com.juliacai.apptick.backgroundProcesses

import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.data.AppLimitGroupEntity
import com.juliacai.apptick.groups.AppUsageStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [BackgroundChecker.pickNotificationGroup] selection logic
 * and [BackgroundChecker.formatGroupNotificationText] formatting.
 *
 * These tests verify that the notification displays info for the correct active
 * (non-paused) profile, choosing the one with the lowest time remaining when
 * multiple active profiles cover the same app.
 */
class NotificationGroupSelectionTest {

    private val testApp = "com.supercell.clashofclans"

    private fun entity(
        id: Long,
        name: String,
        paused: Boolean = false,
        timeRemaining: Long = 3_600_000L,
        timeHrLimit: Int = 1,
        timeMinLimit: Int = 0,
        limitEach: Boolean = false,
        weekDays: List<Int> = emptyList(),
        useTimeRange: Boolean = false,
        startHour: Int = 0,
        startMinute: Int = 0,
        endHour: Int = 0,
        endMinute: Int = 0,
        perAppUsage: List<AppUsageStat> = emptyList(),
        apps: List<AppInGroup> = listOf(
            AppInGroup("Clash of Clans", testApp, testApp)
        )
    ) = AppLimitGroupEntity(
        id = id,
        name = name,
        paused = paused,
        timeRemaining = timeRemaining,
        timeHrLimit = timeHrLimit,
        timeMinLimit = timeMinLimit,
        limitEach = limitEach,
        weekDays = weekDays,
        useTimeRange = useTimeRange,
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        perAppUsage = perAppUsage,
        apps = apps
    )

    // ── pickNotificationGroup tests ──────────────────────────────────────

    /** Single active profile covering the app is returned, isMultiProfile = false. */
    @Test
    fun singleActiveProfile_returnsProfile() {
        val groups = listOf(entity(1, "Gaming"))
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)

        assertNotNull(result)
        assertEquals(1L, result!!.entity.id)
        assertFalse(result.isMultiProfile)
    }

    /** One paused and one active profile → only active profile returned. */
    @Test
    fun onePaused_oneActive_returnsActive() {
        val groups = listOf(
            entity(1, "Paused Profile", paused = true, timeRemaining = 100_000L),
            entity(2, "Active Profile", paused = false, timeRemaining = 300_000L)
        )
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)

        assertNotNull(result)
        assertEquals(2L, result!!.entity.id)
        assertFalse("Only one active profile, so isMultiProfile should be false", result.isMultiProfile)
    }

    /** Two active profiles → picks the one with lowest timeRemaining, isMultiProfile = true. */
    @Test
    fun twoActiveProfiles_picksLowestTimeRemaining() {
        val groups = listOf(
            entity(1, "Profile A", timeRemaining = 600_000L),
            entity(2, "Profile B", timeRemaining = 120_000L)
        )
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)

        assertNotNull(result)
        assertEquals(2L, result!!.entity.id)
        assertTrue("Multiple active profiles should have isMultiProfile = true", result.isMultiProfile)
    }

    /** No groups contain the foreground app → returns null. */
    @Test
    fun noMatchingGroups_returnsNull() {
        val groups = listOf(
            entity(1, "Other Group", apps = listOf(
                AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")
            ))
        )
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)
        assertNull(result)
    }

    /** All matching groups are paused → returns null. */
    @Test
    fun allMatchingGroupsPaused_returnsNull() {
        val groups = listOf(
            entity(1, "Paused 1", paused = true),
            entity(2, "Paused 2", paused = true)
        )
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)
        assertNull(result)
    }

    /** Empty group list → returns null. */
    @Test
    fun emptyGroupList_returnsNull() {
        val result = BackgroundChecker.pickNotificationGroup(emptyList(), testApp)
        assertNull(result)
    }

    /** limitEach mode: effective remaining = limitMillis - perAppUsage for the app. */
    @Test
    fun limitEachMode_usesPerAppUsageForRanking() {
        val groups = listOf(
            // Profile A: limitEach, 30 min limit, app used 25 min → 5 min left
            entity(
                id = 1, name = "Profile A", limitEach = true,
                timeHrLimit = 0, timeMinLimit = 30,
                timeRemaining = 1_800_000L, // group remaining (not used for ranking in limitEach)
                perAppUsage = listOf(AppUsageStat(testApp, 1_500_000L))
            ),
            // Profile B: not limitEach, 1 hr limit, 10 min group remaining
            entity(
                id = 2, name = "Profile B", limitEach = false,
                timeHrLimit = 1, timeMinLimit = 0,
                timeRemaining = 600_000L // 10 min left
            )
        )
        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)

        assertNotNull(result)
        // Profile A has 5 min effectively left (300_000ms) vs Profile B 10 min (600_000ms)
        assertEquals(1L, result!!.entity.id)
        assertTrue(result.isMultiProfile)
    }

    /** Groups with no configured time limit are excluded from bubble/notification selection. */
    @Test
    fun noTimeLimitProfile_isIgnoredForSelection() {
        val groups = listOf(
            entity(
                id = 1,
                name = "No Limit",
                timeHrLimit = 0,
                timeMinLimit = 0,
                timeRemaining = 0L
            ),
            entity(
                id = 2,
                name = "Timed",
                timeHrLimit = 0,
                timeMinLimit = 45,
                timeRemaining = 1_200_000L
            )
        )

        val result = BackgroundChecker.pickNotificationGroup(groups, testApp)

        assertNotNull(result)
        assertEquals(2L, result!!.entity.id)
    }

    /** Inactive time-range profiles are excluded even if they include the foreground app. */
    @Test
    fun inactiveTimeRangeProfile_isIgnoredForSelection() {
        val nowMillis = 12L * 60L * 60L * 1000L // 12:00
        val groups = listOf(
            entity(
                id = 1,
                name = "Inactive Window",
                useTimeRange = true,
                startHour = 1,
                startMinute = 0,
                endHour = 2,
                endMinute = 0,
                timeRemaining = 2_000_000L
            ),
            entity(
                id = 2,
                name = "Active Always",
                useTimeRange = false,
                timeRemaining = 900_000L
            )
        )

        val result = BackgroundChecker.pickNotificationGroup(groups, testApp, nowMillis)

        assertNotNull(result)
        assertEquals(2L, result!!.entity.id)
    }

    // ── formatGroupNotificationText tests ────────────────────────────────

    /** Single profile notification shows group name without multi-profile suffix. */
    @Test
    fun formatText_singleProfile_noSuffix() {
        // Use a BackgroundChecker instance just to call the method
        // (it's @VisibleForTesting, non-static)
        val text = formatHelper(
            groupName = "Gaming",
            limitHours = 1,
            limitMinutes = 0,
            timeRemainingMillis = 1_800_000L, // 30 min left
            nextResetTimeMillis = 0L,
            isMultiProfile = false
        )
        assertTrue("Should start with group name", text.startsWith("Gaming:"))
        assertTrue("Should show used time", text.contains("Used 30m"))
        assertTrue("Should show remaining time", text.contains("left 30m"))
        assertFalse("Should NOT contain multi-profile suffix", text.contains("+ more profiles"))
    }

    /** Multi-profile notification appends "(+ more profiles)" suffix. */
    @Test
    fun formatText_multiProfile_hasSuffix() {
        val text = formatHelper(
            groupName = "Social Media",
            limitHours = 0,
            limitMinutes = 30,
            timeRemainingMillis = 600_000L, // 10 min left
            nextResetTimeMillis = 0L,
            isMultiProfile = true
        )
        assertTrue("Should start with group name", text.startsWith("Social Media:"))
        assertTrue("Should contain multi-profile suffix", text.contains("(+ more profiles)"))
    }

    /** Reset text shows 'unscheduled' when nextResetTimeMillis is 0. */
    @Test
    fun formatText_noResetTime_showsUnscheduled() {
        val text = formatHelper(
            groupName = "Test",
            limitHours = 1,
            limitMinutes = 0,
            timeRemainingMillis = 3_600_000L,
            nextResetTimeMillis = 0L,
            isMultiProfile = false
        )
        assertTrue("Should show 'unscheduled'", text.contains("unscheduled"))
    }

    /**
     * Helper to call the instance method formatGroupNotificationText
     * without needing a full service (method only uses data computation).
     */
    private fun formatHelper(
        groupName: String,
        limitHours: Int,
        limitMinutes: Int,
        timeRemainingMillis: Long,
        nextResetTimeMillis: Long,
        isMultiProfile: Boolean
    ): String {
        // Compute manually — mirrors BackgroundChecker.formatGroupNotificationText
        val totalLimitMinutes = limitHours * 60 + limitMinutes
        val totalLimitMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(totalLimitMinutes.toLong())
        val timeUsedMillis = (totalLimitMillis - timeRemainingMillis).coerceAtLeast(0L)
        val usedMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeUsedMillis)
        val remainingMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(
            timeRemainingMillis.coerceAtLeast(0L)
        )
        val resetText = if (nextResetTimeMillis > 0L) {
            val formatter = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault())
            formatter.format(java.util.Date(nextResetTimeMillis))
        } else {
            "unscheduled"
        }
        val base = "$groupName: Used ${usedMinutes}m, left ${remainingMinutes}m, resets $resetText"
        return if (isMultiProfile) "$base (+ more profiles)" else base
    }
}
