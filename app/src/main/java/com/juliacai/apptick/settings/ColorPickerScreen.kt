package com.juliacai.apptick.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp

private data class ThemePreset(
    val name: String,
    val primary: Color,
    val background: Color
)

private val recommendedPresets = listOf(
    ThemePreset("Light Slate", Color(0xFF1F2937), Color(0xFFF8FAFC)),
    ThemePreset("Light Indigo", Color(0xFF312E81), Color(0xFFEFF2FF)),
    ThemePreset("Light Purple", Color(0xFF4C1D95), Color(0xFFF3E8FF)),
    ThemePreset("Dark Graphite", Color(0xFFE5E7EB), Color(0xFF111827)),
    ThemePreset("Dark Indigo", Color(0xFFDDE4FF), Color(0xFF1E1B4B)),
    ThemePreset("Dark Purple", Color(0xFFE9D5FF), Color(0xFF2E1065))
)

private val primaryForLightBackground = listOf(
    Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF374151)
)

private val primaryForDarkBackground = listOf(
    Color(0xFFF8FAFC), Color(0xFFE2E8F0), Color(0xFFDDE4FF), Color(0xFFE9D5FF), Color(0xFFFDE68A)
)

private val lightBackgrounds = listOf(
    Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFEFF6FF), Color(0xFFF5F3FF), Color(0xFFECFDF5)
)

private val darkBackgrounds = listOf(
    Color(0xFF0B1220), Color(0xFF111827), Color(0xFF1E1B4B), Color(0xFF2E1065), Color(0xFF052E2B)
)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun ColorPickerScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }

    val savedPrimary = remember { prefs.getInt("custom_primary_color", Color(0xFF3949AB).toArgb()) }
    val savedBackground = remember { prefs.getInt("custom_background_color", Color.White.toArgb()) }
    val savedIcon = remember { prefs.getInt("custom_icon_color", Color.Black.toArgb()) }
    val savedIconMode = remember { prefs.getString("app_icon_color_mode", "system") ?: "system" }

    val primaryColorState = rememberColorState(Color(savedPrimary))
    val backgroundColorState = rememberColorState(Color(savedBackground))
    val iconColorState = rememberColorState(Color(savedIcon))

    var iconColorMode by remember { mutableStateOf(savedIconMode) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf("Primary (Text)", "Background (Cards)", "App Icon")
    val activeState = when (selectedTab) {
        0 -> primaryColorState
        1 -> backgroundColorState
        else -> iconColorState
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Customization") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            PreviewCard(
                primary = primaryColorState.color,
                background = backgroundColorState.color,
                icon = if (iconColorMode == "custom") iconColorState.color else primaryColorState.color
            )

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selectedTab != 2) {
                    Text("Recommended Light/Dark Theme Pairs", style = MaterialTheme.typography.titleMedium)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(160.dp)
                    ) {
                        items(recommendedPresets) { preset ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        primaryColorState.setColor(preset.primary)
                                        backgroundColorState.setColor(preset.background)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(preset.name, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(preset.primary)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(preset.background)
                                                .border(1.dp, Color.Gray.copy(alpha = 0.35f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedTab == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Match System Theme", modifier = Modifier.weight(1f))
                        Switch(
                            checked = iconColorMode == "system",
                            onCheckedChange = { iconColorMode = if (it) "system" else "custom" }
                        )
                    }
                }

                if (selectedTab != 2 || iconColorMode == "custom") {
                    val swatches = when (selectedTab) {
                        0 -> if (backgroundColorState.color.luminance() > 0.5f) primaryForLightBackground else primaryForDarkBackground
                        1 -> if (primaryColorState.color.luminance() > 0.5f) darkBackgrounds else lightBackgrounds
                        else -> primaryForDarkBackground + primaryForLightBackground
                    }

                    Text("Quick Picks", style = MaterialTheme.typography.titleMedium)
                    ColorSwatchGrid(colors = swatches, selected = activeState.color, onPick = { activeState.setColor(it) })

                    Text("Spectrum Wheel", style = MaterialTheme.typography.titleMedium)
                    ColorWheel(
                        hue = activeState.h,
                        saturation = activeState.s,
                        onChange = { h, s ->
                            activeState.h = h
                            activeState.s = s
                        }
                    )

                    Text(
                        "Brightness: ${(activeState.v * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = activeState.v,
                        onValueChange = { activeState.v = it },
                        valueRange = 0f..1f
                    )

                    Text("Hex: ${activeState.hex}", style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun PreviewCard(primary: Color, background: Color, icon: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color(0xFFEEEEEE))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
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
                Spacer(modifier = Modifier.size(8.dp))
                Text("Icon Color", color = Color.Black)
            }
        }
    }
}

@Composable
private fun ColorSwatchGrid(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(100.dp)
    ) {
        items(colors.distinctBy { it.toArgb() }) { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color.toArgb() == selected.toArgb()) 2.dp else 1.dp,
                        color = if (color.toArgb() == selected.toArgb()) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onPick(color) }
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
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateWheelSelection(offset, size.width.toFloat(), onChange)
                }
            }
            .pointerInput(Unit) {
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
