package com.juliacai.apptick.lockModes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryEmailEnforcerTest {

    @Test
    fun shouldPrompt_returnsTrue_onlyForForcedPasswordModeWithoutEmail() {
        assertThat(
            RecoveryEmailEnforcer.shouldPrompt(
                forceSetup = true,
                activeMode = "PASSWORD",
                recoveryEmail = ""
            )
        ).isTrue()
    }

    @Test
    fun shouldPrompt_returnsFalse_whenEmailAlreadySet() {
        assertThat(
            RecoveryEmailEnforcer.shouldPrompt(
                forceSetup = true,
                activeMode = "PASSWORD",
                recoveryEmail = "user@example.com"
            )
        ).isFalse()
    }

    @Test
    fun shouldClearForceFlag_returnsTrue_whenForcedAndEmailExists() {
        assertThat(
            RecoveryEmailEnforcer.shouldClearForceFlag(
                forceSetup = true,
                recoveryEmail = "user@example.com"
            )
        ).isTrue()
    }
}
