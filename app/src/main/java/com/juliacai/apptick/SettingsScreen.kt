package com.juliacai.apptick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    showTimeLeft: Boolean,
    onShowTimeLeftChange: (Boolean) -> Unit,
    onColorCustomizationClick: () -> Unit,
    onRemovePasswordClick: () -> Unit,
    hasPassword
: Boolean
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Time Left in Notification", modifier = Modifier.weight(1f))
                Switch(checked = showTimeLeft, onCheckedChange = onShowTimeLeftChange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onColorCustomizationClick, modifier = Modifier.fillMaxWidth()) {
                Text("Customize Colors")
            }
            if (hasPassword) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Password Protection")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRemovePasswordClick, modifier = Modifier.fillMaxWidth()) {
                            Text("Remove Password")
                        }
                    }
                }
            }
        }
    }
}
