package com.juliacai.apptick.lockModes

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth

/**
 * Helper that wraps Firebase Auth email-link (magic-link) operations
 * for password / security-key recovery.
 *
 * Flow:
 * 1. User taps "Reset" → [sendRecoveryLink] sends a magic link to their email.
 * 2. User opens the link → the app's deep-link handler calls [verifyRecoveryLink].
 * 3. On success the caller clears the local password / security-key.
 */
object RecoveryEmailHelper {

    private const val TAG = "RecoveryEmail"
    private const val PREF_PENDING_EMAIL = "recovery_pending_email"
    private const val PREF_PENDING_PURPOSE = "recovery_pending_purpose"
    // Use Firebase-hosted auth domains so email links route through Firebase Auth
    // without requiring custom website/App Links configuration.
    private const val RECOVERY_CONTINUE_URL = "https://apptick-f16dc.firebaseapp.com/recovery"

    const val PURPOSE_SETUP_PASSWORD = "setup_password"
    const val PURPOSE_SETUP_SECURITY_KEY = "setup_security_key"
    const val PURPOSE_RESET_PASSWORD = "reset_password"
    const val PURPOSE_RESET_SECURITY_KEY = "reset_security_key"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Settings that tell Firebase how to build the email link. */
    private fun actionCodeSettings(packageName: String): ActionCodeSettings =
        ActionCodeSettings.newBuilder()
            .setUrl(RECOVERY_CONTINUE_URL)
            .setHandleCodeInApp(true)
            .setAndroidPackageName(packageName, true, null)
            .build()

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Send a sign-in link to [email].
     * Stores the email in SharedPreferences so we can complete verification later.
     */
    fun sendRecoveryLink(
        context: Context,
        email: String,
        purpose: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val settings = actionCodeSettings(context.packageName)

        auth.sendSignInLinkToEmail(email, settings)
            .addOnSuccessListener {
                // Remember which email we sent the link to
                context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_PENDING_EMAIL, email)
                    .putString(PREF_PENDING_PURPOSE, purpose)
                    .apply()
                Log.i(TAG, "Recovery link sent to $email")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send recovery link", e)
                onError(e.localizedMessage ?: "Failed to send recovery email")
            }
    }

    /**
     * Check whether [intent] contains a Firebase email sign-in link.
     */
    fun isRecoveryLink(intent: Intent?): Boolean {
        val link = intent?.data?.toString() ?: return false
        return auth.isSignInWithEmailLink(link)
    }

    /**
     * Complete the email-link verification.
     * Returns the verified email on success so the caller can clear the correct lock mode.
     */
    fun verifyRecoveryLink(
        context: Context,
        intent: Intent?,
        onSuccess: (verifiedEmail: String, purpose: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val link = intent?.data?.toString()
        if (link == null) {
            onError("No recovery link found")
            return
        }

        val prefs = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        val pendingEmail = prefs.getString(PREF_PENDING_EMAIL, null)
        val pendingPurpose = prefs.getString(PREF_PENDING_PURPOSE, null)

        if (pendingEmail == null) {
            onError("No pending recovery request. Please request a new link.")
            return
        }

        auth.signInWithEmailLink(pendingEmail, link)
            .addOnSuccessListener {
                // Clean up
                prefs.edit()
                    .remove(PREF_PENDING_EMAIL)
                    .remove(PREF_PENDING_PURPOSE)
                    .apply()
                // Sign out immediately — we only used Auth for link verification
                auth.signOut()
                Log.i(TAG, "Recovery link verified for $pendingEmail")
                onSuccess(pendingEmail, pendingPurpose)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Recovery link verification failed", e)
                onError(e.localizedMessage ?: "Link verification failed. It may have expired.")
            }
    }
}
