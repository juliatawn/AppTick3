package com.juliacai.apptick.deviceApps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class UsagePeriodTest {

    @Test
    fun `UsagePeriod entries has four periods`() {
        assertThat(UsagePeriod.entries).hasSize(4)
    }

    @Test
    fun `DAY uses DAY_OF_YEAR with minus one`() {
        assertThat(UsagePeriod.DAY.calendarField).isEqualTo(Calendar.DAY_OF_YEAR)
        assertThat(UsagePeriod.DAY.calendarAmount).isEqualTo(-1)
    }

    @Test
    fun `WEEK uses WEEK_OF_YEAR with minus one`() {
        assertThat(UsagePeriod.WEEK.calendarField).isEqualTo(Calendar.WEEK_OF_YEAR)
        assertThat(UsagePeriod.WEEK.calendarAmount).isEqualTo(-1)
    }

    @Test
    fun `MONTH uses MONTH with minus one`() {
        assertThat(UsagePeriod.MONTH.calendarField).isEqualTo(Calendar.MONTH)
        assertThat(UsagePeriod.MONTH.calendarAmount).isEqualTo(-1)
    }

    @Test
    fun `YEAR uses YEAR with minus one`() {
        assertThat(UsagePeriod.YEAR.calendarField).isEqualTo(Calendar.YEAR)
        assertThat(UsagePeriod.YEAR.calendarAmount).isEqualTo(-1)
    }

    @Test
    fun `labels are human readable`() {
        assertThat(UsagePeriod.DAY.label).isEqualTo("Day")
        assertThat(UsagePeriod.WEEK.label).isEqualTo("Week")
        assertThat(UsagePeriod.MONTH.label).isEqualTo("Month")
        assertThat(UsagePeriod.YEAR.label).isEqualTo("Year")
    }

    @Test
    fun `entries are ordered from shortest to longest`() {
        val entries = UsagePeriod.entries
        assertThat(entries[0]).isEqualTo(UsagePeriod.DAY)
        assertThat(entries[1]).isEqualTo(UsagePeriod.WEEK)
        assertThat(entries[2]).isEqualTo(UsagePeriod.MONTH)
        assertThat(entries[3]).isEqualTo(UsagePeriod.YEAR)
    }
}
