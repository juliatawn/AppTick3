package com.juliacai.apptick

import androidx.compose.ui.test.assertIsDisplayed

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Test
    fun mainScreenDisplaysTitleAndEmptyStateMessage() {
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                listContent = {}
            )
        }

        composeTestRule.onNodeWithText("AppTick").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a new app limit group +").assertIsDisplayed()
    }

    @Test
    fun mainScreenRendersProvidedListContentWhenGroupsExist() {
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 2,
                showLockedIcon = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                listContent = { androidx.compose.material3.Text("Group list content") }
            )
        }

        composeTestRule.onNodeWithText("Group list content").assertIsDisplayed()
    }

    @Test
    fun mainScreenPremiumButtonClickInvokesCallback() {
        var clicked = false
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = { clicked = true },
                listContent = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Open lock modes").performClick()
        assertThat(clicked).isTrue()
    }

    @Test
    fun mainScreenShowsLockedIconWhenLockModesLocked() {
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = true,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                listContent = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Lock modes are locked").assertIsDisplayed()
    }
}
