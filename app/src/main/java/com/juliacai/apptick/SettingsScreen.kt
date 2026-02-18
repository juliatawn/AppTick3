package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppLimitBackupManager
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.newAppLimit.normalizeGroupForPersistence
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onCustomizeColors: () -> Unit,
    onUpgradeToPremium: () -> Unit,
    onOpenPremiumModeInfo: () -> Unit,
    onOpenAppLimitBackup: () -> Unit
) {
    val context = LocalContext.current
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    var isPremium by remember { mutableStateOf(groupPrefs.getBoolean("premium", false)) }

    var isDarkMode by remember { mutableStateOf(ThemeModeManager.isDarkModeEnabled(context)) }
    var isCustomColorMode by remember { mutableStateOf(ThemeModeManager.isCustomColorModeEnabled(context)) }
    var showTimeLeft by remember { mutableStateOf(groupPrefs.getBoolean("showTimeLeft", true)) }
    var floatingBubbleEnabled by remember { mutableStateOf(groupPrefs.getBoolean("floatingBubbleEnabled", false)) }
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
                .verticalScroll(rememberScrollState())
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Time Left Bubble")
                    Text(
                        "Shows a small overlay with time remaining when using limited apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = floatingBubbleEnabled,
                    onCheckedChange = {
                        if (!isPremium) {
                            promptPremium("Floating Time Left Bubble")
                            return@Switch
                        }
                        floatingBubbleEnabled = it
                        groupPrefs.edit { putBoolean("floatingBubbleEnabled", it) }
                        // Clear dismissed flag when toggling on so bubble shows immediately
                        if (it) {
                            groupPrefs.edit { putBoolean("bubbleDismissed", false) }
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!isPremium) {
                        promptPremium("App Limit Backup")
                        return@Button
                    }
                    onOpenAppLimitBackup()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("App Limit Backup")
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
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenPremiumModeInfo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Premium Mode Info")
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
                            if (it) {
                                groupPrefs.edit {
                                    putBoolean("debug_force_free", false)
                                    putBoolean("premium", true)
                                }
                            } else {
                                // Force free-user behavior even if billing callbacks run.
                                groupPrefs.edit {
                                    putBoolean("debug_force_free", true)
                                    putBoolean("premium", false)
                                    putBoolean("floatingBubbleEnabled", false)
                                }
                                isCustomColorMode = false
                                isDarkMode = false
                                floatingBubbleEnabled = false
                                ThemeModeManager.persistCustomColorMode(context, false)
                                ThemeModeManager.persistDarkMode(context, false)
                                ThemeModeManager.apply(context)
                                context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitBackupScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppTickDatabase.getDatabase(context).appLimitGroupDao() }
    var backupInProgress by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun exportBackupToUri(uri: android.net.Uri) {
        coroutineScope.launch {
            backupInProgress = true
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val groups = dao.getAllAppLimitGroupsImmediate()
                    val backup = AppLimitBackupManager.createBackup(
                        groups = groups,
                        appSettings = AppLimitBackupManager.collectAppSettings(groupPrefs)
                    )
                    AppLimitBackupManager.writeBackupToUri(context, uri, backup)
                }.exceptionOrNull()
            }
            backupInProgress = false
            if (error == null) {
                Toast.makeText(context, "Backup saved.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    context,
                    "Backup failed: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun importBackupFromUri(uri: android.net.Uri) {
        coroutineScope.launch {
            backupInProgress = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val backup = AppLimitBackupManager.readBackupFromUri(context, uri)
                    val importedGroups = backup.groups.map { group ->
                        normalizeGroupForPersistence(
                            group = group.copy(
                                id = 0L,
                                timeRemaining = 0L,
                                nextResetTime = 0L,
                                nextAddTime = 0L,
                                perAppUsage = emptyList()
                            ).toDomainModel(),
                            normalizedUsage = emptyList()
                        ).toEntity()
                    }
                    dao.replaceAllAppLimitGroups(importedGroups)
                    AppLimitBackupManager.applyAppSettings(groupPrefs, backup.appSettings)
                    ThemeModeManager.apply(context)
                    context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))

                    val activeGroupCount = dao.getActiveGroupCount()
                    if (activeGroupCount > 0) {
                        BackgroundChecker.startServiceIfNotRunning(context.applicationContext)
                    } else {
                        context.applicationContext.stopService(
                            Intent(context.applicationContext, BackgroundChecker::class.java)
                        )
                    }
                    backup
                }
            }
            backupInProgress = false
            result.onSuccess { backup ->
                Toast.makeText(
                    context,
                    "Imported ${backup.groups.size} app limit group(s) and AppTick settings.",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Import failed: ${error.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportBackupToUri(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Limit Backup") },
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Back up all your app limit settings and AppTick app settings to a file.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Import your app limit settings and AppTick app settings from a backup file; it will overwrite your current app limit and app settings.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (backupInProgress) return@Button
                    val timestamp = SimpleDateFormat(
                        "yyyyMMdd-HHmmss",
                        Locale.US
                    ).format(Date())
                    exportBackupLauncher.launch("apptick-backup-$timestamp.json")
                },
                enabled = !backupInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Backup")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (backupInProgress) return@Button
                    importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = !backupInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore Backup")
            }
            if (backupInProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Processing backup...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pendingImportUri?.let { importUri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "This will replace your current app limit groups and restore settings from the backup file."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImportUri = null
                        importBackupFromUri(importUri)
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(onClick = { pendingImportUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
