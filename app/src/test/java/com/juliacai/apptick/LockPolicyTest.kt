package com.juliacai.apptick

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class LockPolicyTest {

    @Test
    fun noLockMode_isNotLocked() {
        val state = LockState(
            activeLockMode = LockMode.NONE,
            passwordUnlocked = false,
            securityKeyUnlocked = false,
            lockdownType = LockdownType.ONE_TIME,
            lockdownEndTimeMillis = 0L,
            lockdownRecurringDays = emptyList(),
            lockdownRecurringUsedKey = null
        )

        val decision = LockPolicy.evaluateEditingLock(state, nowMillis = 1_700_000_000_000L)

        assertThat(decision.isLocked).isFalse()
    }

    @Test
    fun passwordMode_respectsPasswordUnlockedFlag() {
        val locked = LockPolicy.evaluateEditingLock(
            state = baseState().copy(activeLockMode = LockMode.PASSWORD, passwordUnlocked = false),
            nowMillis = 1L
        )
        val unlocked = LockPolicy.evaluateEditingLock(
            state = baseState().copy(activeLockMode = LockMode.PASSWORD, passwordUnlocked = true),
            nowMillis = 1L
        )

        assertThat(locked.isLocked).isTrue()
        assertThat(unlocked.isLocked).isFalse()
    }

    @Test
    fun securityKeyMode_respectsSecurityKeyUnlockedFlag() {
        val locked = LockPolicy.evaluateEditingLock(
            state = baseState().copy(activeLockMode = LockMode.SECURITY_KEY, securityKeyUnlocked = false),
            nowMillis = 1L
        )
        val unlocked = LockPolicy.evaluateEditingLock(
            state = baseState().copy(activeLockMode = LockMode.SECURITY_KEY, securityKeyUnlocked = true),
            nowMillis = 1L
        )

        assertThat(locked.isLocked).isTrue()
        assertThat(unlocked.isLocked).isFalse()
    }

    @Test
    fun shouldAutoRelockOnExit_trueOnlyForUnlockedCredentialModes() {
        val password = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.PASSWORD, passwordUnlocked = true)
        )
        val securityKey = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.SECURITY_KEY, securityKeyUnlocked = true)
        )
        val none = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.NONE)
        )
        val lockdown = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.LOCKDOWN)
        )

        assertThat(password).isTrue()
        assertThat(securityKey).isTrue()
        assertThat(none).isFalse()
        assertThat(lockdown).isFalse()
    }

    @Test
    fun shouldAutoRelockOnExit_falseWhenCredentialModeAlreadyLocked() {
        val password = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.PASSWORD, passwordUnlocked = false)
        )
        val securityKey = LockPolicy.shouldAutoRelockOnExit(
            baseState().copy(activeLockMode = LockMode.SECURITY_KEY, securityKeyUnlocked = false)
        )

        assertThat(password).isFalse()
        assertThat(securityKey).isFalse()
    }

    @Test
    fun oneTimeLockdown_locksBeforeEndAndClearsAfterEnd() {
        val end = 2_000L
        val before = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.ONE_TIME,
                lockdownEndTimeMillis = end
            ),
            nowMillis = 1_999L
        )
        val after = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.ONE_TIME,
                lockdownEndTimeMillis = end
            ),
            nowMillis = 2_000L
        )

        assertThat(before.isLocked).isTrue()
        assertThat(before.shouldClearExpiredLockdown).isFalse()
        assertThat(after.isLocked).isFalse()
        assertThat(after.shouldClearExpiredLockdown).isTrue()
    }

    @Test
    fun oneTimeLockdown_withoutConfiguredEnd_staysLocked() {
        val decision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.ONE_TIME,
                lockdownEndTimeMillis = 0L
            ),
            nowMillis = 2_000L
        )

        assertThat(decision.isLocked).isTrue()
        assertThat(decision.shouldClearExpiredLockdown).isFalse()
    }

    @Test
    fun recurringLockdown_unlocksOnAllowedDayAndProvidesConsumeKey() {
        val monday = millisForDay(Calendar.MONDAY)
        val decision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.RECURRING,
                lockdownRecurringDays = listOf(1), // Monday
                lockdownRecurringUsedKey = null
            ),
            nowMillis = monday
        )

        assertThat(decision.isLocked).isFalse()
        assertThat(decision.consumeKey).isNotNull()
    }

    @Test
    fun recurringLockdown_locksWhenWindowAlreadyConsumed() {
        val monday = millisForDay(Calendar.MONDAY)
        val openDecision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.RECURRING,
                lockdownRecurringDays = listOf(1)
            ),
            nowMillis = monday
        )

        val consumedDecision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.RECURRING,
                lockdownRecurringDays = listOf(1),
                lockdownRecurringUsedKey = openDecision.consumeKey
            ),
            nowMillis = monday
        )

        assertThat(consumedDecision.isLocked).isTrue()
    }

    @Test
    fun recurringLockdown_locksOnNonAllowedDay() {
        val tuesday = millisForDay(Calendar.TUESDAY)
        val decision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.RECURRING,
                lockdownRecurringDays = listOf(1) // Monday only
            ),
            nowMillis = tuesday
        )

        assertThat(decision.isLocked).isTrue()
    }

    @Test
    fun recurringLockdown_withNoAllowedDays_staysLocked() {
        val monday = millisForDay(Calendar.MONDAY)
        val decision = LockPolicy.evaluateEditingLock(
            state = baseState().copy(
                activeLockMode = LockMode.LOCKDOWN,
                lockdownType = LockdownType.RECURRING,
                lockdownRecurringDays = emptyList()
            ),
            nowMillis = monday
        )

        assertThat(decision.isLocked).isTrue()
        assertThat(decision.consumeKey).isNull()
    }

    private fun baseState(): LockState {
        return LockState(
            activeLockMode = LockMode.NONE,
            passwordUnlocked = false,
            securityKeyUnlocked = false,
            lockdownType = LockdownType.ONE_TIME,
            lockdownEndTimeMillis = 0L,
            lockdownRecurringDays = emptyList(),
            lockdownRecurringUsedKey = null
        )
    }

    private fun millisForDay(dayOfWeek: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2025)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 6) // Monday
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (dayOfWeek != Calendar.MONDAY) {
            val offset = dayOfWeek - Calendar.MONDAY
            calendar.add(Calendar.DAY_OF_MONTH, offset)
        }
        return calendar.timeInMillis
    }
}
