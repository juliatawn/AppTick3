package com.juliacai.apptick.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.ThemeModeManager
import com.juliacai.apptick.verticalScrollWithIndicator

private val unifiedSwatches = listOf(
    Color(0xFF5DB8E0), Color(0xFF1860A4), Color(0xFF489BE5),
    Color(0xFF957EBD), Color(0xFF7E48B2), Color(0xFF9365AF),
    Color(0xFFDC8AA3), Color(0xFFC41D66), Color(0xFFB73D69),
    Color(0xFFD09A9A), Color(0xFFB40000), Color(0xFFBB2222),
    Color(0xFF79A97D), Color(0xFF469647), Color(0xFF4B7950),
    Color(0xFFF8A972), Color(0xFFFF7700), Color(0xFFEE6F00),
    Color(0xFFEEC869), Color(0xFFFFD84A), Color(0xFFD2B96C),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }

    val isAppDark = ThemeModeManager.isDarkModeEnabled(context)
    val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(context)
    val savedPrimary = remember { prefs.getInt("custom_primary_color", Color(0xFF6F34AD).toArgb()) }

    var selectedColor by remember { mutableStateOf(Color(savedPrimary)) }

    val previewPalette = remember(selectedColor, isAppDark) {
        AppTheme.customPalette(selectedColor.toArgb(), isAppDark)
    }

    val composePrimary = Color(previewPalette.primary)
    val composeBackground = Color(previewPalette.background)
    val composeCard = Color(previewPalette.card)
    val composePrimaryContainer = Color(previewPalette.primaryContainer)
    val composeOnPrimary = Color(previewPalette.onPrimary)
    val composeOnBackground = Color(previewPalette.onBackground)
    val composeOnSurface = Color(previewPalette.onCard)
    val composeOnPrimaryContainer = Color(previewPalette.onPrimaryContainer)

    val colorScheme = if (customColorModeEnabled) {
        val useDarkScheme = composeBackground.luminance() < 0.4f
        if (useDarkScheme) {
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
    } else if (isAppDark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Color Customization",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
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
                PreviewCard(primary = composePrimary, card = composeCard)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollWithIndicator()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Choose Theme Color",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            unifiedSwatches.distinctBy { it.toArgb() }.forEach { color ->
                                val isSelected = color.toArgb() == selectedColor.toArgb()
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
                                        .clickable { selectedColor = color }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(onClick = onBackClick, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Spacer(modifier = Modifier.size(16.dp))
                        Button(
                            onClick = {
                                val palette = AppTheme.customPalette(selectedColor.toArgb(), isAppDark)
                                prefs.edit()
                                    .putInt("custom_primary_color", selectedColor.toArgb())
                                    .putInt("custom_background_color", palette.background)
                                    .putInt("custom_card_color", palette.card)
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
private fun PreviewCard(primary: Color, card: Color) {
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
                    .background(card)
                    .border(1.dp, Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Card Preview", color = primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ColorPickerScreenPreview() {
    ColorPickerScreen(onBackClick = {})
}
