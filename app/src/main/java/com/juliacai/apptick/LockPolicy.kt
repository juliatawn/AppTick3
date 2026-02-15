package com.juliacai.apptick

import java.util.Calendar
import java.util.Locale

data class LockState(
    val hasPassword: Boolean,
    val hasSecurityKey: Boolean,
    val passwordUnlocked: Boolean,
    val securityKeyUnlocked: Boolean,
    val lockdownEnabled: Boolean,
    val lockdownEndTimeMillis: Long,
    val lockdownOneTimeWeeklyChange: Boolean,
    val lockdownWeeklyDayMondayOne: Int,
    val lockdownWeeklyHour: Int,
    val lockdownWeeklyMinute: Int,
    val lockdownWeeklyUsedKey: String?
)

data class LockDecision(
    val isLocked: Boolean,
    val shouldClearExpiredLockdown: Boolean = false,
    val consumeWeeklyWindowKey: String? = null
)

object LockPolicy {
    fun hasAnyConfiguredLockMode(state: LockState): Boolean {
        return state.hasPassword || state.hasSecurityKey || state.lockdownEnabled
    }

    fun evaluateEditingLock(state: LockState, nowMillis: Long): LockDecision {
        val expiredLockdown = state.lockdownEnabled &&
            state.lockdownEndTimeMillis > 0L &&
            nowMillis >= state.lockdownEndTimeMillis

        val lockdownActive = state.lockdownEnabled && !expiredLockdown
        val credentialsLocked =
            (state.hasPassword && !state.passwordUnlocked) ||
            (state.hasSecurityKey && !state.securityKeyUnlocked)

        var weeklyWindowOpen = false
        var windowKey: String? = null
        if (lockdownActive && state.lockdownOneTimeWeeklyChange) {
            windowKey = currentWeekKey(nowMillis)
            weeklyWindowOpen = isInWeeklyWindow(state, nowMillis) &&
                state.lockdownWeeklyUsedKey != windowKey
        }

        val lockedByLockdown = lockdownActive && !weeklyWindowOpen
        return LockDecision(
            isLocked = credentialsLocked || lockedByLockdown,
            shouldClearExpiredLockdown = expiredLockdown,
            consumeWeeklyWindowKey = if (weeklyWindowOpen) windowKey else null
        )
    }

    private fun isInWeeklyWindow(state: LockState, nowMillis: Long): Boolean {
        if (state.lockdownWeeklyDayMondayOne !in 1..7) return false
        if (state.lockdownWeeklyHour !in 0..23) return false
        if (state.lockdownWeeklyMinute !in 0..59) return false

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowDayMondayOne = ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        if (nowDayMondayOne != state.lockdownWeeklyDayMondayOne) return false

        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = state.lockdownWeeklyHour * 60 + state.lockdownWeeklyMinute
        return nowMinutes >= startMinutes
    }

    private fun currentWeekKey(nowMillis: Long): String {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = nowMillis
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }
}
