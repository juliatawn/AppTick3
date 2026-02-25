package com.juliacai.apptick.premiumMode

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.LockMode
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumModeScreenTest {

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
    fun premiumUserWithPasswordMode_disablesOtherLockModes() {
        composeTestRule.setContent {
            PremiumModeScreen(
                productDetails = null,
                isPremium = true,
                activeLockMode = LockMode.PASSWORD,
                onPurchaseClick = {},
                navController = rememberNavController(),
                onBackClick = {}
            )
        }

        // Only the active mode should be configurable; others show disable guidance.
        composeTestRule.onNodeWithText("Disable PASSWORD to use Lockdown Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password Mode").assertIsDisplayed()
    }

    @Test
    fun premiumUserWithSecurityKeyMode_disablesOtherLockModes() {
        composeTestRule.setContent {
            PremiumModeScreen(
                productDetails = null,
                isPremium = true,
                activeLockMode = LockMode.SECURITY_KEY,
                onPurchaseClick = {},
                navController = rememberNavController(),
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Disable SECURITY_KEY to use Lockdown Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disable SECURITY_KEY to use Password Mode").assertIsDisplayed()
    }

    @Test
    fun premiumUserWithLockdownMode_disablesPasswordMode() {
        composeTestRule.setContent {
            PremiumModeScreen(
                productDetails = null,
                isPremium = true,
                activeLockMode = LockMode.LOCKDOWN,
                onPurchaseClick = {},
                navController = rememberNavController(),
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Disable LOCKDOWN to use Password Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lockdown Mode").assertIsDisplayed()
    }

    @Test
    fun freeUser_seesPremiumPurchaseMessaging() {
        composeTestRule.setContent {
            PremiumModeScreen(
                productDetails = null,
                isPremium = false,
                activeLockMode = LockMode.NONE,
                onPurchaseClick = {},
                navController = rememberNavController(),
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Support the developer and unlock Premium Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Key Features:").assertIsDisplayed()
    }
}
