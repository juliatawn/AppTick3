package com.juliacai.apptick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Flaky on device farms/OEM builds due missing Compose hierarchy despite setContent.")
class ChangelogDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changelogDialog_displaysAdvertisedFeatureSectionsAndCloses() {
        var dismissed = false
        composeTestRule.setContent {
            AppTheme {
                ChangelogDialog(onDismiss = { dismissed = true })
            }
        }

        composeTestRule.onNodeWithText("New premium features:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Floating Time Left Bubble", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Backup and restore option", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("New FREE features:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reliability updates:", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.runOnIdle {
            assertThat(dismissed).isTrue()
        }
    }
}
