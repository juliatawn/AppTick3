package com.juliacai.apptick.settings

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.settings.ColorPickerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPickerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wheelOnBackgroundTab_onlyUpdatesBackgroundColor() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        // Move to background tab before interacting with the wheel.
        composeRule.onNodeWithText("Background").performClick()

        val primaryBefore = getTextByTag("primary_hex")
        val backgroundBefore = getTextByTag("background_hex")

        composeRule.onNodeWithTag("color_wheel").assertIsDisplayed()
        composeRule.onNodeWithTag("color_wheel").performTouchInput {
            down(Offset(110f, 12f))
            up()
        }

        val primaryAfter = getTextByTag("primary_hex")
        val backgroundAfter = getTextByTag("background_hex")

        assertEquals("Primary color should not change on Background tab", primaryBefore, primaryAfter)
        assertNotEquals("Background color should change on Background tab", backgroundBefore, backgroundAfter)
    }

    @Test
    fun wheelOnTextTab_onlyUpdatesPrimaryTextColor() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        composeRule.onNodeWithText("Text").performClick()

        val primaryBefore = getTextByTag("primary_hex")
        val backgroundBefore = getTextByTag("background_hex")

        composeRule.onNodeWithTag("color_wheel").assertIsDisplayed()
        composeRule.onNodeWithTag("color_wheel").performTouchInput {
            down(Offset(208f, 110f))
            up()
        }

        val primaryAfter = getTextByTag("primary_hex")
        val backgroundAfter = getTextByTag("background_hex")

        assertNotEquals("Primary color should change on Text tab", primaryBefore, primaryAfter)
        assertEquals("Background color should not change on Text tab", backgroundBefore, backgroundAfter)
    }

    @Test
    fun wheelOnIconTabCustom_onlyUpdatesIconColor() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        // Move to icon tab and disable system-match so icon color is editable.
        composeRule.onNodeWithText("Icon").performClick()
        composeRule.onNodeWithTag("icon_match_system_toggle").performClick()

        val primaryBefore = getTextByTag("primary_hex")
        val backgroundBefore = getTextByTag("background_hex")
        val iconBefore = getTextByTag("icon_hex")

        composeRule.onNodeWithTag("color_wheel").assertIsDisplayed()
        composeRule.onNodeWithTag("color_wheel").performTouchInput {
            down(Offset(110f, 208f))
            up()
        }

        val primaryAfter = getTextByTag("primary_hex")
        val backgroundAfter = getTextByTag("background_hex")
        val iconAfter = getTextByTag("icon_hex")

        assertEquals("Primary color should not change on Icon tab", primaryBefore, primaryAfter)
        assertEquals("Background color should not change on Icon tab", backgroundBefore, backgroundAfter)
        assertNotEquals("Icon color should change on Icon tab", iconBefore, iconAfter)
    }

    @Test
    fun changingTextColor_doesNotChangeEffectiveIconPreview_whenIconSetCustom() {
        setPremiumEnabled(enabled = true)
        composeRule.setContent {
            ColorPickerScreen(onBackClick = {})
        }

        // First set a custom icon color.
        composeRule.onNodeWithText("Icon").performClick()
        composeRule.onNodeWithTag("icon_match_system_toggle").performClick()
        composeRule.onNodeWithTag("color_wheel").performTouchInput {
            down(Offset(202f, 60f))
            up()
        }
        val effectiveIconBefore = getTextByTag("effective_icon_hex")

        // Then change text color and assert icon preview stays the same.
        composeRule.onNodeWithText("Text").performClick()
        composeRule.onNodeWithTag("color_wheel").performTouchInput {
            down(Offset(20f, 120f))
            up()
        }
        val effectiveIconAfter = getTextByTag("effective_icon_hex")

        assertEquals(
            "Text tab color changes should not alter icon preview color",
            effectiveIconBefore,
            effectiveIconAfter
        )
    }

    private fun getTextByTag(tag: String): String {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
    }

    private fun setPremiumEnabled(enabled: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("premium", enabled)
            .putBoolean("custom_color_mode", true)
            .putString("app_icon_color_mode", "system")
            .commit()
    }
}
