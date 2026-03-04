package com.juliacai.apptick

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        assumeTrue(
            "Compose-only MainScreen tests are stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
    }

    @Test
    fun mainScreenDisplaysTitleAndEmptyStateMessage() {
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = false,
                showGroupDetailsHint = false,
                showBatteryWarning = false,
                batteryWarningDismissable = false,
                batteryWarningText = "",
                batteryWarningDetails = emptyList(),
                hasOemRestrictions = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                onOpenAppBatterySettings = {},
                onOpenGeneralBatterySettings = {},
                onOpenDontKillMyApp = {},
                onOpenOemStartupSettings = {},
                onRefreshBatteryStatus = {},
                onDismissBatteryWarning = {},
                onDismissGroupDetailsHint = {},
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
                showGroupDetailsHint = false,
                showBatteryWarning = false,
                batteryWarningDismissable = false,
                batteryWarningText = "",
                batteryWarningDetails = emptyList(),
                hasOemRestrictions = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                onOpenAppBatterySettings = {},
                onOpenGeneralBatterySettings = {},
                onOpenDontKillMyApp = {},
                onOpenOemStartupSettings = {},
                onRefreshBatteryStatus = {},
                onDismissBatteryWarning = {},
                onDismissGroupDetailsHint = {},
                listContent = { androidx.compose.material3.Text("Group list content") }
            )
        }

        composeTestRule.onNodeWithText("Group list content").assertIsDisplayed()
    }

    @Test
    fun mainScreenPremiumButtonClickInvokesCallback() {
        assumeTrue(
            "Compose callback click assertion is unstable on some physical OEM devices",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )

        var clicked = false
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = false,
                showGroupDetailsHint = false,
                showBatteryWarning = false,
                batteryWarningDismissable = false,
                batteryWarningText = "",
                batteryWarningDetails = emptyList(),
                hasOemRestrictions = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = { clicked = true },
                onOpenAppBatterySettings = {},
                onOpenGeneralBatterySettings = {},
                onOpenDontKillMyApp = {},
                onOpenOemStartupSettings = {},
                onRefreshBatteryStatus = {},
                onDismissBatteryWarning = {},
                onDismissGroupDetailsHint = {},
                listContent = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Open lock modes").performClick()
        composeTestRule.waitForIdle()
        assertThat(clicked).isTrue()
    }

    @Test
    fun mainScreenShowsLockedIconWhenLockModesLocked() {
        composeTestRule.setContent {
            MainScreen(
                appLimitGroupCount = 0,
                showLockedIcon = true,
                showGroupDetailsHint = false,
                showBatteryWarning = false,
                batteryWarningDismissable = false,
                batteryWarningText = "",
                batteryWarningDetails = emptyList(),
                hasOemRestrictions = false,
                onFabClick = {},
                onSettingsClick = {},
                onPremiumClick = {},
                onOpenAppBatterySettings = {},
                onOpenGeneralBatterySettings = {},
                onOpenDontKillMyApp = {},
                onOpenOemStartupSettings = {},
                onRefreshBatteryStatus = {},
                onDismissBatteryWarning = {},
                onDismissGroupDetailsHint = {},
                listContent = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Lock modes are locked").assertIsDisplayed()
    }
}
