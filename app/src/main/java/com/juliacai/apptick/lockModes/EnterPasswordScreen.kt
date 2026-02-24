package com.juliacai.apptick.lockModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterPasswordScreen(
    onPasswordSubmit: (String) -> Unit,
    onCancelClick: () -> Unit,
    onBiometricClick: () -> Unit,
    onUsbKeyClick: () -> Unit,
    isBiometricVisible: Boolean,
    isUsbKeyVisible: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Enter Password",
                        maxLines = 1,
                        softWrap = false
                    )
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enter Password", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onPasswordSubmit(password)
                    }
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onPasswordSubmit(password)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isBiometricVisible) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBiometricClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Biometric")
                }
            }
            if (isUsbKeyVisible) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onUsbKeyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use USB Key")
                }
            }
            Button(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EnterPasswordScreenPreview() {
    AppTheme {
        EnterPasswordScreen(
            onPasswordSubmit = {},
            onCancelClick = {},
            onBiometricClick = {},
            onUsbKeyClick = {},
            isBiometricVisible = true,
            isUsbKeyVisible = true
        )
    }
}
