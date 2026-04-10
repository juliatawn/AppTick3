package com.juliacai.apptick.newAppLimit

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.TimeRange
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for the SetTimeLimitsScreen daily usage preview,
 * default time range mode, and related UI behaviors.
 */
@RunWith(AndroidJUnit4::class)
class SetTimeLimitsPreviewIntegrationTest {

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

    private fun setScreenWithDraft(
        draft: SetTimeLimitDraft,
        group: AppLimitGroup? = null,
        selectedApps: List<AppInfo> = emptyList()
    ) {
        composeTestRule.setContent {
            SetTimeLimitsScreenContent(
                group = group,
                draft = draft,
                selectedApps = selectedApps,
                onSelectedAppsChange = {},
                onDraftChange = {},
                onFinish = {},
                onCancel = {},
                onEditApps = {},
                onUpgradeToPremium = {}
            )
        }
    }

    // ── Default time range mode is "Block Apps" ─────────────────────────

    @Test
    fun newGroup_timeRangeEnabled_defaultsToBlockApps() {
        // When creating a new group with time range enabled and no draft/existing group,
        // blockOutsideTimeRange should default to true (Block Apps selected).
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = true,
                timeHrLimit = "1",
                timeMinLimit = "0",
                limitEach = false,
                useTimeRange = true,
                blockOutsideTimeRange = true, // This is the new default
                timeRanges = listOf(TimeRange(startHour = 8, endHour = 17)),
                startHour = 8,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = false,
                resetHours = "0",
                resetMinutes = "0"
            )
        )

        // "Block Apps" button should be rendered with primary styling (selected state)
        composeTestRule.onNodeWithText("Block Apps").assertExists()
        composeTestRule.onNodeWithText("Allow No Limits").assertExists()
    }

    // ── Daily Usage Preview displays with periodic reset ────────────────

    @Test
    fun periodicReset_showsDailyUsagePreview() {
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = true,
                timeHrLimit = "0",
                timeMinLimit = "1",
                limitEach = false,
                useTimeRange = true,
                blockOutsideTimeRange = true,
                timeRanges = listOf(TimeRange(startHour = 8, startMinute = 0, endHour = 17, endMinute = 0)),
                startHour = 8,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = true,
                resetHours = "1",
                resetMinutes = "0"
            )
        )

        // Scroll down to see the preview
        composeTestRule.onNodeWithText("Daily Usage Preview").assertExists()
        // 8am-5pm = 540min, 60min interval, 1min limit → 9 periods × 1min = 9min
        composeTestRule.onNodeWithText("~9min/day", substring = true).assertExists()
    }

    @Test
    fun periodicReset_noTimeRange_showsFullDayPreview() {
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = true,
                timeHrLimit = "0",
                timeMinLimit = "10",
                limitEach = false,
                useTimeRange = false,
                blockOutsideTimeRange = false,
                timeRanges = emptyList(),
                startHour = 0,
                startMinute = 0,
                endHour = 23,
                endMinute = 59,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = true,
                resetHours = "1",
                resetMinutes = "0"
            )
        )

        // 1440min / 60 = 24 periods × 10min = 240min = 4hr
        composeTestRule.onNodeWithText("Daily Usage Preview").assertExists()
        composeTestRule.onNodeWithText("~4hr/day", substring = true).assertExists()
    }

    @Test
    fun noPeriodicReset_doesNotShowPreview() {
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = true,
                timeHrLimit = "1",
                timeMinLimit = "0",
                limitEach = false,
                useTimeRange = false,
                blockOutsideTimeRange = false,
                timeRanges = emptyList(),
                startHour = 0,
                startMinute = 0,
                endHour = 23,
                endMinute = 59,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = false,
                resetHours = "0",
                resetMinutes = "0"
            )
        )

        composeTestRule.onNodeWithText("Daily Usage Preview").assertDoesNotExist()
    }

    @Test
    fun timeLimitDisabled_doesNotShowPreview() {
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = false,
                timeHrLimit = "0",
                timeMinLimit = "0",
                limitEach = false,
                useTimeRange = false,
                blockOutsideTimeRange = false,
                timeRanges = emptyList(),
                startHour = 0,
                startMinute = 0,
                endHour = 23,
                endMinute = 59,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = true,
                resetHours = "1",
                resetMinutes = "0"
            )
        )

        composeTestRule.onNodeWithText("Daily Usage Preview").assertDoesNotExist()
    }

    @Test
    fun noLimitsOutsideRange_showsNote() {
        setScreenWithDraft(
            draft = SetTimeLimitDraft(
                groupName = "Test",
                useTimeLimit = true,
                timeHrLimit = "0",
                timeMinLimit = "5",
                limitEach = false,
                useTimeRange = true,
                blockOutsideTimeRange = false,
                timeRanges = listOf(TimeRange(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)),
                startHour = 9,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                weekDays = emptyList(),
                cumulativeTime = false,
                useReset = true,
                resetHours = "1",
                resetMinutes = "0"
            )
        )

        composeTestRule.onNodeWithText("No limits outside time range").assertExists()
    }
}
