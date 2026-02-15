package com.juliacai.apptick.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.ThemeModeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val isPremium = remember { prefs.getBoolean("premium", false) }

    val initialPrimaryColor = remember { Color(AppTheme.getPrimaryColor(context)) }
    val initialBackgroundColor = remember { Color(AppTheme.getBackgroundColor(context)) }
    val initialIconColor = remember { Color(AppTheme.getIconColor(context)) }
    val initialIconColorMode = remember {
        if (isPremium) prefs.getString("app_icon_color_mode", "system") ?: "system" else "system"
    }

    val hadPrimaryColor = remember { prefs.contains("custom_primary_color") }
    val hadBackgroundColor = remember { prefs.contains("custom_background_color") }
    val hadIconColor = remember { prefs.contains("custom_icon_color") }
    val hadIconColorMode = remember { prefs.contains("app_icon_color_mode") }
    val initialSavedPrimaryColor = remember { prefs.getInt("custom_primary_color", initialPrimaryColor.toArgb()) }
    val initialSavedBackgroundColor = remember { prefs.getInt("custom_background_color", initialBackgroundColor.toArgb()) }
    val initialSavedIconColor = remember { prefs.getInt("custom_icon_color", initialIconColor.toArgb()) }
    val initialSavedIconColorMode = remember { prefs.getString("app_icon_color_mode", initialIconColorMode) ?: initialIconColorMode }

    var primaryColor by remember { mutableStateOf(initialPrimaryColor) }
    var backgroundColor by remember { mutableStateOf(initialBackgroundColor) }
    var iconColor by remember { mutableStateOf(initialIconColor) }
    var iconColorMode by remember { mutableStateOf(initialIconColorMode) }
    
    var showColorPicker by remember { mutableStateOf(false) }
    var activeColorPickerTarget by remember { mutableStateOf("") }
    val hasChanges by remember(primaryColor, backgroundColor, iconColor, iconColorMode) {
        mutableStateOf(
            primaryColor != initialPrimaryColor ||
                backgroundColor != initialBackgroundColor ||
                iconColor != initialIconColor ||
                iconColorMode != initialIconColorMode
        )
    }

    fun saveChanges() {
        val resolvedIconColorMode = if (isPremium) iconColorMode else "system"
        prefs.edit {
            putInt("custom_primary_color", primaryColor.toArgb())
            putInt("custom_background_color", backgroundColor.toArgb())
            putString("app_icon_color_mode", resolvedIconColorMode)
            if (resolvedIconColorMode == "custom") {
                putInt("custom_icon_color", iconColor.toArgb())
            } else {
                remove("custom_icon_color")
            }
        }
        ThemeModeManager.persistCustomColorMode(context, true)
        ThemeModeManager.apply(context)
        context.sendBroadcast(Intent("COLORS_CHANGED"))
        onBackClick()
    }

    fun cancelChanges() {
        prefs.edit {
            if (hadPrimaryColor) putInt("custom_primary_color", initialSavedPrimaryColor)
            else remove("custom_primary_color")

            if (hadBackgroundColor) putInt("custom_background_color", initialSavedBackgroundColor)
            else remove("custom_background_color")

            if (hadIconColor) putInt("custom_icon_color", initialSavedIconColor)
            else remove("custom_icon_color")

            if (hadIconColorMode) putString("app_icon_color_mode", initialSavedIconColorMode)
            else remove("app_icon_color_mode")
        }
        ThemeModeManager.apply(context)
        context.sendBroadcast(Intent("COLORS_CHANGED"))
        onBackClick()
    }

    val presetColors = listOf(
        Color(0xFF3949AB), // Default Indigo
        Color(0xFFD32F2F), // Red
        Color(0xFF1976D2), // Blue
        Color(0xFF388E3C), // Green
        Color(0xFFFBC02D), // Yellow
        Color(0xFF8E24AA), // Purple
        Color(0xFFE64A19), // Orange
        Color(0xFF455A64), // Blue Grey
        Color(0xFF000000)  // Black
    )

    val backgroundColors = listOf(
        Color.White,
        Color(0xFFF5F5F5),
        Color(0xFFFFF3E0), // Light Orange
        Color(0xFFE8F5E9), // Light Green
        Color(0xFFE3F2FD), // Light Blue
        Color(0xFFF3E5F5), // Light Purple
        Color(0xFF212121)  // Dark Grey
    )
    val effectivePreviewIconColor =
        if (iconColorMode == "custom") iconColor
        else if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Theme") },
                navigationIcon = {
                    IconButton(onClick = { cancelChanges() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Theme Preview", style = MaterialTheme.typography.titleLarge, color = effectivePreviewIconColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Button Color", color = if (primaryColor == Color.Black) Color.White else Color.White)
                        }
                    }
                }
            }

            Text("Primary Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ColorSelectionRow(
                colors = presetColors,
                selectedColor = primaryColor,
                onColorSelected = {
                    primaryColor = it
                },
                onCustomColorClick = {
                    showColorPicker = true
                    activeColorPickerTarget = "primary"
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Background Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ColorSelectionRow(
                colors = backgroundColors,
                selectedColor = backgroundColor,
                onColorSelected = {
                    backgroundColor = it
                },
                onCustomColorClick = {
                    showColorPicker = true
                    activeColorPickerTarget = "background"
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            Text("App Icon Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (isPremium) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { iconColorMode = "system" },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            1.dp,
                            if (iconColorMode == "system") MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    ) {
                        Text("Use Android Theme")
                    }
                    OutlinedButton(
                        onClick = { iconColorMode = "custom" },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(
                            1.dp,
                            if (iconColorMode == "custom") MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    ) {
                        Text("Custom (Premium)")
                    }
                }

                if (iconColorMode == "custom") {
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorSelectionRow(
                        colors = presetColors,
                        selectedColor = iconColor,
                        onColorSelected = {
                            iconColor = it
                        },
                        onCustomColorClick = {
                            showColorPicker = true
                            activeColorPickerTarget = "icon"
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Icon color follows your Android system theme.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    "Icon color follows your Android system theme. Premium unlocks custom app icon color.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { cancelChanges() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { saveChanges() },
                    enabled = hasChanges,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showColorPicker) {
        val initialColor = when (activeColorPickerTarget) {
            "primary" -> primaryColor
            "background" -> backgroundColor
            "icon" -> iconColor
            else -> Color.White
        }

        RGBColorPickerDialog(
            initialColor = initialColor,
            onColorSelected = { color ->
                when (activeColorPickerTarget) {
                    "primary" -> {
                        primaryColor = color
                    }
                    "background" -> {
                        backgroundColor = color
                    }
                    "icon" -> {
                        iconColor = color
                    }
                }
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}



@Composable
fun ColorSelectionRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onCustomColorClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
             Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onCustomColorClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Custom Color",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selectedColor) 3.dp else 1.dp,
                        color = if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White
                    )
                }
            }
        }
    }
}
