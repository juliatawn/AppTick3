package com.juliacai.apptick.lockModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    onSaveClick: (String, String, String, Boolean, Boolean) -> Unit,
    onVerifyRecoveryEmailClick: (String) -> Unit,
    onCancelClick: () -> Unit,
    onBackClick: () -> Unit,
    onEnableDeviceAdminClick: () -> Unit,
    onDisableClick: () -> Unit,
    isAdminGranted: Boolean,
    isPasswordEnabled: Boolean,
    activeLockMode: LockMode,
    isRecoveryEmailVerified: Boolean,
    initialRecoveryEmail: String,
    initialAdminProtection: Boolean,
    initialBiometricEnabled: Boolean,
    isBiometricSupported: Boolean
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf(initialRecoveryEmail) }
    var enableAdminProtection by remember { mutableStateOf(initialAdminProtection) }
    var enableBiometric by remember { mutableStateOf(initialBiometricEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Password") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                .padding(24.dp)
                .verticalScrollWithIndicator(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Password setup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Enter the new password twice. It must match before Password mode can be enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = if (isPasswordEnabled) "Password mode: On" else "Password mode: Off",
                style = MaterialTheme.typography.bodyMedium
            )
            if (activeLockMode != LockMode.NONE && activeLockMode != LockMode.PASSWORD) {
                Text(
                    text = "Another mode is active ($activeLockMode). Disable it before enabling Password mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("New Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm New Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label = { Text("Recovery Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Current recovery email: ${if (initialRecoveryEmail.isBlank()) "Not set" else initialRecoveryEmail}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (isRecoveryEmailVerified) {
                    "Recovery email verified"
                } else {
                    "Recovery email not verified yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isRecoveryEmailVerified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onVerifyRecoveryEmailClick(recoveryEmail.trim())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify Recovery Email")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enableAdminProtection,
                    onCheckedChange = { enableAdminProtection = it }
                )
                Text(text = "Use Device Admin to harden uninstall protection (optional)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = enableBiometric,
                    onCheckedChange = { enableBiometric = it },
                    enabled = isBiometricSupported
                )
                Text(
                    text = if (isBiometricSupported) {
                        "Allow biometric unlock for Password mode (fingerprint/face)"
                    } else {
                        "Biometric unlock unavailable on this device"
                    }
                )
            }
            Text(
                text = "Device Admin is optional and only used for uninstall hardening.",
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
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onSaveClick(
                            password,
                            confirmPassword,
                            recoveryEmail.trim(),
                            enableAdminProtection,
                            enableBiometric && isBiometricSupported
                        )
                    },
                    enabled = activeLockMode == LockMode.NONE || activeLockMode == LockMode.PASSWORD,
                    modifier = Modifier.weight(1f)
                ) {
                    if (activeLockMode == LockMode.NONE || activeLockMode == LockMode.PASSWORD) {
                        Text("Enable Password Mode")
                    } else {
                        Text("Disable $activeLockMode first")
                    }
                }
            }
            if (isPasswordEnabled) {
                Button(
                    onClick = onDisableClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disable Password Mode")
                }
            }
            Button(onClick = onEnableDeviceAdminClick, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Device Admin")
            }
        }
    }
}
