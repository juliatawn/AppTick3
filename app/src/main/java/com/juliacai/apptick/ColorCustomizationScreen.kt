package com.juliacai.apptick

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorCustomizationScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    primaryColor: Color,
    onPrimaryColorChange: (Float) -> Unit,
    primaryDarkColor: Color,
    onPrimaryDarkColorChange: (Float) -> Unit,
    accentColor: Color,
    onAccentColorChange: (Float) -> Unit,
    onResetClick: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Customize Colors") }) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
            }
            ColorPicker("Primary Color", primaryColor, onPrimaryColorChange)
            ColorPicker("Primary Dark Color", primaryDarkColor, onPrimaryDarkColorChange)
            ColorPicker("Accent Color", accentColor, onAccentColorChange)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onResetClick, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to Default Colors")
            }
        }
    }
}

@Composable
private fun ColorPicker(label: String, color: Color, onValueChange: (Float) -> Unit) {
    Column {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = colorToHue(color),
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color, CircleShape)
                    .clip(CircleShape)
            )
        }
    }
}

private fun colorToHue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(color.red.toInt(), color.green.toInt(), color.blue.toInt(), hsv)
    return hsv[0] / 360f
}
