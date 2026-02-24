package com.juliacai.apptick.lockModes

object RecoveryResetPolicy {
    fun hasConfiguredRecoveryEmail(recoveryEmail: String?): Boolean = !recoveryEmail.isNullOrBlank()

    fun canStartResetFlow(recoveryEmail: String?, isRecoveryEmailVerified: Boolean): Boolean {
        return hasConfiguredRecoveryEmail(recoveryEmail) && isRecoveryEmailVerified
    }

    fun canSendResetLink(
        enteredEmail: String,
        recoveryEmail: String?,
        isRecoveryEmailVerified: Boolean
    ): Boolean {
        if (!canStartResetFlow(recoveryEmail, isRecoveryEmailVerified)) return false
        if (enteredEmail.isBlank()) return false
        return enteredEmail.equals(recoveryEmail, ignoreCase = true)
    }
}
