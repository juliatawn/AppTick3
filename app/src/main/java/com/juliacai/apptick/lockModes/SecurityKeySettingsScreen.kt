package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.premiumMode.DeviceAdmin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityKeySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val activity = context as? Activity
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminComponentName = remember(context) {
        ComponentName(context, DeviceAdmin::class.java)
    }

    var securityKey by remember { mutableStateOf("") }
    var confirmSecurityKey by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf(prefs.getString("recovery_email_security_key", "") ?: "") }
    var lockSettings by remember { mutableStateOf(prefs.getBoolean("blockSettings", false)) }
    var isAdminGranted by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponentName)) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isAdminGranted = devicePolicyManager.isAdminActive(adminComponentName)
        val message = if (isAdminGranted) "Device admin enabled" else "Device admin not enabled"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Key Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Set a security key to unlock AppTick limit changes.")

            OutlinedTextField(
                value = securityKey,
                onValueChange = { securityKey = it },
                label = { Text("Security Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmSecurityKey,
                onValueChange = { confirmSecurityKey = it },
                label = { Text("Confirm Security Key") },
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
                Checkbox(checked = lockSettings, onCheckedChange = { lockSettings = it })
                Text("Protect uninstall from Settings")
            }
            Text("Device Admin: ${if (isAdminGranted) "Enabled" else "Not enabled"}")
            Text("When enabled, AppTick blocks the Settings uninstall page while lock mode is active.")

            Button(
                onClick = {
                    if (isAdminGranted) {
                        Toast.makeText(context, "Device admin already enabled", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (activity == null) {
                        Toast.makeText(context, "Unable to open Device Admin settings", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponentName)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Granting this permission lets AppTick block Settings uninstall pages while lock mode is active."
                        )
                    }
                    deviceAdminLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Device Admin")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (securityKey.isBlank()) {
                        Toast.makeText(context, "Enter a security key", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (securityKey != confirmSecurityKey) {
                        Toast.makeText(context, "Security keys do not match", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (lockSettings && !isAdminGranted) {
                        Toast.makeText(
                            context,
                            "Enable Device Admin before turning on uninstall protection",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    prefs.edit {
                        putBoolean("security_key_enabled", true)
                        putString("active_lock_mode", "SECURITY_KEY")
                        putString("security_key_value", securityKey)
                        putString("recovery_email_security_key", recoveryEmail)
                        putBoolean("blockSettings", lockSettings)
                        putBoolean("locked", true)
                        putBoolean("securityKeyUnlocked", false)
                    }
                    if (lockSettings) {
                        BackgroundChecker.startServiceIfNotRunning(context.applicationContext)
                    }
                    Toast.makeText(context, "Security key enabled", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Security Key")
            }

            Button(
                onClick = {
                    prefs.edit {
                        remove("security_key_value")
                        remove("recovery_email_security_key")
                        putBoolean("security_key_enabled", false)
                        putString("active_lock_mode", "NONE")
                        putBoolean("securityKeyUnlocked", false)
                    }
                    Toast.makeText(context, "Security key disabled", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disable Security Key")
            }
        }
    }
}
