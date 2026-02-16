package com.juliacai.apptick.premiumMode

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.LockMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumModeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
        composeTestRule.onNodeWithText("Disable PASSWORD to use Security Key Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password Mode").assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Unlock Premium Features!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Support the developer and get access to exclusive features like Lockdown Mode, Password Mode, and more!").assertIsDisplayed()
    }
}
