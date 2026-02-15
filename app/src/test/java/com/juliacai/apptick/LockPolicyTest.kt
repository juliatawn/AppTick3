package com.juliacai.apptick

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class LockPolicyTest {

    @Test
    fun `hasAnyConfiguredLockMode true when password configured`() {
        val state = baseState(hasPassword = true)
        assertThat(LockPolicy.hasAnyConfiguredLockMode(state)).isTrue()
    }

    @Test
    fun `evaluateEditingLock locked when password configured and not unlocked`() {
        val state = baseState(hasPassword = true, passwordUnlocked = false)
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis = 1_000L)
        assertThat(decision.isLocked).isTrue()
        assertThat(decision.shouldClearExpiredLockdown).isFalse()
    }

    @Test
    fun `evaluateEditingLock unlocked when password configured and unlocked`() {
        val state = baseState(hasPassword = true, passwordUnlocked = true)
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis = 1_000L)
        assertThat(decision.isLocked).isFalse()
    }

    @Test
    fun `evaluateEditingLock clears expired lockdown`() {
        val state = baseState(
            lockdownEnabled = true,
            lockdownEndTimeMillis = 2_000L
        )
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis = 2_001L)
        assertThat(decision.isLocked).isFalse()
        assertThat(decision.shouldClearExpiredLockdown).isTrue()
    }

    @Test
    fun `evaluateEditingLock locked in lockdown when no weekly window`() {
        val state = baseState(
            lockdownEnabled = true,
            lockdownEndTimeMillis = 999_999_999_999L,
            lockdownOneTimeWeeklyChange = false
        )
        val decision = LockPolicy.evaluateEditingLock(state, nowMillis = 50_000L)
        assertThat(decision.isLocked).isTrue()
        assertThat(decision.consumeWeeklyWindowKey).isNull()
    }

    @Test
    fun `evaluateEditingLock unlocks within weekly one-time window and emits week key`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 16) // Monday
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val state = baseState(
            lockdownEnabled = true,
            lockdownEndTimeMillis = calendar.timeInMillis + 100_000_000L,
            lockdownOneTimeWeeklyChange = true,
            lockdownWeeklyDayMondayOne = 1,
            lockdownWeeklyHour = 9,
            lockdownWeeklyMinute = 0
        )

        val decision = LockPolicy.evaluateEditingLock(state, calendar.timeInMillis)
        assertThat(decision.isLocked).isFalse()
        assertThat(decision.consumeWeeklyWindowKey).isNotNull()
    }

    @Test
    fun `evaluateEditingLock remains locked when weekly window already used`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.FEBRUARY)
            set(Calendar.DAY_OF_MONTH, 16) // Monday
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val usedKey = "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.WEEK_OF_YEAR))}"

        val state = baseState(
            lockdownEnabled = true,
            lockdownEndTimeMillis = calendar.timeInMillis + 100_000_000L,
            lockdownOneTimeWeeklyChange = true,
            lockdownWeeklyDayMondayOne = 1,
            lockdownWeeklyHour = 9,
            lockdownWeeklyMinute = 0,
            lockdownWeeklyUsedKey = usedKey
        )

        val decision = LockPolicy.evaluateEditingLock(state, calendar.timeInMillis)
        assertThat(decision.isLocked).isTrue()
        assertThat(decision.consumeWeeklyWindowKey).isNull()
    }

    private fun baseState(
        hasPassword: Boolean = false,
        hasSecurityKey: Boolean = false,
        passwordUnlocked: Boolean = false,
        securityKeyUnlocked: Boolean = false,
        lockdownEnabled: Boolean = false,
        lockdownEndTimeMillis: Long = 0L,
        lockdownOneTimeWeeklyChange: Boolean = false,
        lockdownWeeklyDayMondayOne: Int = -1,
        lockdownWeeklyHour: Int = -1,
        lockdownWeeklyMinute: Int = -1,
        lockdownWeeklyUsedKey: String? = null
    ): LockState {
        return LockState(
            hasPassword = hasPassword,
            hasSecurityKey = hasSecurityKey,
            passwordUnlocked = passwordUnlocked,
            securityKeyUnlocked = securityKeyUnlocked,
            lockdownEnabled = lockdownEnabled,
            lockdownEndTimeMillis = lockdownEndTimeMillis,
            lockdownOneTimeWeeklyChange = lockdownOneTimeWeeklyChange,
            lockdownWeeklyDayMondayOne = lockdownWeeklyDayMondayOne,
            lockdownWeeklyHour = lockdownWeeklyHour,
            lockdownWeeklyMinute = lockdownWeeklyMinute,
            lockdownWeeklyUsedKey = lockdownWeeklyUsedKey
        )
    }
}
