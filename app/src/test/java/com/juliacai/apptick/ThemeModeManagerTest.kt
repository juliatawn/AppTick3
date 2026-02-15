package com.juliacai.apptick

import androidx.appcompat.app.AppCompatDelegate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeManagerTest {

    @Test
    fun resolveNightMode_customColorAlwaysForcesLightMode() {
        val mode = ThemeModeManager.resolveNightMode(customColorMode = true, darkMode = true)
        assertThat(mode).isEqualTo(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun resolveNightMode_darkModeEnabledUsesNightYes() {
        val mode = ThemeModeManager.resolveNightMode(customColorMode = false, darkMode = true)
        assertThat(mode).isEqualTo(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @Test
    fun resolveNightMode_bothDisabledUsesNightNo() {
        val mode = ThemeModeManager.resolveNightMode(customColorMode = false, darkMode = false)
        assertThat(mode).isEqualTo(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
