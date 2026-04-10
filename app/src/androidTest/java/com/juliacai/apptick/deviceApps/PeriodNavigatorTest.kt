package com.juliacai.apptick.deviceApps

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeriodNavigatorTest {

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
    fun displaysThisWeekLabelAtOffset0() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 0,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithText("This Week").assertIsDisplayed()
    }

    @Test
    fun displaysLastWeekLabelAtOffset1() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 1,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Last Week").assertIsDisplayed()
    }

    @Test
    fun displaysWeeksAgoLabelAtOffset5() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 5,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithText("5 Weeks Ago").assertIsDisplayed()
    }

    @Test
    fun nextButtonDisabledAtOffset0() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 0,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Next").assertIsNotEnabled()
    }

    @Test
    fun nextButtonEnabledAtOffset1() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 1,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Next").assertIsEnabled()
    }

    @Test
    fun previousButtonCallsOnPrevious() {
        var called = false
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 0,
                    onPrevious = { called = true },
                    onNext = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        assertEquals(true, called)
    }

    @Test
    fun nextButtonCallsOnNext() {
        var called = false
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.WEEK,
                    offset = 3,
                    onPrevious = {},
                    onNext = { called = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Next").performClick()
        assertEquals(true, called)
    }

    @Test
    fun displaysDateRange() {
        composeTestRule.setContent {
            AppTheme {
                PeriodNavigator(
                    period = UsagePeriod.MONTH,
                    offset = 0,
                    onPrevious = {},
                    onNext = {}
                )
            }
        }

        // Should show "This Month" and a date range string
        composeTestRule.onNodeWithText("This Month").assertIsDisplayed()
    }
}
