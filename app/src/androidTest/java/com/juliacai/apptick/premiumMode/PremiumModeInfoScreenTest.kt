package com.juliacai.apptick.premiumMode

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumModeInfoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        assumeTrue(
            "Compose-only UI tests are stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
    }

    @Test
    fun premiumModeInfoScreen_showsCorePremiumFeatures_andBackNavigates() {
        var backPressed = false
        composeTestRule.setContent {
            PremiumModeInfoScreen(onBackClick = { backPressed = true })
        }

        composeTestRule.onNodeWithText("Premium Mode Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Time Range").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Floating Time Left Bubble").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Lockdown mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Password mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Dark mode and AppTick color theming").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.runOnIdle {
            assertThat(backPressed).isTrue()
        }
    }
}
