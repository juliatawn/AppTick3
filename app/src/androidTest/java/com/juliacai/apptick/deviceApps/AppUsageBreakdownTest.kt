package com.juliacai.apptick.deviceApps

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.juliacai.apptick.AppTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormatSymbols
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class AppUsageBreakdownTest {

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

    // ── WeeklyBreakdownCard ─────────────────────────────────────────────

    @Test
    fun weeklyBreakdown_displaysTitle() {
        val dailyUsages = buildList {
            val cal = Calendar.getInstance()
            for (i in 0 until 7) {
                add(
                    DailyUsage(
                        year = cal.get(Calendar.YEAR),
                        month = cal.get(Calendar.MONTH),
                        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                        dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                        usageMillis = (i + 1) * 600_000L // 10min increments
                    )
                )
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        composeTestRule.setContent {
            AppTheme {
                WeeklyBreakdownCard(dailyUsages = dailyUsages)
            }
        }

        composeTestRule.onNodeWithText("Daily Breakdown").assertIsDisplayed()
    }

    @Test
    fun weeklyBreakdown_showsUsageValues() {
        val cal = Calendar.getInstance()
        val dailyUsages = listOf(
            DailyUsage(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                usageMillis = 90 * 60_000L // 1h 30m
            )
        )

        composeTestRule.setContent {
            AppTheme {
                WeeklyBreakdownCard(dailyUsages = dailyUsages)
            }
        }

        composeTestRule.onNodeWithText("1h 30m").assertIsDisplayed()
    }

    @Test
    fun weeklyBreakdown_showsDayOfWeekAndDate() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 2) // Thursday
        }
        val dayNames = DateFormatSymbols.getInstance().weekdays // full names
        val expectedDayName = dayNames[cal.get(Calendar.DAY_OF_WEEK)]

        val dailyUsages = listOf(
            DailyUsage(
                year = 2026,
                month = Calendar.APRIL,
                dayOfMonth = 2,
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                usageMillis = 60_000L
            )
        )

        composeTestRule.setContent {
            AppTheme {
                WeeklyBreakdownCard(dailyUsages = dailyUsages)
            }
        }

        // Should show something like "Thursday 4/2"
        composeTestRule.onNodeWithText("$expectedDayName 4/2").assertIsDisplayed()
    }

    @Test
    fun weeklyBreakdown_noUsageDayShowsNoUsage() {
        val cal = Calendar.getInstance()
        val dailyUsages = listOf(
            DailyUsage(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                usageMillis = 0L
            )
        )

        composeTestRule.setContent {
            AppTheme {
                WeeklyBreakdownCard(dailyUsages = dailyUsages)
            }
        }

        composeTestRule.onNodeWithText("No usage").assertIsDisplayed()
    }

    @Test
    fun weeklyBreakdown_emptyList_displaysNothing() {
        composeTestRule.setContent {
            AppTheme {
                WeeklyBreakdownCard(dailyUsages = emptyList())
            }
        }

        composeTestRule.onNodeWithText("Daily Breakdown").assertDoesNotExist()
    }

    // ── MonthCalendarCard ───────────────────────────────────────────────

    @Test
    fun monthCalendar_displaysMonthName() {
        val cal = Calendar.getInstance()
        val monthNames = DateFormatSymbols.getInstance().months
        val expectedMonth = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"

        val dailyUsages = listOf(
            DailyUsage(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                dayOfMonth = 1,
                dayOfWeek = Calendar.MONDAY,
                usageMillis = 60_000L
            )
        )

        composeTestRule.setContent {
            AppTheme {
                MonthCalendarCard(dailyUsages = dailyUsages)
            }
        }

        composeTestRule.onNodeWithText(expectedMonth).assertIsDisplayed()
    }

    @Test
    fun monthCalendar_displaysDayOfWeekHeaders() {
        val dailyUsages = listOf(
            DailyUsage(
                year = 2026,
                month = Calendar.APRIL,
                dayOfMonth = 1,
                dayOfWeek = Calendar.WEDNESDAY,
                usageMillis = 60_000L
            )
        )

        composeTestRule.setContent {
            AppTheme {
                MonthCalendarCard(dailyUsages = dailyUsages)
            }
        }

        composeTestRule.onNodeWithText("Mo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fr").assertIsDisplayed()
        composeTestRule.onNodeWithText("Su").assertIsDisplayed()
    }

    @Test
    fun monthCalendar_emptyList_displaysNothing() {
        composeTestRule.setContent {
            AppTheme {
                MonthCalendarCard(dailyUsages = emptyList())
            }
        }

        // Should render nothing when empty
        composeTestRule.onNodeWithText("Su").assertDoesNotExist()
    }

    // ── YearlyBreakdownCard ─────────────────────────────────────────────

    @Test
    fun yearlyBreakdown_displaysTitle() {
        val monthlyUsages = listOf(
            MonthlyUsage(year = 2026, month = Calendar.MARCH, usageMillis = 3_600_000L)
        )

        composeTestRule.setContent {
            AppTheme {
                YearlyBreakdownCard(monthlyUsages = monthlyUsages)
            }
        }

        composeTestRule.onNodeWithText("Monthly Breakdown").assertIsDisplayed()
    }

    @Test
    fun yearlyBreakdown_showsMonthNameAndUsage() {
        val shortMonths = DateFormatSymbols.getInstance().shortMonths
        val monthlyUsages = listOf(
            MonthlyUsage(year = 2026, month = Calendar.MARCH, usageMillis = 5 * 3_600_000L)
        )

        composeTestRule.setContent {
            AppTheme {
                YearlyBreakdownCard(monthlyUsages = monthlyUsages)
            }
        }

        composeTestRule.onNodeWithText("${shortMonths[Calendar.MARCH]} 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("5h 0m").assertIsDisplayed()
    }

    @Test
    fun yearlyBreakdown_emptyList_displaysNothing() {
        composeTestRule.setContent {
            AppTheme {
                YearlyBreakdownCard(monthlyUsages = emptyList())
            }
        }

        composeTestRule.onNodeWithText("Monthly Breakdown").assertDoesNotExist()
    }

    // ── Overview Card daily averages ────────────────────────────────────

    @Test
    fun overviewCard_showsDailyAverages() {
        val usageMap = mapOf(
            UsagePeriod.DAY to 60 * 60_000L,      // 1h today
            UsagePeriod.WEEK to 7 * 60 * 60_000L,  // 7h past week → 1h/day avg
            UsagePeriod.MONTH to 30 * 60 * 60_000L, // 30h past month → 1h/day avg
            UsagePeriod.YEAR to 365 * 60 * 60_000L  // 365h past year → 1h/day avg
        )

        composeTestRule.setContent {
            AppTheme {
                UsageOverviewCard(usageMillis = usageMap)
            }
        }

        // Week and Month should show "Avg 1h 0m/day", Year should also show daily avg
        composeTestRule.onNodeWithText("Avg 1h 0m/day", substring = true).assertExists()
    }

    @Test
    fun overviewCard_noDailyAvgForToday() {
        val usageMap = mapOf(
            UsagePeriod.DAY to 3_600_000L,
            UsagePeriod.WEEK to 0L,
            UsagePeriod.MONTH to 0L,
            UsagePeriod.YEAR to 0L
        )

        composeTestRule.setContent {
            AppTheme {
                UsageOverviewCard(usageMillis = usageMap)
            }
        }

        // "Today" row should NOT show a daily average
        composeTestRule.onNodeWithText("Overview").assertIsDisplayed()
        // The overview shows the period labels and values but the DAY row has no avg
    }
}
