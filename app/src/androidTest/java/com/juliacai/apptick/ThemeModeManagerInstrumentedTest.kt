package com.juliacai.apptick

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeModeManagerInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE).edit {
            putBoolean("premium", true)
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @After
    fun tearDown() {
        clearPrefs()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun persistDarkMode_true_disablesCustomColorMode() {
        ThemeModeManager.persistCustomColorMode(context, true)
        ThemeModeManager.persistDarkMode(context, true)

        assertThat(ThemeModeManager.isDarkModeEnabled(context)).isTrue()
        assertThat(ThemeModeManager.isCustomColorModeEnabled(context)).isFalse()
    }

    @Test
    fun persistCustomColorMode_true_disablesDarkMode() {
        ThemeModeManager.persistDarkMode(context, true)
        ThemeModeManager.persistCustomColorMode(context, true)

        assertThat(ThemeModeManager.isCustomColorModeEnabled(context)).isTrue()
        assertThat(ThemeModeManager.isDarkModeEnabled(context)).isFalse()
    }

    @Test
    fun isDarkModeEnabled_migratesLegacyValue_whenUiPrefsMissing() {
        val legacy = context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE)
        legacy.edit { putBoolean("dark_mode", true) }

        val enabled = ThemeModeManager.isDarkModeEnabled(context)
        val migrated =
            context.getSharedPreferences("AppTickPrefs", Context.MODE_PRIVATE)
                .getBoolean("dark_mode", false)

        assertThat(enabled).isTrue()
        assertThat(migrated).isTrue()
    }

    @Test
    fun isDarkModeEnabled_returnsFalse_whenCustomColorModeEnabled() {
        val uiPrefs = context.getSharedPreferences("AppTickPrefs", Context.MODE_PRIVATE)
        uiPrefs.edit {
            putBoolean("dark_mode", true)
            putBoolean("custom_color_mode", true)
        }

        assertThat(ThemeModeManager.isDarkModeEnabled(context)).isFalse()
    }

    @Test
    fun apply_usesNightNo_whenCustomColorModeEnabled() {
        ThemeModeManager.persistDarkMode(context, true)
        ThemeModeManager.persistCustomColorMode(context, true)

        ThemeModeManager.apply(context)

        assertThat(AppCompatDelegate.getDefaultNightMode()).isEqualTo(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun apply_usesNightYes_whenDarkModeEnabledWithoutCustomColorMode() {
        ThemeModeManager.persistDarkMode(context, true)

        ThemeModeManager.apply(context)

        assertThat(AppCompatDelegate.getDefaultNightMode()).isEqualTo(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @Test
    fun nonPremium_cannotEnableDarkOrCustomModes() {
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE).edit {
            putBoolean("premium", false)
        }

        ThemeModeManager.persistDarkMode(context, true)
        ThemeModeManager.persistCustomColorMode(context, true)

        assertThat(ThemeModeManager.isDarkModeEnabled(context)).isFalse()
        assertThat(ThemeModeManager.isCustomColorModeEnabled(context)).isFalse()
    }

    private fun clearPrefs() {
        context.getSharedPreferences("AppTickPrefs", Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE).edit { clear() }
    }
}
