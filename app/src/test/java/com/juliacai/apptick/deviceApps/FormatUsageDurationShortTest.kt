package com.juliacai.apptick.deviceApps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatUsageDurationShortTest {

    @Test
    fun `zero millis returns empty string`() {
        assertThat(formatUsageDurationShort(0)).isEmpty()
    }

    @Test
    fun `negative millis returns empty string`() {
        assertThat(formatUsageDurationShort(-5000)).isEmpty()
    }

    @Test
    fun `less than one minute returns less than 1m`() {
        assertThat(formatUsageDurationShort(30_000)).isEqualTo("<1m")
    }

    @Test
    fun `exactly one minute`() {
        assertThat(formatUsageDurationShort(60_000)).isEqualTo("1m")
    }

    @Test
    fun `multiple minutes no hours`() {
        assertThat(formatUsageDurationShort(45 * 60_000L)).isEqualTo("45m")
    }

    @Test
    fun `exactly one hour no trailing minutes`() {
        assertThat(formatUsageDurationShort(3_600_000)).isEqualTo("1h")
    }

    @Test
    fun `hours and minutes compact`() {
        assertThat(formatUsageDurationShort(5 * 3_600_000L + 23 * 60_000L)).isEqualTo("5h23m")
    }

    @Test
    fun `large value many hours`() {
        assertThat(formatUsageDurationShort(100 * 3_600_000L + 5 * 60_000L)).isEqualTo("100h5m")
    }
}
