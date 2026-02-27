package com.juliacai.apptick

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

internal fun shouldUseCustomColorMode(isPremium: Boolean, customColorModeEnabled: Boolean): Boolean {
    return isPremium && customColorModeEnabled
}

data class ThemePalette(
    val primary: Int,
    val background: Int,
    val card: Int,
    val primaryContainer: Int,
    val onPrimary: Int,
    val onBackground: Int,
    val onCard: Int,
    val onPrimaryContainer: Int
)

object AppTheme {
    private const val PREFS_NAME = "groupPrefs"
    private const val KEY_PRIMARY_COLOR = "custom_primary_color"
    private const val KEY_ACCENT_COLOR = "custom_accent_color"
    private const val DEFAULT_PRIMARY_COLOR = "#6F34AD" // Match the AppTick logo purple.
    private const val DARK_BACKGROUND = "#101214"
    private const val DARK_CARD = "#171A1E"
    private const val LIGHT_BACKGROUND = "#F7F8FB"
    private const val LIGHT_CARD = "#FFFFFF"

    private fun readableColor(background: Int): Int {
        return if (ColorUtils.calculateLuminance(background) > 0.45) Color.BLACK else Color.WHITE
    }

    fun customPalette(seedColor: Int, isDark: Boolean): ThemePalette {
        val primary = if (isDark) {
            ColorUtils.blendARGB(seedColor, Color.WHITE, 0.18f)
        } else {
            ColorUtils.blendARGB(seedColor, Color.BLACK, 0.08f)
        }
        val background = if (isDark) {
            ColorUtils.blendARGB(seedColor, DARK_BACKGROUND.toColorInt(), 0.88f)
        } else {
            ColorUtils.blendARGB(seedColor, LIGHT_BACKGROUND.toColorInt(), 0.90f)
        }
        val card = if (isDark) {
            ColorUtils.blendARGB(seedColor, DARK_CARD.toColorInt(), 0.82f)
        } else {
            ColorUtils.blendARGB(seedColor, LIGHT_CARD.toColorInt(), 0.84f)
        }
        val primaryContainer = if (isDark) {
            ColorUtils.blendARGB(seedColor, Color.BLACK, 0.42f)
        } else {
            ColorUtils.blendARGB(seedColor, Color.WHITE, 0.72f)
        }

        return ThemePalette(
            primary = primary,
            background = background,
            card = card,
            primaryContainer = primaryContainer,
            onPrimary = readableColor(primary),
            onBackground = readableColor(background),
            onCard = readableColor(card),
            onPrimaryContainer = readableColor(primaryContainer)
        )
    }

    private fun resolveSeedColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val premiumEnabled = prefs.getBoolean("premium", false)
        val customModeEnabled =
            shouldUseCustomColorMode(premiumEnabled, ThemeModeManager.isCustomColorModeEnabled(context))

        return if (customModeEnabled) {
            prefs.getInt(KEY_PRIMARY_COLOR, DEFAULT_PRIMARY_COLOR.toColorInt())
        } else {
            DEFAULT_PRIMARY_COLOR.toColorInt()
        }
    }

    fun currentPalette(context: Context, isDark: Boolean = isSystemDarkMode(context)): ThemePalette {
        return customPalette(resolveSeedColor(context), isDark)
    }

    fun colorSchemeFromPalette(palette: ThemePalette): ColorScheme {
        val composePrimary = ComposeColor(palette.primary)
        val composeBackground = ComposeColor(palette.background)
        val composeCard = ComposeColor(palette.card)
        val composePrimaryContainer = ComposeColor(palette.primaryContainer)
        val composeOnPrimary = ComposeColor(palette.onPrimary)
        val composeOnBackground = ComposeColor(palette.onBackground)
        val composeOnSurface = ComposeColor(palette.onCard)
        val composeOnPrimaryContainer = ComposeColor(palette.onPrimaryContainer)

        val useDarkScheme = composeBackground.luminance() < 0.4f

        return if (useDarkScheme) {
            darkColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeCard,
                primaryContainer = composePrimaryContainer,
                onPrimary = composeOnPrimary,
                onBackground = composeOnBackground,
                onSurface = composeOnSurface,
                onPrimaryContainer = composeOnPrimaryContainer
            ).copy(
                surfaceVariant = composeCard,
                surfaceContainerLowest = composeCard,
                surfaceContainerLow = composeCard,
                surfaceContainer = composeCard,
                surfaceContainerHigh = composeCard,
                surfaceContainerHighest = composeCard
            )
        } else {
            lightColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeCard,
                primaryContainer = composePrimaryContainer,
                onPrimary = composeOnPrimary,
                onBackground = composeOnBackground,
                onSurface = composeOnSurface,
                onPrimaryContainer = composeOnPrimaryContainer
            ).copy(
                surfaceVariant = composeCard,
                surfaceContainerLowest = composeCard,
                surfaceContainerLow = composeCard,
                surfaceContainer = composeCard,
                surfaceContainerHigh = composeCard,
                surfaceContainerHighest = composeCard
            )
        }
    }

    fun applyTheme(context: Context) {
        val palette = currentPalette(context)

        if (context is AppCompatActivity) {
            context.window.decorView.setBackgroundColor(palette.background)
            val insetsController = WindowCompat.getInsetsController(
                context.window,
                context.window.decorView
            )
            val useLightSystemBarIcons = ColorUtils.calculateLuminance(palette.background) > 0.5
            insetsController?.isAppearanceLightStatusBars = useLightSystemBarIcons
            insetsController?.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    fun getPrimaryColor(context: Context): Int {
        return currentPalette(context).primary
    }

    fun getAccentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACCENT_COLOR, "#FF4081".toColorInt())
    }

    fun getBackgroundColor(context: Context): Int {
        return currentPalette(context).background
    }

    fun getCardColor(context: Context): Int {
        return currentPalette(context).card
    }

    fun getIconColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme = if (isSystemDarkMode(context)) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            return dynamicScheme.primary.toArgb()
        }

        val background = getBackgroundColor(context)
        return if (ColorUtils.calculateLuminance(background) > 0.5) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    fun getLogoBackgroundColor(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme = if (isSystemDarkMode(context)) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            return dynamicScheme.primary.toArgb()
        }

        return DEFAULT_PRIMARY_COLOR.toColorInt()
    }

    fun isSystemDarkMode(context: Context): Boolean {
        val currentNightMode =
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    val palette = AppTheme.currentPalette(context, isSystemDark)
    val colorScheme = AppTheme.colorSchemeFromPalette(palette)

    val typography = Typography().copy(
        titleMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 25.sp
        )
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
