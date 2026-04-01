package com.juliacai.apptick

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Centralized, encrypted storage for premium entitlement state.
 *
 * On first access the store migrates any existing plaintext "premium" flag from
 * the legacy "groupPrefs" SharedPreferences into an EncryptedSharedPreferences
 * file, then removes the plaintext key so it cannot be trivially edited.
 *
 * All premium reads/writes across the app should go through this singleton.
 */
object PremiumStore {

    // Obfuscated key — not the obvious "premium" string
    private const val KEY_ENTITLEMENT = "ent_v1_ac"
    private const val ENCRYPTED_PREFS_FILE = "apptick_secure_prefs"
    private const val MIGRATION_DONE_KEY = "premium_migrated_v1"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPrefs(context.applicationContext).also {
                encryptedPrefs = it
            }
        }
    }

    private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val prefs = EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_FILE,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // One-time migration from plaintext groupPrefs → encrypted prefs
        val legacyPrefs = appContext.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MIGRATION_DONE_KEY, false)) {
            val legacyPremium = legacyPrefs.getBoolean("premium", false)
            prefs.edit {
                putBoolean(KEY_ENTITLEMENT, legacyPremium)
                putBoolean(MIGRATION_DONE_KEY, true)
            }
            // Remove the plaintext key so it can no longer be exploited
            legacyPrefs.edit { remove("premium") }
            // Also clean up debug flag
            legacyPrefs.edit { remove("debug_force_free") }
        }

        return prefs
    }

    fun isPremium(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENTITLEMENT, false)
    }

    fun setPremium(context: Context, entitled: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_ENTITLEMENT, entitled)
        }
    }
}
