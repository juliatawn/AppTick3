package com.juliacai.apptick.deviceApps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeriodLabelTest {

    @Test
    fun `day offset 0 is Today`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.DAY, 0)).isEqualTo("Today")
    }

    @Test
    fun `day offset 1 is Yesterday`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.DAY, 1)).isEqualTo("Yesterday")
    }

    @Test
    fun `day offset 5 is 5 Days Ago`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.DAY, 5)).isEqualTo("5 Days Ago")
    }

    @Test
    fun `week offset 0 is This Week`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.WEEK, 0)).isEqualTo("This Week")
    }

    @Test
    fun `week offset 1 is Last Week`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.WEEK, 1)).isEqualTo("Last Week")
    }

    @Test
    fun `week offset 5 is 5 Weeks Ago`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.WEEK, 5)).isEqualTo("5 Weeks Ago")
    }

    @Test
    fun `month offset 0 is This Month`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.MONTH, 0)).isEqualTo("This Month")
    }

    @Test
    fun `month offset 1 is Last Month`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.MONTH, 1)).isEqualTo("Last Month")
    }

    @Test
    fun `month offset 3 is 3 Months Ago`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.MONTH, 3)).isEqualTo("3 Months Ago")
    }

    @Test
    fun `year offset 0 is This Year`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.YEAR, 0)).isEqualTo("This Year")
    }

    @Test
    fun `year offset 1 is Last Year`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.YEAR, 1)).isEqualTo("Last Year")
    }

    @Test
    fun `year offset 2 is 2 Years Ago`() {
        assertThat(AppUsageStats.periodLabel(UsagePeriod.YEAR, 2)).isEqualTo("2 Years Ago")
    }

    @Test
    fun `periodDateRange returns non-empty string`() {
        // Just verify it doesn't crash and returns something
        val range = AppUsageStats.periodDateRange(UsagePeriod.WEEK, 0)
        assertThat(range).isNotEmpty()
    }

    @Test
    fun `periodDateRange for day offset 0 contains day of week`() {
        val range = AppUsageStats.periodDateRange(UsagePeriod.DAY, 0)
        val weekdays = java.text.DateFormatSymbols.getInstance().weekdays
        val todayName = weekdays[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)]
        assertThat(range).contains(todayName)
    }

    @Test
    fun `periodRange start is before end`() {
        for (period in UsagePeriod.entries) {
            for (offset in 0..3) {
                val (start, end) = AppUsageStats.periodRange(period, offset)
                assertThat(start).isLessThan(end)
            }
        }
    }

    @Test
    fun `periodRange offset 1 ends before offset 0 starts for week`() {
        val (_, end1) = AppUsageStats.periodRange(UsagePeriod.WEEK, 1)
        val (start0, _) = AppUsageStats.periodRange(UsagePeriod.WEEK, 0)
        // offset=1 should cover a time range before offset=0
        assertThat(end1).isLessThan(start0)
    }
}
