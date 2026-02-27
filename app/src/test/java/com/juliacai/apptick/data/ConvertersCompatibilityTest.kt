package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConvertersCompatibilityTest {

    private val converters = Converters()

    @Test
    fun toAppInGroupList_parsesObfuscatedKeys() {
        val json = """[{"a":"YouTube","b":"com.google.android.youtube","c":null}]"""

        val parsed = converters.toAppInGroupList(json)

        assertThat(parsed).hasSize(1)
        assertThat(parsed.first().appName).isEqualTo("YouTube")
        assertThat(parsed.first().appPackage).isEqualTo("com.google.android.youtube")
    }

    @Test
    fun toTimeRangeList_parsesObfuscatedKeys() {
        val json = """[{"a":9,"b":0,"c":22,"d":0}]"""

        val parsed = converters.toTimeRangeList(json)

        assertThat(parsed).hasSize(1)
        assertThat(parsed.first().startHour).isEqualTo(9)
        assertThat(parsed.first().endHour).isEqualTo(22)
    }

    @Test
    fun toAppUsageStatList_parsesObfuscatedKeys() {
        val json = """[{"a":"com.google.android.youtube","b":12345}]"""

        val parsed = converters.toAppUsageStatList(json)

        assertThat(parsed).hasSize(1)
        assertThat(parsed.first().appPackage).isEqualTo("com.google.android.youtube")
        assertThat(parsed.first().usedMillis).isEqualTo(12345L)
    }
}
