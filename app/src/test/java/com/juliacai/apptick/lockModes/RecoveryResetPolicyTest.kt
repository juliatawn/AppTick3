package com.juliacai.apptick.lockModes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryResetPolicyTest {

    @Test
    fun canStartResetFlow_false_whenNoRecoveryEmail() {
        assertThat(RecoveryResetPolicy.canStartResetFlow(null, isRecoveryEmailVerified = true)).isFalse()
        assertThat(RecoveryResetPolicy.canStartResetFlow("", isRecoveryEmailVerified = true)).isFalse()
    }

    @Test
    fun canStartResetFlow_false_whenNotVerified() {
        assertThat(
            RecoveryResetPolicy.canStartResetFlow(
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = false
            )
        ).isFalse()
    }

    @Test
    fun canStartResetFlow_true_whenEmailPresentAndVerified() {
        assertThat(
            RecoveryResetPolicy.canStartResetFlow(
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = true
            )
        ).isTrue()
    }

    @Test
    fun canSendResetLink_false_whenEnteredEmailBlank() {
        assertThat(
            RecoveryResetPolicy.canSendResetLink(
                enteredEmail = "",
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = true
            )
        ).isFalse()
    }

    @Test
    fun canSendResetLink_false_whenEnteredEmailDoesNotMatch() {
        assertThat(
            RecoveryResetPolicy.canSendResetLink(
                enteredEmail = "other@example.com",
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = true
            )
        ).isFalse()
    }

    @Test
    fun canSendResetLink_false_whenNotVerifiedEvenIfEmailsMatch() {
        assertThat(
            RecoveryResetPolicy.canSendResetLink(
                enteredEmail = "user@example.com",
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = false
            )
        ).isFalse()
    }

    @Test
    fun canSendResetLink_true_whenEmailsMatchCaseInsensitiveAndVerified() {
        assertThat(
            RecoveryResetPolicy.canSendResetLink(
                enteredEmail = "User@Example.com",
                recoveryEmail = "user@example.com",
                isRecoveryEmailVerified = true
            )
        ).isTrue()
    }
}
