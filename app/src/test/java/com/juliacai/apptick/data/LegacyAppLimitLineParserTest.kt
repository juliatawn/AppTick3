package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LegacyAppLimitLineParserTest {

    @Test
    fun parseLineToEntity_convertsLegacyFieldsToCurrentSchema() {
        val legacyLine =
            "0:1700000000000:1:30:true:Focus:2:[1, 2, 7]:[com.instagram.android, com.google.android.youtube]:false"

        val parsed = LegacyAppLimitLineParser.parseLineToEntity(legacyLine, nowMillis = 1700000000000L)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("Focus")
        assertThat(parsed.resetMinutes).isEqualTo(120)
        assertThat(parsed.weekDays).containsExactly(1, 6, 7).inOrder()
        assertThat(parsed.apps.map { it.appPackage })
            .containsExactly("com.instagram.android", "com.google.android.youtube")
            .inOrder()
        assertThat(parsed.timeRemaining).isEqualTo(5_400_000L)
    }

    @Test
    fun parseLineToEntity_usesResolverLabelWhenAvailable() {
        val legacyLine =
            "0:1700000000000:1:30:true:Focus:2:[1, 2, 7]:[com.supercell.clashofclans]:false"

        val parsed = LegacyAppLimitLineParser.parseLineToEntity(
            line = legacyLine,
            nowMillis = 1700000000000L,
            appNameResolver = { packageName ->
                if (packageName == "com.supercell.clashofclans") "Clash of Clans" else null
            }
        )

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.apps.single().appName).isEqualTo("Clash of Clans")
    }

    @Test
    fun parseLineToEntity_handlesEmptyLists() {
        val legacyLine = "1:1700000000000:0:0:false::0:[]:[]:true"

        val parsed = LegacyAppLimitLineParser.parseLineToEntity(legacyLine, nowMillis = 1700000000000L)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.name).isEqualTo("App Limit Group")
        assertThat(parsed.weekDays).isEmpty()
        assertThat(parsed.apps).isEmpty()
        assertThat(parsed.paused).isTrue()
    }

    @Test
    fun parseLineToEntity_returnsNullForMalformedLine() {
        val parsed = LegacyAppLimitLineParser.parseLineToEntity("invalid:line")
        assertThat(parsed).isNull()
    }
}
