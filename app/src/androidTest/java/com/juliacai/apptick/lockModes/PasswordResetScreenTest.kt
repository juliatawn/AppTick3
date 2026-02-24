package com.juliacai.apptick.lockModes

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordResetScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetScreen_whenRecoveryEmailNotVerified_blocksResetForm() {
        val prefs = preparePrefs(
            recoveryEmail = "user@example.com",
            recoveryEmailVerified = false
        )

        composeRule.setContent {
            AppTheme {
                PasswordResetScreen(
                    prefs = prefs,
                    resetMode = "password",
                    onPasswordReset = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Recovery email is not verified. Verify it in Lock Mode settings before resetting."
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Send Recovery Link").assertCountEquals(0)
    }

    @Test
    fun resetScreen_whenRecoveryEmailVerified_showsResetForm() {
        val prefs = preparePrefs(
            recoveryEmail = "user@example.com",
            recoveryEmailVerified = true
        )

        composeRule.setContent {
            AppTheme {
                PasswordResetScreen(
                    prefs = prefs,
                    resetMode = "password",
                    onPasswordReset = {}
                )
            }
        }

        composeRule.onNodeWithText("Recovery Email").assertIsDisplayed()
        composeRule.onNodeWithText("Send Recovery Link").assertIsDisplayed()
    }

    @Test
    fun resetScreen_securityKeyMode_usesSecurityKeyVerificationFlag() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .also {
                it.edit()
                    .clear()
                    .putString("recovery_email_security_key", "key@example.com")
                    .putBoolean("recovery_email_security_key_verified", false)
                    .commit()
            }

        composeRule.setContent {
            AppTheme {
                PasswordResetScreen(
                    prefs = prefs,
                    resetMode = "security_key",
                    onPasswordReset = {}
                )
            }
        }

        composeRule.onNodeWithText("Reset Security Key").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Recovery email is not verified. Verify it in Lock Mode settings before resetting."
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Send Recovery Link").assertCountEquals(0)
    }

    private fun preparePrefs(
        recoveryEmail: String?,
        recoveryEmailVerified: Boolean
    ) = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        .also { prefs ->
            prefs.edit()
                .clear()
                .apply {
                    if (recoveryEmail != null) {
                        putString("recovery_email", recoveryEmail)
                    }
                    putBoolean("recovery_email_password_verified", recoveryEmailVerified)
                }
                .commit()
        }
}
