package com.juliacai.apptick.lockModes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordResetDestinationResolverTest {

    @Test
    fun resolvePostLinkDestination_setupPassword_returnsSetPassword() {
        assertThat(
            resolvePostLinkDestination(
                purpose = RecoveryEmailHelper.PURPOSE_SETUP_PASSWORD,
                resetMode = "password"
            )
        ).isEqualTo(PostLinkDestination.SET_PASSWORD)
    }

    @Test
    fun resolvePostLinkDestination_setupSecurityKey_returnsLockModes() {
        assertThat(
            resolvePostLinkDestination(
                purpose = RecoveryEmailHelper.PURPOSE_SETUP_SECURITY_KEY,
                resetMode = "security_key"
            )
        ).isEqualTo(PostLinkDestination.LOCK_MODES)
    }

    @Test
    fun resolvePostLinkDestination_resetPassword_returnsEnterPassword() {
        assertThat(
            resolvePostLinkDestination(
                purpose = RecoveryEmailHelper.PURPOSE_RESET_PASSWORD,
                resetMode = "password"
            )
        ).isEqualTo(PostLinkDestination.ENTER_PASSWORD)
    }

    @Test
    fun resolvePostLinkDestination_resetSecurityKey_returnsEnterSecurityKey() {
        assertThat(
            resolvePostLinkDestination(
                purpose = RecoveryEmailHelper.PURPOSE_RESET_SECURITY_KEY,
                resetMode = "security_key"
            )
        ).isEqualTo(PostLinkDestination.ENTER_SECURITY_KEY)
    }

    @Test
    fun resolvePostLinkDestination_unknownPurpose_fallsBackToResetMode() {
        assertThat(resolvePostLinkDestination(purpose = null, resetMode = "security_key"))
            .isEqualTo(PostLinkDestination.ENTER_SECURITY_KEY)
        assertThat(resolvePostLinkDestination(purpose = "unknown", resetMode = "password"))
            .isEqualTo(PostLinkDestination.ENTER_PASSWORD)
    }
}
