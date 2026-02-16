package com.juliacai.apptick.lockModes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
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
fun EnterPasswordScreen(
    onPasswordSubmit: (String) -> Unit,
    onBiometricClick: () -> Unit,
    onUsbKeyClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    isBiometricVisible: Boolean,
    isUsbKeyVisible: Boolean,
) {
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter Password") },
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
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onPasswordSubmit(password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
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
            Spacer(modifier = Modifier.height(32.dp))
            TextButton(onClick = onForgotPasswordClick) {
                Text("Forgot Password?")
            }
        }
    }
}
