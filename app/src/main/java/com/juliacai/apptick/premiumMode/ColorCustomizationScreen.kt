package com.juliacai.apptick.premiumMode

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorCustomizationScreen(
    onColorSelected: (Color) -> Unit,
    onDarkModeChanged: (Boolean) -> Unit
) {
    var isDarkMode by remember { mutableStateOf(false) }
    var red by remember { mutableStateOf(0.5f) }
    var green by remember { mutableStateOf(0.5f) }
    var blue by remember { mutableStateOf(0.5f) }
    val selectedColor = Color(red, green, blue)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Color Customization") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode")
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = {
                        isDarkMode = it
                        onDarkModeChanged(it)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select a color for the app theme:")
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlider("Red", red) { newRed ->
                red = newRed
                onColorSelected(Color(newRed, green, blue))
            }
            ColorSlider("Green", green) { newGreen ->
                green = newGreen
                onColorSelected(Color(red, newGreen, blue))
            }
            ColorSlider("Blue", blue) { newBlue ->
                blue = newBlue
                onColorSelected(Color(red, green, newBlue))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(selectedColor, CircleShape)
            )
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}
