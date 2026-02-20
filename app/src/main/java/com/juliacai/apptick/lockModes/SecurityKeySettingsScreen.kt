package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Patterns
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.premiumMode.DeviceAdmin
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityKeySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val activity = context as? Activity
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminComponentName = remember(context) {
        ComponentName(context, DeviceAdmin::class.java)
    }

    var securityKey by remember { mutableStateOf("") }
    var recoveryEmail by remember { mutableStateOf(prefs.getString("recovery_email_security_key", "") ?: "") }
    var enableAdminProtection by remember { mutableStateOf(prefs.getBoolean("useDeviceAdminUninstallProtection", false)) }
    var recoveryEmailVerified by remember {
        mutableStateOf(
            prefs.getBoolean("recovery_email_security_key_verified", false) &&
                !prefs.getString("recovery_email_security_key", null).isNullOrBlank()
        )
    }
    var activeLockMode by remember {
        mutableStateOf(
            runCatching {
                LockMode.valueOf(prefs.getString("active_lock_mode", "NONE") ?: "NONE")
            }.getOrDefault(LockMode.NONE)
        )
    }
    var isAdminGranted by remember { mutableStateOf(devicePolicyManager.isAdminActive(adminComponentName)) }

    val deviceAdminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isAdminGranted = devicePolicyManager.isAdminActive(adminComponentName)
        val message = if (isAdminGranted) "Device admin enabled" else "Device admin not enabled"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, prefs, devicePolicyManager, adminComponentName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAdminGranted = devicePolicyManager.isAdminActive(adminComponentName)
                activeLockMode = runCatching {
                    LockMode.valueOf(prefs.getString("active_lock_mode", "NONE") ?: "NONE")
                }.getOrDefault(LockMode.NONE)
                recoveryEmailVerified = prefs.getBoolean("recovery_email_security_key_verified", false) &&
                    prefs.getString("recovery_email_security_key", null)
                        .equals(recoveryEmail, ignoreCase = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                .padding(16.dp)
                .verticalScrollWithIndicator(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Security key setup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Set your security key, then verify recovery email before enabling Security Key mode.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = if (activeLockMode == LockMode.SECURITY_KEY) "Security key mode: On" else "Security key mode: Off",
                style = MaterialTheme.typography.bodyMedium
            )
            if (activeLockMode != LockMode.NONE && activeLockMode != LockMode.SECURITY_KEY) {
                Text(
                    text = "Another mode is active ($activeLockMode). Disable it before enabling Security Key mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = securityKey,
                onValueChange = { securityKey = it },
                label = { Text("Security Key") },
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
                text = "Current recovery email: ${recoveryEmail.ifBlank { "Not set" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (recoveryEmailVerified) "Recovery email verified" else "Recovery email not verified yet",
                style = MaterialTheme.typography.bodySmall,
                color = if (recoveryEmailVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    val trimmedEmail = recoveryEmail.trim()
                    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                        Toast.makeText(context, "Enter a valid recovery email", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    prefs.edit {
                        putString("recovery_email_security_key", trimmedEmail)
                        putBoolean("recovery_email_security_key_verified", false)
                    }
                    recoveryEmailVerified = false

                    RecoveryEmailHelper.sendRecoveryLink(
                        context = context,
                        email = trimmedEmail,
                        purpose = RecoveryEmailHelper.PURPOSE_SETUP_SECURITY_KEY,
                        onSuccess = {
                            Toast.makeText(
                                context,
                                "Verification link sent. Open it from your email, then return here.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify Recovery Email")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enableAdminProtection, onCheckedChange = { enableAdminProtection = it })
                Text("Use Device Admin to harden uninstall protection (optional)")
            }
            Text("Device Admin: ${if (isAdminGranted) "Enabled" else "Not enabled"}")
            Text("Device Admin is optional and only used for uninstall hardening.")

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
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    if (activeLockMode != LockMode.NONE && activeLockMode != LockMode.SECURITY_KEY) {
                        Toast.makeText(
                            context,
                            "Only one lock mode can be enabled at a time. Disable $activeLockMode first.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (securityKey.isBlank()) {
                        Toast.makeText(context, "Enter a security key", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val trimmedEmail = recoveryEmail.trim()
                    if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                        Toast.makeText(context, "Enter a valid recovery email", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val emailVerifiedForCurrentValue =
                        prefs.getBoolean("recovery_email_security_key_verified", false) &&
                            prefs.getString("recovery_email_security_key", null)
                                .equals(trimmedEmail, ignoreCase = true)
                    if (!emailVerifiedForCurrentValue) {
                        Toast.makeText(
                            context,
                            "Verify the recovery email first using the email link",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (enableAdminProtection && !isAdminGranted) {
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
                        putString("recovery_email_security_key", trimmedEmail)
                        putBoolean("recovery_email_security_key_verified", true)
                        putBoolean("useDeviceAdminUninstallProtection", enableAdminProtection)
                        putBoolean("locked", true)
                        putBoolean("securityKeyUnlocked", false)
                        putBoolean("passUnlocked", false)
                    }
                    if (enableAdminProtection) {
                        BackgroundChecker.startServiceIfNotRunning(context.applicationContext)
                    }
                    Toast.makeText(context, "Security key enabled", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
                enabled = activeLockMode == LockMode.NONE || activeLockMode == LockMode.SECURITY_KEY,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeLockMode == LockMode.NONE || activeLockMode == LockMode.SECURITY_KEY) {
                    Text("Enable Security Key Mode")
                } else {
                    Text("Disable $activeLockMode first")
                }
            }

            if (activeLockMode == LockMode.SECURITY_KEY) {
                Button(
                    onClick = {
                        prefs.edit {
                            remove("security_key_value")
                            putBoolean("security_key_enabled", false)
                            putString("active_lock_mode", "NONE")
                            putBoolean("securityKeyUnlocked", false)
                        }
                        Toast.makeText(context, "Security key disabled", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disable Security Key Mode")
                }
            }
        }
    }
}
