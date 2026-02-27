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
}
