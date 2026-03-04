package com.juliacai.apptick.lockModes

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.safePop
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.premiumMode.DeviceAdmin
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityKeySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val activity = context as? Activity
    val usbManager = remember(context) {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    val devicePolicyManager = remember(context) {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }
    val adminComponentName = remember(context) {
        ComponentName(context, DeviceAdmin::class.java)
    }

    var activeLockMode by remember {
        mutableStateOf(
            runCatching {
                LockMode.valueOf(prefs.getString("active_lock_mode", "NONE") ?: "NONE")
            }.getOrDefault(LockMode.NONE)
        )
    }
    var registeredKey by remember {
        mutableStateOf(UsbSecurityKey.readRegisteredKey(prefs))
    }
    var lockSettingsApp by remember {
        mutableStateOf(
            if (activeLockMode == LockMode.SECURITY_KEY) {
                prefs.getBoolean("useDeviceAdminUninstallProtection", false)
            } else {
                false
            }
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
                registeredKey = UsbSecurityKey.readRegisteredKey(prefs)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security Key Settings",
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.safePop() }) {
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
                        "Register a connected USB hardware key. That same key must be plugged in to unlock Security Key mode.",
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

            Text(
                text = "Registered USB key: ${registeredKey?.displayName() ?: "None"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    val connected = UsbSecurityKey.connectedUsbDevices(usbManager)
                    if (connected.isEmpty()) {
                        Toast.makeText(context, "No USB key detected. Plug one in and try again.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val selected = connected.first()
                    val key = UsbSecurityKey.fromUsbDevice(selected)
                    registeredKey = key
                    Toast.makeText(
                        context,
                        "Registered ${key.displayName()}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                enabled = activeLockMode == LockMode.NONE || activeLockMode == LockMode.SECURITY_KEY,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register Connected USB Key")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = lockSettingsApp, onCheckedChange = { lockSettingsApp = it })
                Text("Lock Settings app to block AppTick uninstall attempts")
            }
            Text("Requires Device Admin when this option is enabled.")
            Text("Device Admin: ${if (isAdminGranted) "Enabled" else "Not enabled"}")

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
                    if (activeLockMode != LockMode.NONE && activeLockMode != LockMode.SECURITY_KEY) {
                        Toast.makeText(
                            context,
                            "Only one lock mode can be enabled at a time. Disable $activeLockMode first.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (registeredKey == null) {
                        Toast.makeText(context, "Register a USB security key first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (lockSettingsApp && !isAdminGranted) {
                        Toast.makeText(
                            context,
                            "Enable Device Admin before locking the Settings app",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    prefs.edit {
                        putBoolean("security_key_enabled", true)
                        putString("active_lock_mode", "SECURITY_KEY")
                        remove("security_key_value")
                        putString("security_usb_key_fingerprint", registeredKey!!.toPersistedString())
                        putBoolean("useDeviceAdminUninstallProtection", lockSettingsApp)
                        putBoolean("locked", true)
                        putBoolean("securityKeyUnlocked", false)
                        putBoolean("passUnlocked", false)
                    }
                    if (lockSettingsApp) {
                        BackgroundChecker.startServiceIfNotRunning(context.applicationContext)
                    }
                    Toast.makeText(context, "Security key enabled", Toast.LENGTH_SHORT).show()
                    navController.safePop()
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
                            remove("security_usb_key_fingerprint")
                            putBoolean("security_key_enabled", false)
                            putString("active_lock_mode", "NONE")
                            putBoolean("securityKeyUnlocked", false)
                        }
                        Toast.makeText(context, "Security key disabled", Toast.LENGTH_SHORT).show()
                        navController.safePop()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disable Security Key Mode")
                }
            }
        }
    }
}
