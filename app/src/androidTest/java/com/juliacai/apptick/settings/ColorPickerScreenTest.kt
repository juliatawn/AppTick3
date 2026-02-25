package com.juliacai.apptick.settings

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPickerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        assumeTrue(
            "Compose-only UI tests are stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
    }

    @Test
    fun iconTabAndAdvancedColorControls_areRemoved() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        composeRule.onAllNodesWithText("Icon").assertCountEquals(0)
        composeRule.onAllNodesWithText("Spectrum Wheel").assertCountEquals(0)
        composeRule.onAllNodesWithText("Hex Code").assertCountEquals(0)
        composeRule.onAllNodesWithTag("color_wheel").assertCountEquals(0)
    }

    @Test
    fun swatchPicker_hasNoTabs() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        composeRule.onAllNodesWithText("Text").assertCountEquals(0)
        composeRule.onAllNodesWithText("Background").assertCountEquals(0)
        composeRule.onAllNodesWithText("Card").assertCountEquals(0)
        composeRule.onNodeWithText("Choose Theme Color").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    private fun setPremiumEnabled(enabled: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("premium", enabled)
            .putBoolean("custom_color_mode", true)
            .commit()
    }
}
