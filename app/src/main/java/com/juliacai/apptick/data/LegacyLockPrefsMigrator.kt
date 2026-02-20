package com.juliacai.apptick.data

import android.content.SharedPreferences
import androidx.core.content.edit

object LegacyLockPrefsMigrator {
    private const val KEY_ACTIVE_LOCK_MODE = "active_lock_mode"
    private const val KEY_PASSWORD = "password"
    private const val KEY_PASS_UNLOCKED = "passUnlocked"
    private const val KEY_SECURITY_KEY_UNLOCKED = "securityKeyUnlocked"

    private const val KEY_LEGACY_LOCKED = "locked"
    private const val KEY_LEGACY_TEMP_UNLOCKED = "tempUnlocked"
    private const val KEY_LEGACY_DATE_DAY = "dateDay"
    private const val KEY_RECOVERY_EMAIL = "recovery_email"
    private const val KEY_FORCE_RECOVERY_EMAIL_SETUP = "force_recovery_email_setup"

    private const val MODE_NONE = "NONE"
    private const val MODE_PASSWORD = "PASSWORD"

    fun migrate(prefs: SharedPreferences, nowMillis: Long = System.currentTimeMillis()) {
        val allPrefs = prefs.all
        val legacyPasswordMode = allPrefs[KEY_PASSWORD] as? Boolean
        val legacyLocked = allPrefs[KEY_LEGACY_LOCKED] as? Boolean ?: false
        val legacyTempUnlocked = allPrefs[KEY_LEGACY_TEMP_UNLOCKED] as? Boolean ?: false
        val legacyDateDay = allPrefs[KEY_LEGACY_DATE_DAY] as? Int
        val currentDay = dayOfMonth(nowMillis)
        val existingPasswordString = allPrefs[KEY_PASSWORD] as? String
        val existingRecoveryEmail = allPrefs[KEY_RECOVERY_EMAIL] as? String

        val activeMode = (allPrefs[KEY_ACTIVE_LOCK_MODE] as? String)
            ?.takeIf { it.isNotBlank() }
            ?: MODE_NONE

        val shouldEnablePasswordMode =
            activeMode == MODE_NONE && legacyLocked && legacyPasswordMode == true

        val shouldSetPassUnlocked = when {
            prefs.contains(KEY_PASS_UNLOCKED) -> null
            shouldEnablePasswordMode && !existingPasswordString.isNullOrBlank() -> false
            shouldEnablePasswordMode -> true
            activeMode == MODE_PASSWORD && !existingPasswordString.isNullOrBlank() -> false
            activeMode == MODE_PASSWORD -> true
            legacyLocked && legacyTempUnlocked -> true
            legacyLocked && legacyDateDay != null && legacyDateDay != currentDay -> true
            else -> null
        }

        val hasLegacyPasswordType = legacyPasswordMode != null
        val shouldForceRecoveryEmailSetup =
            legacyLocked &&
                (legacyPasswordMode == true || activeMode == MODE_PASSWORD || shouldEnablePasswordMode) &&
                existingRecoveryEmail.isNullOrBlank()
        if (!hasLegacyPasswordType && !shouldEnablePasswordMode && shouldSetPassUnlocked == null) {
            return
        }

        prefs.edit {
            if (hasLegacyPasswordType) remove(KEY_PASSWORD)
            if (shouldEnablePasswordMode) putString(KEY_ACTIVE_LOCK_MODE, MODE_PASSWORD)
            shouldSetPassUnlocked?.let { putBoolean(KEY_PASS_UNLOCKED, it) }
            putBoolean(KEY_FORCE_RECOVERY_EMAIL_SETUP, shouldForceRecoveryEmailSetup)
            if (!prefs.contains(KEY_SECURITY_KEY_UNLOCKED)) {
                putBoolean(KEY_SECURITY_KEY_UNLOCKED, false)
            }
        }
    }

    private fun dayOfMonth(nowMillis: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        return calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }
}
