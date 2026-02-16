package com.juliacai.apptick.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.juliacai.apptick.ThemeModeManager

private val unifiedSwatches = listOf(
    // Red
    Color(0xFFFFEBEE), Color(0xFFD32F2F),
    // Green
    Color(0xFFE8F5E9), Color(0xFF388E3C),
    // Yellow
    Color(0xFFFFFDE7), Color(0xFFFBC02D),
    // Orange
    Color(0xFFFFF3E0), Color(0xFFF57C00),
    // Blue
    Color(0xFFE3F2FD), Color(0xFF1976D2),
    // Purple
    Color(0xFFF3E5F5), Color(0xFF7B1FA2),
    // Pink
    Color(0xFFFCE4EC), Color(0xFFC2185B),
    // Black / Gray
    Color(0xFFFAFAFA), Color(0xFF212121),
    Color(0xFFCFD8DC), Color(0xFF607D8B),
    // Extras for solid black/white
    Color.White, Color.Black
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }

    val isAppDark = ThemeModeManager.isDarkModeEnabled(context)
    val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(context)
    val isPremium = prefs.getBoolean("premium", false)

    val savedPrimary = remember { prefs.getInt("custom_primary_color", Color(0xFF3949AB).toArgb()) }
    val savedBackground = remember { prefs.getInt("custom_background_color", Color.White.toArgb()) }
    val savedIcon = remember { prefs.getInt("custom_icon_color", Color.Black.toArgb()) }
    val savedIconMode = remember { prefs.getString("app_icon_color_mode", "system") ?: "system" }

    val primaryColorState = rememberColorState(Color(savedPrimary))
    val backgroundColorState = rememberColorState(Color(savedBackground))
    val iconColorState = rememberColorState(Color(savedIcon))

    var iconColorMode by remember { mutableStateOf(savedIconMode) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Text", "Background", "Icon")
    val activeState = when (selectedTab) {
        0 -> primaryColorState
        1 -> backgroundColorState
        else -> iconColorState
    }

    val composePrimary = primaryColorState.color
    val composeBackground = backgroundColorState.color
    val systemThemeIconColor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isAppDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        } else {
            null
        }
    val fallbackIconColor = if (ColorUtils.calculateLuminance(composeBackground.toArgb()) > 0.5) Color.Black else Color.White
    val effectiveSystemIconColor = systemThemeIconColor ?: fallbackIconColor
    
    // Logic update: For PREVIEW purposes, we show the custom color if the mode is set to custom,
    // regardless of premium status. This ensures the user sees what they are picking.
    // However, saving might still respect premium if enforced elsewhere.
    val previewIconColor = if (iconColorMode == "custom") iconColorState.color else effectiveSystemIconColor
    
    // For the UI THEME of this screen (WYSIWYG), we use the preview colors.
    val composeIconColor = previewIconColor

    val colorScheme = if (customColorModeEnabled) {
        val useDarkScheme = composeBackground.luminance() < 0.4f
        if (useDarkScheme) {
            darkColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeBackground,
                primaryContainer = composePrimary.copy(alpha = 0.24f),
                onPrimary = composeIconColor,
                onBackground = composeIconColor,
                onSurface = composeIconColor,
                onPrimaryContainer = composeIconColor
            )
        } else {
            lightColorScheme(
                primary = composePrimary,
                background = composeBackground,
                surface = composeBackground,
                primaryContainer = composePrimary.copy(alpha = 0.16f),
                onPrimary = composeIconColor,
                onBackground = composeIconColor,
                onSurface = composeIconColor,
                onPrimaryContainer = composeIconColor
            )
        }
    } else if (isAppDark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Color Customization") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                // ── Fixed Preview Card ──────────────────────────────────────
                PreviewCard(
                    primary = primaryColorState.color,
                    background = backgroundColorState.color,
                    icon = previewIconColor
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Display hex values for reference (user requested editable field below, but keeping header info is fine,
                        // actually user said "hex number is a changeable field ... not just showing the number".
                        // So I should arguably remove these static displays if they are redundant.
                        // However, these show the CURRENT state of all 3. The field below edits the ACTIVE one.
                        // I'll keep them as summary but maybe smaller.
                        Text("Primary: ${primaryColorState.hex}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("primary_hex"))
                        Text("Background: ${backgroundColorState.hex}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("background_hex"))
                        Text("Icon: ${iconColorState.hex}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("icon_hex"))

                        if (selectedTab == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Match System Theme", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = iconColorMode == "system",
                                    onCheckedChange = { iconColorMode = if (it) "system" else "custom" },
                                    modifier = Modifier.testTag("icon_match_system_toggle")
                                )
                            }
                        }

                        if (selectedTab != 2 || iconColorMode == "custom") {
                            // Unified swatches for all tabs
                            val swatches = unifiedSwatches

                            Text("Quick Picks", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                swatches.distinctBy { it.toArgb() }.forEach { color ->
                                    val isSelected = color.toArgb() == activeState.color.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clickable { activeState.setColor(color) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Spectrum Wheel", style = MaterialTheme.typography.titleSmall)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("color_wheel_container"),
                                contentAlignment = Alignment.Center
                            ) {
                                ColorWheel(
                                    hue = activeState.h,
                                    saturation = activeState.s,
                                    gestureKey = "$selectedTab-$iconColorMode",
                                    onChange = { h, s ->
                                        activeState.h = h
                                        activeState.s = s
                                        if (activeState.v <= 0.01f) {
                                            activeState.v = 1f
                                        }
                                    }
                                )
                            }

                            Text("Brightness: ${(activeState.v * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = activeState.v,
                                onValueChange = { activeState.v = it },
                                valueRange = 0f..1f
                            )

                            // Editable Hex Field
                            var hexInput by remember(activeState.color) { mutableStateOf(activeState.hex) }
                            var isError by remember { mutableStateOf(false) }

                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { newValue ->
                                    hexInput = newValue
                                    try {
                                        val clean = newValue.removePrefix("#")
                                        if (clean.length == 6) {
                                            val colorInt = android.graphics.Color.parseColor("#$clean")
                                            activeState.setColor(Color(colorInt))
                                            isError = false
                                        } else {
                                            isError = newValue.isNotEmpty() && clean.length > 6
                                        }
                                    } catch (e: Exception) {
                                        isError = true
                                    }
                                },
                                label = { Text("Hex Code") },
                                isError = isError,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("hex_input")
                            )
                        } else {
                            Text(
                                "Icon color will match your system theme color.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }

                    Row(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(onClick = onBackClick, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(
                            onClick = {
                                prefs.edit()
                                    .putInt("custom_primary_color", primaryColorState.color.toArgb())
                                    .putInt("custom_background_color", backgroundColorState.color.toArgb())
                                    .putInt("custom_icon_color", iconColorState.color.toArgb())
                                    .putString("app_icon_color_mode", iconColorMode)
                                    .putBoolean("custom_color_mode", true)
                                    .apply()
                                context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                                onBackClick()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(primary: Color, background: Color, icon: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .border(1.dp, Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Card Preview", color = primary, style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(icon))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Icon Color", color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Preview Icon: ${String.format("#%06X", 0xFFFFFF and icon.toArgb())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("effective_icon_hex")
            )
        }
    }
}

class ColorState(initialColor: Color) {
    var h by mutableFloatStateOf(0f)
    var s by mutableFloatStateOf(0f)
    var v by mutableFloatStateOf(0f)

    init {
        setColor(initialColor)
    }

    val color: Color
        get() = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

    val hex: String
        get() = String.format("#%06X", 0xFFFFFF and color.toArgb())

    fun setColor(newColor: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(newColor.toArgb(), hsv)
        h = hsv[0]
        s = hsv[1]
        v = hsv[2]
    }
}

@Composable
fun rememberColorState(initialColor: Color) = remember(initialColor) { ColorState(initialColor) }

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    gestureKey: String,
    onChange: (Float, Float) -> Unit
) {
    val sweep = listOf(
        Color.Red,
        Color.Yellow,
        Color.Green,
        Color.Cyan,
        Color.Blue,
        Color.Magenta,
        Color.Red
    )

    Canvas(
        modifier = Modifier
            .size(220.dp)
            .testTag("color_wheel")
            .pointerInput(gestureKey) {
                detectTapGestures { offset ->
                    updateWheelSelection(offset, size.width.toFloat(), onChange)
                }
            }
            .pointerInput(gestureKey) {
                detectDragGestures { change, _ ->
                    updateWheelSelection(change.position, size.width.toFloat(), onChange)
                }
            }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(radius, radius)

        drawCircle(brush = Brush.sweepGradient(sweep, center = center), radius = radius, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        val angle = Math.toRadians(hue.toDouble())
        val markerRadius = saturation.coerceIn(0f, 1f) * radius
        val markerX = center.x + (kotlin.math.cos(angle) * markerRadius).toFloat()
        val markerY = center.y + (kotlin.math.sin(angle) * markerRadius).toFloat()

        drawCircle(Color.White, radius = 10f, center = Offset(markerX, markerY))
        drawCircle(Color.Black, radius = 6f, center = Offset(markerX, markerY))
    }
}

private fun updateWheelSelection(offset: Offset, sizePx: Float, onChange: (Float, Float) -> Unit) {
    val radius = sizePx / 2f
    val dx = offset.x - radius
    val dy = offset.y - radius
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    val sat = (distance / radius).coerceIn(0f, 1f)

    var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0f) angle += 360f

    onChange(angle, sat)
}
