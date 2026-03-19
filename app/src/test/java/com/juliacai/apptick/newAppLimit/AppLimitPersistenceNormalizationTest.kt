package com.juliacai.apptick.newAppLimit

import com.juliacai.apptick.TimeManager
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

class AppLimitPersistenceNormalizationTest {

    @Test
    fun duplicateGroup_resetsPersistenceFields_forImmediateSaveAsNew() {
        val original = AppLimitGroup(
            id = 42L,
            paused = true,
            timeHrLimit = 1,
            timeMinLimit = 30,
            timeRemaining = 5_000L,
            nextResetTime = System.currentTimeMillis() + 60_000L,
            nextAddTime = System.currentTimeMillis() + 60_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 4_000L))
        )

        val duplicated = duplicateGroupForCreation(original)
        val normalized = normalizeGroupForPersistence(duplicated)

        assertEquals(0L, duplicated.id)
        assertFalse(duplicated.paused)
        assertTrue(duplicated.perAppUsage.isEmpty())
        assertEquals(0L, duplicated.timeRemaining)
        assertEquals(5_400_000L, normalized.timeRemaining)
    }

    @Test
    fun duplicateGroup_preservesConfigurationFields() {
        val original = AppLimitGroup(
            id = 42L,
            name = "Focus",
            timeHrLimit = 2,
            timeMinLimit = 15,
            limitEach = true,
            resetMinutes = 90,
            weekDays = listOf(1, 3, 5),
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")),
            useTimeRange = true,
            blockOutsideTimeRange = true,
            cumulativeTime = true
        )

        val duplicated = duplicateGroupForCreation(original)

        assertEquals("Focus", duplicated.name)
        assertEquals(2, duplicated.timeHrLimit)
        assertEquals(15, duplicated.timeMinLimit)
        assertTrue(duplicated.limitEach)
        assertEquals(90, duplicated.resetMinutes)
        assertEquals(listOf(1, 3, 5), duplicated.weekDays)
        assertEquals(1, duplicated.apps.size)
        assertEquals("com.google.android.youtube", duplicated.apps.first().appPackage)
        assertTrue(duplicated.useTimeRange)
        assertTrue(duplicated.blockOutsideTimeRange)
        assertTrue(duplicated.cumulativeTime)
    }

    @Test
    fun newGroupWithTimeLimit_initializesTimeRemainingFromLimit() {
        val group = AppLimitGroup(
            id = 0L,
            timeHrLimit = 0,
            timeMinLimit = 2,
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "com.google.android.youtube")),
            timeRemaining = 0L,
            perAppUsage = emptyList()
        )

        val normalized = normalizeGroupForPersistence(group)

        assertEquals(120_000L, normalized.timeRemaining)
    }

    @Test
    fun existingGroupWithCarryOver_capsTimeRemainingAtLimit() {
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 2,
            timeRemaining = 300_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 30_000L))
        )

        val normalized = normalizeGroupForPersistence(group)

        assertEquals(120_000L, normalized.timeRemaining)
    }

    @Test
    fun noTimeLimit_forcesTimeRemainingToZero() {
        val group = AppLimitGroup(
            id = 0L,
            timeHrLimit = 0,
            timeMinLimit = 0,
            timeRemaining = 10_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 5_000L))
        )

        val normalized = normalizeGroupForPersistence(group)

        assertEquals(0L, normalized.timeRemaining)
    }

    // ── nextResetTime normalization tests ─────────────────────────────────

    @Test
    fun newGroup_dailyMode_nextResetTimeSetToMidnight() {
        // New group with no periodic reset — should get midnight tomorrow
        val group = AppLimitGroup(
            id = 0L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0,
            nextResetTime = 0L
        )

        val normalized = normalizeGroupForPersistence(group)

        // Verify it's midnight tomorrow
        val cal = Calendar.getInstance().apply { timeInMillis = normalized.nextResetTime }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertTrue(normalized.nextResetTime > System.currentTimeMillis())
    }

    @Test
    fun newGroup_periodicMode_nextResetTimeSetToNowPlusInterval() {
        // Periodic reset of 2 hours — nextResetTime should be ~2 hours from now
        val before = System.currentTimeMillis()
        val group = AppLimitGroup(
            id = 0L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 2 * 60, // 2 hours in minutes
            nextResetTime = 0L
        )

        val normalized = normalizeGroupForPersistence(group)
        val after = System.currentTimeMillis()

        val twoHoursMs = 2 * 60 * 60 * 1000L
        assertTrue(normalized.nextResetTime >= before + twoHoursMs - 100)
        assertTrue(normalized.nextResetTime <= after + twoHoursMs + 100)
    }

    @Test
    fun existingGroup_futureNextResetTime_isPreserved() {
        // Group already has a valid future reset time — should not be overwritten
        val futureTime = System.currentTimeMillis() + 3_600_000L  // 1 hour from now
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0,
            nextResetTime = futureTime
        )

        val normalized = normalizeGroupForPersistence(group)

        assertEquals(futureTime, normalized.nextResetTime)
    }

    @Test
    fun existingGroup_expiredNextResetTime_getsNewMidnight() {
        // Group has expired reset time (in the past) — should be updated
        val pastTime = System.currentTimeMillis() - 3_600_000L  // 1 hour ago
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0,
            nextResetTime = pastTime
        )

        val normalized = normalizeGroupForPersistence(group)

        assertTrue(normalized.nextResetTime > System.currentTimeMillis())
    }

    // ── Limit-edit time balance sync tests ────────────────────────────────

    @Test
    fun editLimit_increasingLimit_resetsTimeRemainingToNewLimit() {
        // User had 1 min left on a 2-min limit, edits to 30 min → time left = 30 min
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 2,
            timeRemaining = 60_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 60_000L))
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 30
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(1_800_000L, normalized.timeRemaining) // 30 min in millis
    }

    @Test
    fun editLimit_decreasingLimit_resetsTimeRemainingToNewLimit() {
        // User had 50 min left on a 1-hr limit, edits down to 10 min → time left = 10 min
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            timeRemaining = 3_000_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 600_000L))
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 10
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(600_000L, normalized.timeRemaining) // 10 min in millis
    }

    @Test
    fun editLimit_clearsPerAppUsageOnLimitChange() {
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 5,
            limitEach = true,
            timeRemaining = 60_000L,
            perAppUsage = listOf(
                AppUsageStat("com.app1", 120_000L),
                AppUsageStat("com.app2", 60_000L)
            ),
            apps = listOf(
                AppInGroup("App1", "com.app1", "com.app1"),
                AppInGroup("App2", "com.app2", "com.app2")
            )
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 30
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertTrue(normalized.perAppUsage.isEmpty())
    }

    @Test
    fun editLimit_periodicReset_recalculatesNextResetTimeFromNow() {
        val futureReset = System.currentTimeMillis() + 7_200_000L // 2 hours from now
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 10,
            resetMinutes = 120,
            timeRemaining = 60_000L,
            nextResetTime = futureReset
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 30
        )

        val before = System.currentTimeMillis()
        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)
        val after = System.currentTimeMillis()

        // nextResetTime should be recalculated from now, NOT the old future value
        val twoHoursMs = 120 * 60 * 1000L
        assertTrue(normalized.nextResetTime >= before + twoHoursMs - 100)
        assertTrue(normalized.nextResetTime <= after + twoHoursMs + 100)
    }

    @Test
    fun editLimit_dailyReset_preservesFutureNextResetTime() {
        val futureReset = System.currentTimeMillis() + 3_600_000L // 1 hour from now
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 10,
            resetMinutes = 0, // daily mode
            timeRemaining = 60_000L,
            nextResetTime = futureReset
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 30
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        // Daily reset: nextResetTime stays as-is if still in the future
        assertEquals(futureReset, normalized.nextResetTime)
    }

    @Test
    fun editLimit_sameLimit_preservesTimeRemaining() {
        // Editing other fields (e.g., name) without changing the limit should NOT reset balance
        val previous = AppLimitGroup(
            id = 42L,
            name = "Old Name",
            timeHrLimit = 0,
            timeMinLimit = 10,
            timeRemaining = 300_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 300_000L))
        )
        val edited = previous.copy(
            name = "New Name"
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(300_000L, normalized.timeRemaining)
        assertEquals(1, normalized.perAppUsage.size)
    }

    @Test
    fun editLimit_noPreviousGroup_behavesAsBeforeForNewGroup() {
        // When no previousGroup is supplied (new group), no limit-change detection
        val group = AppLimitGroup(
            id = 0L,
            timeHrLimit = 0,
            timeMinLimit = 5,
            timeRemaining = 0L,
            perAppUsage = emptyList()
        )

        val normalized = normalizeGroupForPersistence(group, previousGroup = null)

        assertEquals(300_000L, normalized.timeRemaining) // 5 min = full limit for new group
    }

    @Test
    fun editLimit_limitEach_resetsTimeRemainingToNewLimitOnChange() {
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 5,
            limitEach = true,
            timeRemaining = 120_000L,
            apps = listOf(AppInGroup("App1", "com.app1", "com.app1")),
            perAppUsage = listOf(AppUsageStat("com.app1", 180_000L))
        )
        val edited = previous.copy(
            timeHrLimit = 0,
            timeMinLimit = 20
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(1_200_000L, normalized.timeRemaining) // 20 min in millis
        assertTrue(normalized.perAppUsage.isEmpty())
    }

    // ── Reset interval change tests ───────────────────────────────────────

    @Test
    fun editResetInterval_recalculatesNextResetTimeFromNow() {
        // User changes reset interval from daily (0) to 3 hours (180 min)
        val futureReset = System.currentTimeMillis() + 12 * 3_600_000L // 12 hours from now (midnight)
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 0, // daily
            timeRemaining = 1_800_000L,
            nextResetTime = futureReset,
            perAppUsage = listOf(AppUsageStat("com.test.app", 1_800_000L))
        )
        val edited = previous.copy(
            resetMinutes = 180 // 3 hours
        )

        val before = System.currentTimeMillis()
        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)
        val after = System.currentTimeMillis()

        // nextResetTime should be ~3 hours from now, not the old midnight value
        val threeHoursMs = 180 * 60 * 1000L
        assertTrue(normalized.nextResetTime >= before + threeHoursMs - 100)
        assertTrue(normalized.nextResetTime <= after + threeHoursMs + 100)
    }

    @Test
    fun editResetInterval_resetsTimeRemainingToFullLimit() {
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 60, // hourly
            timeRemaining = 600_000L, // 10 min left
            perAppUsage = listOf(AppUsageStat("com.test.app", 3_000_000L))
        )
        val edited = previous.copy(
            resetMinutes = 180 // 3 hours
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(3_600_000L, normalized.timeRemaining) // Full 1hr limit
    }

    @Test
    fun editResetInterval_clearsPerAppUsage() {
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 30,
            resetMinutes = 60,
            timeRemaining = 600_000L,
            apps = listOf(AppInGroup("App1", "com.app1", "com.app1")),
            perAppUsage = listOf(AppUsageStat("com.app1", 1_200_000L))
        )
        val edited = previous.copy(
            resetMinutes = 120
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertTrue(normalized.perAppUsage.isEmpty())
    }

    @Test
    fun editResetInterval_sameInterval_preservesState() {
        // Editing other fields without changing resetMinutes should NOT reset balance
        val previous = AppLimitGroup(
            id = 42L,
            name = "Old Name",
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 60,
            timeRemaining = 1_200_000L,
            nextResetTime = System.currentTimeMillis() + 1_800_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 2_400_000L))
        )
        val edited = previous.copy(
            name = "New Name"
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        assertEquals(1_200_000L, normalized.timeRemaining)
        assertEquals(1, normalized.perAppUsage.size)
    }

    @Test
    fun editResetInterval_periodicToDaily_recalculatesNextResetToMidnight() {
        val previous = AppLimitGroup(
            id = 42L,
            timeHrLimit = 1,
            timeMinLimit = 0,
            resetMinutes = 60, // hourly
            timeRemaining = 600_000L,
            nextResetTime = System.currentTimeMillis() + 1_800_000L
        )
        val edited = previous.copy(
            resetMinutes = 0 // switch to daily
        )

        val normalized = normalizeGroupForPersistence(edited, previousGroup = previous)

        // Should be midnight tomorrow
        val cal = Calendar.getInstance().apply { timeInMillis = normalized.nextResetTime }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(normalized.nextResetTime > System.currentTimeMillis())
        assertEquals(3_600_000L, normalized.timeRemaining) // Full limit reset
    }
}
