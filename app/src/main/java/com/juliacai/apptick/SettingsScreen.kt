package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onCustomizeColors: () -> Unit,
    onUpgradeToPremium: () -> Unit
) {
    val context = LocalContext.current
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    var isPremium by remember { mutableStateOf(groupPrefs.getBoolean("premium", false)) }

    var isDarkMode by remember { mutableStateOf(ThemeModeManager.isDarkModeEnabled(context)) }
    var isCustomColorMode by remember { mutableStateOf(ThemeModeManager.isCustomColorModeEnabled(context)) }
    var showTimeLeft by remember { mutableStateOf(groupPrefs.getBoolean("showTimeLeft", true)) }
    var hasPassword by remember { mutableStateOf(groupPrefs.getString("password", null) != null) }
    var premiumFeatureDialogFor by remember { mutableStateOf<String?>(null) }

    fun promptPremium(featureName: String) {
        premiumFeatureDialogFor = featureName
    }

    val onRemovePasswordClick: () -> Unit = {
        val passwordInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter current password"
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Confirm Password")
            .setView(passwordInput)
            .setPositiveButton("Confirm") { _, _ ->
                val enteredPassword = passwordInput.text.toString()
                val currentPassword = groupPrefs.getString("password", "")
                if (enteredPassword == currentPassword) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Remove Password Protection")
                        .setMessage("Are you sure you want to remove password protection?")
                        .setPositiveButton("Yes, Remove Password") { _, _ ->
                            groupPrefs.edit {
                                remove("password")
                                putBoolean("locked", false)
                                putBoolean("blockMain", false)
                                putBoolean("blockSettings", false)
                            }
                            hasPassword = false
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Incorrect Password")
                        .setMessage("The password you entered is incorrect.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp)
        ) {
            if (!isPremium) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Unlock Premium",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Dark mode and custom colors are premium features.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onUpgradeToPremium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upgrade to Premium")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = {
                        if (!isPremium) {
                            promptPremium("Dark Mode")
                            return@Switch
                        }
                        isDarkMode = it
                        ThemeModeManager.persistDarkMode(context, it)
                        ThemeModeManager.apply(context)
                        isCustomColorMode = ThemeModeManager.isCustomColorModeEnabled(context)
                        context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom Color Mode", modifier = Modifier.weight(1f))
                Switch(
                    checked = isCustomColorMode,
                    onCheckedChange = {
                        if (!isPremium) {
                            promptPremium("Custom Color Mode")
                            return@Switch
                        }
                        isCustomColorMode = it
                        ThemeModeManager.persistCustomColorMode(context, it)
                        ThemeModeManager.apply(context)
                        isDarkMode = ThemeModeManager.isDarkModeEnabled(context)
                        context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Time Left in Notification", modifier = Modifier.weight(1f))
                Switch(
                    checked = showTimeLeft,
                    onCheckedChange = {
                        showTimeLeft = it
                        groupPrefs.edit { putBoolean("showTimeLeft", it) }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!isPremium) {
                        promptPremium("Customize Colors")
                        return@Button
                    }
                    if (!isCustomColorMode) {
                        isCustomColorMode = true
                        ThemeModeManager.persistCustomColorMode(context, true)
                        ThemeModeManager.apply(context)
                        isDarkMode = ThemeModeManager.isDarkModeEnabled(context)
                        context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                    }
                    onCustomizeColors()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Customize Colors")
            }
            if (!isPremium) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap premium features to see upgrade options.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (hasPassword) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Password Protection")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRemovePasswordClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remove Password")
                        }
                    }
                }
            }

            // Debug-only premium toggle
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Debug Options", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Premium Mode (Debug)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPremium,
                        onCheckedChange = {
                            isPremium = it
                            groupPrefs.edit { putBoolean("premium", it) }
                        }
                    )
                }
                Text("Toggle to simulate premium purchase in emulator", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }

    premiumFeatureDialogFor?.let { featureName ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { premiumFeatureDialogFor = null },
            title = { Text("Premium Feature") },
            text = { Text("$featureName is available in Premium Mode.") },
            confirmButton = {
                Button(
                    onClick = {
                        premiumFeatureDialogFor = null
                        onUpgradeToPremium()
                    }
                ) {
                    Text("Buy Premium")
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(onClick = { premiumFeatureDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
