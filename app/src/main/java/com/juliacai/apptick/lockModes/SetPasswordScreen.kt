package com.juliacai.apptick.lockModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPasswordScreen(
    onSaveClick: (String, String, Boolean, Boolean) -> Unit,
    onCancelClick: () -> Unit,
    onBiometricToggleRequest: (Boolean) -> Unit,
    onUsbSecurityKeyToggleRequest: (Boolean) -> Unit,
    onDisabledInteraction: () -> Unit,
    isConfigurationEnabled: Boolean,
    onConfigurationEnabledChange: (Boolean) -> Unit,
    isPasswordEnabled: Boolean,
    activeLockMode: LockMode,
    biometricEnabled: Boolean,
    isBiometricSupported: Boolean,
    usbSecurityKeyEnabled: Boolean
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val disabledInteractionSource = remember { MutableInteractionSource() }
    val disabledTapModifier = if (!isConfigurationEnabled) {
        Modifier.clickable(
            interactionSource = disabledInteractionSource,
            indication = null
        ) {
            onDisabledInteraction()
        }
    } else {
        Modifier
    }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Set Password",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancelClick) {
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
                .verticalScrollWithIndicator()
                .clickable(
                    enabled = !isConfigurationEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDisabledInteraction()
                },
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
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clickable(
                            enabled = !isConfigurationEnabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Password Mode", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isConfigurationEnabled,
                        onCheckedChange = onConfigurationEnabledChange
                    )
                }
            }
            if (activeLockMode != LockMode.NONE && activeLockMode != LockMode.PASSWORD) {
                Text(
                    text = "Another mode is active ($activeLockMode). Disable it before enabling Password mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().then(disabledTapModifier)) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("New Password") },
                            enabled = isConfigurationEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().then(disabledTapModifier)) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm New Password") },
                            enabled = isConfigurationEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().then(disabledTapModifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricToggleRequest,
                            enabled = isConfigurationEnabled && isBiometricSupported
                        )
                        Text(
                            text = if (isBiometricSupported) {
                                "Allow biometric unlock for Password mode (fingerprint/face)"
                            } else {
                                "Biometric unlock unavailable on this device"
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().then(disabledTapModifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = usbSecurityKeyEnabled,
                            onCheckedChange = onUsbSecurityKeyToggleRequest,
                            enabled = isConfigurationEnabled
                        )
                        Text("Allow USB security key as an alternative unlock for Password mode")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onSaveClick(
                                password,
                                confirmPassword,
                                biometricEnabled && isBiometricSupported,
                                usbSecurityKeyEnabled
                            )
                        },
                        enabled = isConfigurationEnabled &&
                            (activeLockMode == LockMode.NONE || activeLockMode == LockMode.PASSWORD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (activeLockMode == LockMode.NONE || activeLockMode == LockMode.PASSWORD) {
                            Text("Save")
                        } else {
                            Text("Disable $activeLockMode first")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun SetPasswordScreenPreview() {
    AppTheme {
        SetPasswordScreen(
            onSaveClick = { _, _, _, _ -> },
            onCancelClick = {},
            onBiometricToggleRequest = {},
            onUsbSecurityKeyToggleRequest = {},
            onDisabledInteraction = {},
            isConfigurationEnabled = true,
            onConfigurationEnabledChange = {},
            isPasswordEnabled = false,
            activeLockMode = LockMode.NONE,
            biometricEnabled = false,
            isBiometricSupported = true,
            usbSecurityKeyEnabled = false
        )
    }
}
