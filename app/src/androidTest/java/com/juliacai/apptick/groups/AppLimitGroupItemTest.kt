package com.juliacai.apptick.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLimitGroupItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenEditingLocked_pauseAndEditAreReplacedWithLockIcons() {
        var lockClicks = 0
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(),
                isEditingLocked = true,
                onLockClick = { lockClicks++ },
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        // Both action slots should be lock actions while lock mode is active.
        composeTestRule.onNodeWithContentDescription("Unlock to pause").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Unlock to edit").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Unlock to pause").performClick()
        composeTestRule.onNodeWithContentDescription("Unlock to edit").performClick()
        assertThat(lockClicks).isEqualTo(2)
    }

    @Test
    fun whenEditingUnlocked_pauseAndEditButtonsAreShown() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(),
                isEditingLocked = false,
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        // Normal controls should be available when no lock mode is active.
        composeTestRule.onNodeWithContentDescription("Toggle Pause").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("PAUSED").assertCountEquals(0)
    }

    @Test
    fun pausedState_showsPausedTextBesideToggleControl() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(paused = true),
                isEditingLocked = false,
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Toggle Pause").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
    }

    private fun sampleGroup(paused: Boolean = false): AppLimitGroup {
        return AppLimitGroup(
            id = 1L,
            name = "Focus",
            apps = listOf(AppInGroup("Calculator", "com.android.calculator2", null)),
            paused = paused
        )
    }
}
