package com.juliacai.apptick.newAppLimit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.groups.AppLimitGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetTimeLimitsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun setTimeLimitsScreen_displaysCorrectly() {
        composeTestRule.setContent {
            // We can't easily mock the internal viewModel() call without Hilt or complex setup in this simple test.
            // However, we can test that the screen attempts to render.
            // If the ViewModel creation fails, this test will fail, which is also a valuable signal.
            // For a robust test, we would ideally pass the state as parameters instead of the ViewModel,
            // but for now we test integration with the current architecture.
            SetTimeLimitsScreen(
                onFinish = {},
                onCancel = {},
                onEditApps = {}
            )
        }

        // Verify Title
        composeTestRule.onNodeWithText("Set Time Limits").assertExists()
        
        // Verify Day Buttons (checking a few)
        composeTestRule.onNodeWithText("Mo").assertExists()
        composeTestRule.onNodeWithText("Tu").assertExists()
        composeTestRule.onNodeWithText("We").assertExists()
        
        // Verify Sections
        composeTestRule.onNodeWithText("App Limit Group Name").assertExists()
        composeTestRule.onNodeWithText("Enable Time Limit").assertExists()
    }
}
