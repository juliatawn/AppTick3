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
    val shouldClearExpiredLockdown: Boolean,
    val consumeWeeklyWindowKey: String?
)

object LockPolicy {

    fun hasAnyConfiguredLockMode(state: LockState): Boolean {
        return state.hasPassword || state.hasSecurityKey || state.lockdownEnabled
    }

    fun evaluateEditingLock(state: LockState, nowMillis: Long): LockDecision {
        if ((state.hasPassword || state.hasSecurityKey) &&
            !(state.passwordUnlocked || state.securityKeyUnlocked)
        ) {
            return LockDecision(isLocked = true, shouldClearExpiredLockdown = false, consumeWeeklyWindowKey = null)
        }

        if (!state.lockdownEnabled) {
            return LockDecision(isLocked = false, shouldClearExpiredLockdown = false, consumeWeeklyWindowKey = null)
        }

        if (state.lockdownEndTimeMillis > 0L && nowMillis >= state.lockdownEndTimeMillis) {
            return LockDecision(isLocked = false, shouldClearExpiredLockdown = true, consumeWeeklyWindowKey = null)
        }

        val weeklyWindow = currentWeeklyWindow(state, nowMillis)
        return if (weeklyWindow == null) {
            LockDecision(isLocked = true, shouldClearExpiredLockdown = false, consumeWeeklyWindowKey = null)
        } else {
            LockDecision(isLocked = false, shouldClearExpiredLockdown = false, consumeWeeklyWindowKey = weeklyWindow)
        }
    }

    private fun currentWeeklyWindow(state: LockState, nowMillis: Long): String? {
        if (!state.lockdownOneTimeWeeklyChange) return null
        if (state.lockdownWeeklyDayMondayOne !in 1..7) return null
        if (state.lockdownWeeklyHour !in 0..23) return null
        if (state.lockdownWeeklyMinute !in 0..59) return null

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis

        val dayOfWeekMondayOne = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        if (dayOfWeekMondayOne != state.lockdownWeeklyDayMondayOne) return null

        val minutesOfDayNow = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val minutesOfDayStart = state.lockdownWeeklyHour * 60 + state.lockdownWeeklyMinute
        if (minutesOfDayNow < minutesOfDayStart) return null

        val weekKey = calendar.get(Calendar.YEAR).toString() + "-" +
            String.format(Locale.US, "%02d", calendar.get(Calendar.WEEK_OF_YEAR))

        return if (state.lockdownWeeklyUsedKey == weekKey) null else weekKey
    }
}
