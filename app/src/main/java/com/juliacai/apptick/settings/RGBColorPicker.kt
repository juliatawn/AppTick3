package com.juliacai.apptick.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun RGBColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }

    val currentColor = Color(red, green, blue)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Color Preview
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(1.dp, Color.Gray, CircleShape)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Red Slider
                ColorSlider(
                    label = "Red",
                    value = red,
                    onValueChange = { red = it },
                    color = Color.Red
                )

                // Green Slider
                ColorSlider(
                    label = "Green",
                    value = green,
                    onValueChange = { green = it },
                    color = Color.Green
                )

                // Blue Slider
                ColorSlider(
                    label = "Blue",
                    value = blue,
                    onValueChange = { blue = it },
                    color = Color.Blue
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(currentColor) }) {
                        Text("Select")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = "${(value * 255).toInt()}", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.24f)
            )
        )
    }
}
