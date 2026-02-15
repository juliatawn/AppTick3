package com.juliacai.apptick.newAppLimit

import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLimitPersistenceNormalizationTest {

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
    fun existingGroupWithCarryOver_keepsPositiveTimeRemaining() {
        val group = AppLimitGroup(
            id = 42L,
            timeHrLimit = 0,
            timeMinLimit = 2,
            timeRemaining = 300_000L,
            perAppUsage = listOf(AppUsageStat("com.test.app", 30_000L))
        )

        val normalized = normalizeGroupForPersistence(group)

        assertEquals(300_000L, normalized.timeRemaining)
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
}
