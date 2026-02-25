package com.juliacai.apptick.groups

import android.os.Build
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLimitGroupItemTest {

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
    fun whenEditingLocked_pauseAndEditAreReplacedWithLockIcons() {
        var lockClicks = 0
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(),
                isExpanded = true,
                isEditingLocked = true,
                onExpandToggle = {},
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
                isExpanded = true,
                isEditingLocked = false,
                onExpandToggle = {},
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
                isExpanded = true,
                isEditingLocked = false,
                onExpandToggle = {},
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Toggle Pause").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
    }

    @Test
    fun showsConfiguredTimeLimitAmount() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(timeMinLimit = 10),
                isExpanded = true,
                isEditingLocked = false,
                onExpandToggle = {},
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onNodeWithText("Time limit: 10 min").assertIsDisplayed()
    }

    @Test
    fun showsTimeLeftLine() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(timeMinLimit = 30, timeRemaining = 25 * 60_000L),
                isExpanded = true,
                isEditingLocked = false,
                onExpandToggle = {},
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onNodeWithText("Time left: 25 min").assertIsDisplayed()
    }

    @Test
    fun collapsedState_hidesExpandedDetails_butShowsCoreInfo() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(timeMinLimit = 10, timeRemaining = 25 * 60_000L),
                isExpanded = false,
                isEditingLocked = false,
                onExpandToggle = {},
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onNodeWithText("Time limit: 10 min").assertIsDisplayed()
        composeTestRule.onNodeWithText("Time left: 25 min").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Active days: Everyday").assertCountEquals(0)
    }

    @Test
    fun pausedGroup_hidesTimeLeftLine() {
        composeTestRule.setContent {
            AppLimitGroupItem(
                group = sampleGroup(paused = true, timeMinLimit = 30, timeRemaining = 25 * 60_000L),
                isExpanded = true,
                isEditingLocked = false,
                onExpandToggle = {},
                onLockClick = {},
                onPauseToggle = {},
                onEdit = {},
                onDelete = {}
            )
        }

        composeTestRule.onAllNodesWithText("Time left: 25 min").assertCountEquals(0)
    }

    private fun sampleGroup(
        paused: Boolean = false,
        timeHrLimit: Int = 0,
        timeMinLimit: Int = 0,
        timeRemaining: Long = 0L
    ): AppLimitGroup {
        return AppLimitGroup(
            id = 1L,
            name = "Focus",
            apps = listOf(AppInGroup("Calculator", "com.android.calculator2", null)),
            paused = paused,
            timeHrLimit = timeHrLimit,
            timeMinLimit = timeMinLimit,
            timeRemaining = timeRemaining
        )
    }
}
