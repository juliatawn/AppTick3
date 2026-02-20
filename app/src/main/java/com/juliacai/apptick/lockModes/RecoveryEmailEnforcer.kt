package com.juliacai.apptick.lockModes

object RecoveryEmailEnforcer {
    fun shouldPrompt(forceSetup: Boolean, activeMode: String?, recoveryEmail: String?): Boolean {
        return forceSetup && activeMode == "PASSWORD" && recoveryEmail.isNullOrBlank()
    }

    fun shouldClearForceFlag(forceSetup: Boolean, recoveryEmail: String?): Boolean {
        return forceSetup && !recoveryEmail.isNullOrBlank()
    }
}
