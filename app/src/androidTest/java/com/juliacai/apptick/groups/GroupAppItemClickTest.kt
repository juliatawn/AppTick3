package com.juliacai.apptick.groups

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.AppTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupAppItemClickTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        assumeTrue(
            "Compose-only tests are stable on emulator in this suite",
            Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true)
        )
    }

    @Test
    fun groupAppItem_withOnClick_invokesCallbackOnTap() {
        var clicked = false
        composeTestRule.setContent {
            AppTheme {
                GroupAppItem(
                    appInfo = AppInfo(
                        appName = "Chrome",
                        appPackage = "com.android.chrome",
                        appTimeUse = 28 * 60_000L
                    ),
                    timeLimit = 60,
                    limitEach = true,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Chrome").performClick()
        composeTestRule.waitForIdle()
        assertThat(clicked).isTrue()
    }

    @Test
    fun groupAppItem_withoutOnClick_doesNotCrashOnTap() {
        composeTestRule.setContent {
            AppTheme {
                GroupAppItem(
                    appInfo = AppInfo(
                        appName = "Settings",
                        appPackage = "com.android.settings",
                        appTimeUse = 10 * 60_000L
                    ),
                    timeLimit = 120,
                    limitEach = false
                )
            }
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        // No onClick set - tapping should not cause any crash
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun groupAppItem_withNullOnClick_doesNotCrashOnTap() {
        composeTestRule.setContent {
            AppTheme {
                GroupAppItem(
                    appInfo = AppInfo(
                        appName = "YouTube",
                        appPackage = "com.google.android.youtube",
                        appTimeUse = 0L
                    ),
                    timeLimit = 90,
                    limitEach = true,
                    onClick = null
                )
            }
        }

        composeTestRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeTestRule.onNodeWithText("YouTube").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun groupAppItem_displaysUsageInfo() {
        composeTestRule.setContent {
            AppTheme {
                GroupAppItem(
                    appInfo = AppInfo(
                        appName = "Instagram",
                        appPackage = "com.instagram.android",
                        appTimeUse = 45 * 60_000L
                    ),
                    timeLimit = 60,
                    limitEach = true,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNodeWithText("45 min used").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 min remaining").assertIsDisplayed()
    }

    @Test
    fun groupAppItem_sharedLimit_displaysSharedTimeRemaining() {
        composeTestRule.setContent {
            AppTheme {
                GroupAppItem(
                    appInfo = AppInfo(
                        appName = "Twitter",
                        appPackage = "com.twitter.android",
                        appTimeUse = 20 * 60_000L
                    ),
                    timeLimit = 120,
                    limitEach = false,
                    sharedTimeRemainingMinutes = 50,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Twitter").assertIsDisplayed()
        composeTestRule.onNodeWithText("50 min remaining").assertIsDisplayed()
    }
}
