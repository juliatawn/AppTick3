package com.juliacai.apptick.deviceApps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatUsageDurationTest {

    @Test
    fun `zero millis returns no usage`() {
        assertThat(formatUsageDuration(0)).isEqualTo("No usage")
    }

    @Test
    fun `negative millis returns no usage`() {
        assertThat(formatUsageDuration(-5000)).isEqualTo("No usage")
    }

    @Test
    fun `less than one minute returns less than 1m`() {
        assertThat(formatUsageDuration(30_000)).isEqualTo("<1m")
    }

    @Test
    fun `exactly one minute`() {
        assertThat(formatUsageDuration(60_000)).isEqualTo("1m")
    }

    @Test
    fun `multiple minutes no hours`() {
        assertThat(formatUsageDuration(45 * 60_000L)).isEqualTo("45m")
    }

    @Test
    fun `exactly one hour`() {
        assertThat(formatUsageDuration(3_600_000)).isEqualTo("1h 0m")
    }

    @Test
    fun `hours and minutes`() {
        assertThat(formatUsageDuration(5 * 3_600_000L + 23 * 60_000L)).isEqualTo("5h 23m")
    }

    @Test
    fun `large value many hours`() {
        assertThat(formatUsageDuration(100 * 3_600_000L + 5 * 60_000L)).isEqualTo("100h 5m")
    }

    @Test
    fun `one millisecond under one minute`() {
        assertThat(formatUsageDuration(59_999)).isEqualTo("<1m")
    }

    @Test
    fun `59 seconds returns less than 1m`() {
        assertThat(formatUsageDuration(59_000)).isEqualTo("<1m")
    }
}
