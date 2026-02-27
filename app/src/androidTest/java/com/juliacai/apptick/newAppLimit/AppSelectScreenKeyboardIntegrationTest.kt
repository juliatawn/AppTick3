package com.juliacai.apptick.newAppLimit

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSelectScreenKeyboardIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        assumeTrue(
            "Compose-only UI tests are stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
    }

    @Test
    fun searchField_imeActionClearsFocus() {
        val appLimitViewModel = ViewModelProvider(composeRule.activity)[AppLimitViewModel::class.java]

        composeRule.setContent {
            AppSelectScreen(
                viewModel = appLimitViewModel,
                onNextClick = {},
                onCancel = {}
            )
        }

        val searchField = composeRule.onNodeWithText("Search Apps")
        searchField.performClick()
        searchField.assertIsFocused()
        searchField.performImeAction()
        searchField.assertIsNotFocused()
    }
}
