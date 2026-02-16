package com.juliacai.apptick

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppThemeTest {

    @Test
    fun shouldUseCustomColorMode_requiresPremiumAndUserToggle() {
        assertThat(shouldUseCustomColorMode(isPremium = true, customColorModeEnabled = true)).isTrue()
        assertThat(shouldUseCustomColorMode(isPremium = false, customColorModeEnabled = true)).isFalse()
        assertThat(shouldUseCustomColorMode(isPremium = true, customColorModeEnabled = false)).isFalse()
        assertThat(shouldUseCustomColorMode(isPremium = false, customColorModeEnabled = false)).isFalse()
    }
}
