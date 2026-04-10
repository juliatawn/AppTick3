package com.juliacai.apptick.deviceApps

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.AppTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUsagePageTest {

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
    fun appHeaderCard_displaysAppNameOnly() {
        composeTestRule.setContent {
            AppTheme {
                AppHeaderCard(appName = "YouTube", appPackage = "com.google.android.youtube")
            }
        }

        composeTestRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeTestRule.onNodeWithText("com.google.android.youtube").assertDoesNotExist()
    }

    @Test
    fun usageDetailCard_displaysNoUsageForZeroMillis() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.DAY, usageMillis = 0L)
            }
        }

        composeTestRule.onNodeWithText("Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("No usage").assertIsDisplayed()
    }

    @Test
    fun usageDetailCard_displaysFormattedTimeForNonZero() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.WEEK, usageMillis = 2 * 3_600_000L + 15 * 60_000L)
            }
        }

        composeTestRule.onNodeWithText("Week").assertIsDisplayed()
        composeTestRule.onNodeWithText("2h 15m").assertIsDisplayed()
    }

    @Test
    fun usageDetailCard_showsDailyAvgForWeek() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.WEEK, usageMillis = 7 * 60 * 60_000L)
            }
        }

        composeTestRule.onNodeWithText("Daily avg").assertIsDisplayed()
    }

    @Test
    fun usageDetailCard_showsDailyAvgForMonth() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.MONTH, usageMillis = 30 * 60 * 60_000L)
            }
        }

        composeTestRule.onNodeWithText("Daily avg").assertIsDisplayed()
    }

    @Test
    fun usageDetailCard_showsMonthlyAvgForYear() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.YEAR, usageMillis = 120 * 3_600_000L)
            }
        }

        composeTestRule.onNodeWithText("Monthly avg").assertIsDisplayed()
    }

    @Test
    fun usageDetailCard_noAvgShownForDay() {
        composeTestRule.setContent {
            AppTheme {
                UsageDetailCard(period = UsagePeriod.DAY, usageMillis = 3_600_000L)
            }
        }

        composeTestRule.onNodeWithText("Daily avg").assertDoesNotExist()
        composeTestRule.onNodeWithText("Monthly avg").assertDoesNotExist()
    }

    @Test
    fun usageOverviewCard_displaysAllPeriodLabels() {
        val usageMap = mapOf(
            UsagePeriod.DAY to 60_000L,
            UsagePeriod.WEEK to 420_000L,
            UsagePeriod.MONTH to 1_800_000L,
            UsagePeriod.YEAR to 21_600_000L
        )

        composeTestRule.setContent {
            AppTheme {
                UsageOverviewCard(usageMillis = usageMap)
            }
        }

        composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Week").assertIsDisplayed()
        composeTestRule.onNodeWithText("Month").assertIsDisplayed()
        composeTestRule.onNodeWithText("Year").assertIsDisplayed()
    }

    @Test
    fun usageOverviewCard_emptyMap_displaysNothing() {
        composeTestRule.setContent {
            AppTheme {
                UsageOverviewCard(usageMillis = emptyMap())
            }
        }

        composeTestRule.onNodeWithText("Overview").assertDoesNotExist()
    }

    @Test
    fun usageOverviewCard_displaysFormattedValues() {
        val usageMap = mapOf(
            UsagePeriod.DAY to 45 * 60_000L,
            UsagePeriod.WEEK to 5 * 3_600_000L,
            UsagePeriod.MONTH to 0L,
            UsagePeriod.YEAR to 100 * 3_600_000L
        )

        composeTestRule.setContent {
            AppTheme {
                UsageOverviewCard(usageMillis = usageMap)
            }
        }

        composeTestRule.onNodeWithText("45m").assertIsDisplayed()
        composeTestRule.onNodeWithText("5h 0m").assertIsDisplayed()
        composeTestRule.onNodeWithText("No usage").assertIsDisplayed()
        composeTestRule.onNodeWithText("100h 0m").assertIsDisplayed()
    }
}
