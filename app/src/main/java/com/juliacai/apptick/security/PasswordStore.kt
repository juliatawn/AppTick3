package com.juliacai.apptick.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.juliacai.apptick.PremiumStore

/**
 * Hashed-password storage for the lock-mode password.
 *
 * The hash lives in EncryptedSharedPreferences (shared with PremiumStore) and
 * is excluded from auto-backup, so an adb-backup attack on a debuggable
 * install can no longer extract the password — and even on a rooted device
 * the attacker only obtains the PBKDF2 hash, not the plaintext.
 *
 * On any read/write, a one-time migration moves any pre-existing plaintext
 * `groupPrefs.password` into the hashed store and removes the legacy key, so
 * users upgrading from an older build keep the same password without a
 * re-enrollment prompt.
 */
object PasswordStore {
    private const val TAG = "PasswordStore"

    private const val KEY_PASSWORD_HASH = "lock_password_hash_v1"

    private const val LEGACY_PREFS = "groupPrefs"
    private const val LEGACY_KEY = "password"

    fun hasPassword(context: Context): Boolean {
        migrateIfNeeded(context)
        return readHash(context) != null
    }

    fun verify(context: Context, password: String): Boolean {
        migrateIfNeeded(context)
        val hash = readHash(context) ?: return false
        return PasswordHasher.verify(password, hash)
    }

    fun setPassword(context: Context, password: String) {
        try {
            secure(context).edit { putString(KEY_PASSWORD_HASH, PasswordHasher.hash(password)) }
        } catch (t: Throwable) {
            Log.w(TAG, "setPassword failed", t)
        }
        legacy(context).edit { remove(LEGACY_KEY) }
    }

    fun clearPassword(context: Context) {
        try {
            secure(context).edit { remove(KEY_PASSWORD_HASH) }
        } catch (t: Throwable) {
            Log.w(TAG, "clearPassword failed", t)
        }
        legacy(context).edit { remove(LEGACY_KEY) }
    }

    private fun migrateIfNeeded(context: Context) {
        val legacyPrefs = legacy(context)
        val legacyPlaintext = legacyPrefs.getString(LEGACY_KEY, null)?.takeIf { it.isNotBlank() }
            ?: return
        val secure = try {
            secure(context)
        } catch (t: Throwable) {
            // Encrypted prefs unavailable — leave legacy in place rather than
            // silently dropping the user's password. Next call retries.
            Log.w(TAG, "secure prefs unavailable for password migration", t)
            return
        }
        if (!secure.contains(KEY_PASSWORD_HASH)) {
            secure.edit { putString(KEY_PASSWORD_HASH, PasswordHasher.hash(legacyPlaintext)) }
        }
        legacyPrefs.edit { remove(LEGACY_KEY) }
    }

    private fun readHash(context: Context): String? {
        return try {
            secure(context).getString(KEY_PASSWORD_HASH, null)?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "readHash failed", t)
            null
        }
    }

    private fun secure(context: Context): SharedPreferences =
        PremiumStore.getSecurePrefs(context)

    private fun legacy(context: Context): SharedPreferences =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
}
