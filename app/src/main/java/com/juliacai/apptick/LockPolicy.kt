package com.juliacai.apptick

import java.util.Calendar
import java.util.Locale

enum class LockMode {
    NONE, LOCKDOWN, PASSWORD, SECURITY_KEY
}

enum class LockdownType {
    ONE_TIME, RECURRING
}

data class LockState(
    val activeLockMode: LockMode,
    
    // Credential States
    val passwordUnlocked: Boolean,
    val securityKeyUnlocked: Boolean,
    
    // Legacy/Migration helpers (optional, but cleaner to just use new fields)
    // Lockdown Details
    val lockdownType: LockdownType,
    val lockdownEndTimeMillis: Long,          // For ONE_TIME
    val lockdownRecurringDays: List<Int>,     // For RECURRING (1=Mon..7=Sun)
    val lockdownRecurringUsedKey: String?     // e.g. "2023-10-27" to track if window was consumed today
)

data class LockDecision(
    val isLocked: Boolean,
    val shouldClearExpiredLockdown: Boolean = false,
    val consumeKey: String? = null
)

object LockPolicy {
    fun hasAnyConfiguredLockMode(state: LockState): Boolean {
        return state.activeLockMode != LockMode.NONE
    }

    fun shouldAutoRelockOnExit(state: LockState): Boolean {
        return when (state.activeLockMode) {
            LockMode.PASSWORD -> state.passwordUnlocked
            LockMode.SECURITY_KEY -> state.securityKeyUnlocked
            else -> false
        }
    }

    fun evaluateEditingLock(state: LockState, nowMillis: Long): LockDecision {
        return when (state.activeLockMode) {
            LockMode.NONE -> LockDecision(isLocked = false)
            LockMode.PASSWORD -> LockDecision(isLocked = !state.passwordUnlocked)
            LockMode.SECURITY_KEY -> LockDecision(isLocked = !state.securityKeyUnlocked)
            LockMode.LOCKDOWN -> evaluateLockdown(state, nowMillis)
        }
    }

    private fun evaluateLockdown(state: LockState, nowMillis: Long): LockDecision {
        if (state.lockdownType == LockdownType.ONE_TIME) {
            val expired = state.lockdownEndTimeMillis > 0 && nowMillis >= state.lockdownEndTimeMillis
            return LockDecision(
                isLocked = !expired,
                shouldClearExpiredLockdown = expired
            )
        } else {
            // RECURRING
            val currentDay = dayOfWeek(nowMillis) // 1=Mon, 7=Sun
            val todayKey = currentDateKey(nowMillis)
            
            // If today is NOT in allowed days, it is locked.
            if (!state.lockdownRecurringDays.contains(currentDay)) {
                return LockDecision(isLocked = true)
            }
            
            // If today IS allowed, check if user already "consumed" the window (locked it again).
            if (state.lockdownRecurringUsedKey == todayKey) {
                return LockDecision(isLocked = true)
            }
            
            // Otherwise, it is unlocked (window is open).
            return LockDecision(
                isLocked = false,
                consumeKey = todayKey // User can consume this key to lock it
            )
        }
    }

    private fun dayOfWeek(millis: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        // Cal: Sun=1, Mon=2...
        // We want Mon=1... Sun=7
        val day = c.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    private fun currentDateKey(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return String.format(Locale.US, "%04d-%02d-%02d", 
            c.get(Calendar.YEAR), 
            c.get(Calendar.MONTH) + 1, 
            c.get(Calendar.DAY_OF_MONTH))
    }
}
