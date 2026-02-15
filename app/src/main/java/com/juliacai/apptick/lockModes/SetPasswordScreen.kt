package com.juliacai.apptick.lockModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    onSaveClick: (String, String, String, Boolean) -> Unit,
    onCancelClick: () -> Unit,
    onEnableDeviceAdminClick: () -> Unit,
    onSetupRecoveryEmailClick: () -> Unit,
    isAdminGranted: Boolean
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf("") }
    var enableSettingsLock by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Set Password") }) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label = { Text("Recovery Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enableSettingsLock,
                    onCheckedChange = { enableSettingsLock = it }
                )
                Text(text = "Protect uninstall from Settings")
            }
            Text(
                text = "When enabled, AppTick blocks the Settings uninstall page while lock mode is active. Device Admin permission is required.",
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = if (isAdminGranted) "Device Admin: Enabled" else "Device Admin: Not enabled",
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSaveClick(password, confirmPassword, recoveryEmail, enableSettingsLock) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Password")
                }
            }
            Button(onClick = onEnableDeviceAdminClick, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Device Admin")
            }
            Button(onClick = onSetupRecoveryEmailClick, modifier = Modifier.fillMaxWidth()) {
                Text("Setup Recovery Email")
            }
        }
    }
}
