package com.juliacai.apptick

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyStore

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
        // Creation + first read is wrapped: if the encrypted file exists but
        // the matching keystore master key is gone (classic symptom:
        // uninstall+reinstall where Auto Backup restored the ciphertext but
        // the key was destroyed), `EncryptedSharedPreferences` throws
        // AEADBadTagException on first decrypt. In that case wipe the file
        // and the master key and start clean — premium entitlement is
        // re-hydrated from Play Billing on next launch anyway.
        val prefs = openOrReset(appContext)

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

    private fun openOrReset(appContext: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(appContext).also {
                // Force a decrypt now so we fail fast here (and can recover)
                // instead of crashing on the first caller's read.
                it.getBoolean(MIGRATION_DONE_KEY, false)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Encrypted prefs unreadable, wiping and recreating", t)
            resetEncryptedStore(appContext)
            buildEncryptedPrefs(appContext)
        }
    }

    private fun buildEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_FILE,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun resetEncryptedStore(appContext: Context) {
        // Delete the encrypted prefs file itself.
        runCatching { appContext.deleteSharedPreferences(ENCRYPTED_PREFS_FILE) }
        // Delete the master key from Android Keystore so MasterKeys.getOrCreate
        // generates a fresh one next time.
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(ANDROIDX_SECURITY_MASTER_KEY_ALIAS)
        }
    }

    private const val TAG = "PremiumStore"
    // Default alias used by androidx.security.crypto's MasterKeys.
    private const val ANDROIDX_SECURITY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"

    // Public API swallows every throwable so a degraded encrypted store can
    // never crash the app. If reads fail we return `false` (non-premium);
    // Play Billing re-hydrates the correct state on the next launch anyway.
    fun isPremium(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_ENTITLEMENT, false)
        } catch (t: Throwable) {
            Log.w(TAG, "isPremium read failed, defaulting to false", t)
            false
        }
    }

    fun setPremium(context: Context, entitled: Boolean) {
        try {
            getPrefs(context).edit {
                putBoolean(KEY_ENTITLEMENT, entitled)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "setPremium write failed, entitlement will be re-applied on next launch", t)
        }
    }
}
