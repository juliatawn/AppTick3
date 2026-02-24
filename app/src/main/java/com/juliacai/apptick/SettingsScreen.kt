package com.juliacai.apptick

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppLimitBackupManager
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.GroupCardOrderStore
import com.juliacai.apptick.permissions.BatteryOptimizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onCustomizeColors: () -> Unit,
    onUpgradeToPremium: () -> Unit,
    onOpenPremiumModeInfo: () -> Unit,
    onOpenAppLimitBackup: () -> Unit,
    onOpenChangelog: () -> Unit
) {
    val context = LocalContext.current
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    var isPremium by remember { mutableStateOf(groupPrefs.getBoolean("premium", false)) }

    var isDarkMode by remember { mutableStateOf(ThemeModeManager.isDarkModeEnabled(context)) }
    var isCustomColorMode by remember { mutableStateOf(ThemeModeManager.isCustomColorModeEnabled(context)) }
    var showTimeLeft by remember { mutableStateOf(groupPrefs.getBoolean("showTimeLeft", true)) }
    var floatingBubbleEnabled by remember { mutableStateOf(groupPrefs.getBoolean("floatingBubbleEnabled", false)) }
    var premiumFeatureDialogFor by remember { mutableStateOf<String?>(null) }
    var batteryStatus by remember { mutableStateOf(BatteryOptimizationHelper.getStatus(context)) }
    var showBatteryDialog by remember { mutableStateOf(false) }

    fun promptPremium(featureName: String) {
        premiumFeatureDialogFor = featureName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        maxLines = 1,
                        softWrap = false
                    )
                },
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
                .padding(vertical = 16.dp)
                .verticalScrollWithIndicator()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                Spacer(modifier = Modifier.height(16.dp))
            }
            Divider()
            SettingsNavigationItem(
                title = "App Limit Backup",
                onClick = {
                    if (!isPremium) {
                        promptPremium("App Limit Backup")
                        return@SettingsNavigationItem
                    }
                    onOpenAppLimitBackup()
                }
            )
            SettingsNavigationItem(
                title = "Changelog",
                onClick = onOpenChangelog
            )
            SettingsNavigationItem(
                title = "Battery Reliability",
                onClick = {
                    batteryStatus = BatteryOptimizationHelper.getStatus(context)
                    showBatteryDialog = true
                }
            )
            if (isPremium) {
                SettingsNavigationItem(
                    title = "Premium Mode Info",
                    onClick = onOpenPremiumModeInfo
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                /*
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
                */

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onvarise.com"))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Check Out Upcoming/Current Apps at OnvaRise.com",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
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

    if (showBatteryDialog) {
        val detailItems = buildList {
            add("Ignore battery optimizations:" to if (batteryStatus.ignoringBatteryOptimizations) "On" else "Off")
            add("Background restricted:" to if (batteryStatus.backgroundRestricted) "Yes" else "No")
            if (batteryStatus.hasAdditionalOemRestrictions) {
                add("OEM startup controls:" to "Detected")
            }
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("Battery Reliability") },
            text = {
                Column {
                    Text(
                        if (batteryStatus.unrestricted) {
                            "AppTick battery mode is set for reliable background tracking."
                        } else {
                            "Set AppTick to Unrestricted battery mode for stronger blocking reliability."
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    detailItems.forEach { (label, value) ->
                        Text(
                            text = buildAnnotatedString {
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                append(label)
                                pop()
                                append(" ")
                                append(value)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    val oemGuidance = batteryStatus.oemGuidance
                    if (oemGuidance != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            oemGuidance,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Some manufacturers aggressively kill apps in the background. If reliability issues continue, review device-specific steps at dontkillmyapp.com.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (!BatteryOptimizationHelper.openAppBatterySettings(context)) {
                                Toast.makeText(context, "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Battery Settings")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!BatteryOptimizationHelper.openGeneralBatterySettings(context)) {
                                Toast.makeText(context, "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open General Battery Settings")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!BatteryOptimizationHelper.openDontKillMyApp(context)) {
                                Toast.makeText(context, "Unable to open dontkillmyapp.com", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open dontkillmyapp.com")
                    }
                    if (batteryStatus.hasAdditionalOemRestrictions) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (!BatteryOptimizationHelper.openManufacturerBackgroundSettings(context)) {
                                    Toast.makeText(context, "Unable to open OEM startup settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open OEM Startup Settings")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            batteryStatus = BatteryOptimizationHelper.getStatus(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Status")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBatteryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Divider()
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            onBackClick = {},
            onCustomizeColors = {},
            onUpgradeToPremium = {},
            onOpenPremiumModeInfo = {},
            onOpenAppLimitBackup = {},
            onOpenChangelog = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitBackupScreen(
    onBackClick: () -> Unit
) {
    data class ImportSummary(
        val importedGroupCount: Int,
        val removedAppCount: Int,
        val droppedGroupCount: Int,
        val limitsActive: Boolean
    )

    val context = LocalContext.current
    val appContext = context.applicationContext
    val groupPrefs = remember { context.getSharedPreferences("groupPrefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    val dao = remember { AppTickDatabase.getDatabase(context).appLimitGroupDao() }
    var backupInProgress by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var lastRestoreResultMessage by remember { mutableStateOf<String?>(null) }

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
                Toast.makeText(appContext, "Backup saved.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    appContext,
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
                    val installedPackages = context.packageManager
                        .getInstalledApplications(0)
                        .map { it.packageName }
                        .toSet()

                    var removedAppCount = 0
                    var droppedGroupCount = 0
                    val importedGroups = backup.groups.mapNotNull { group ->
                        val filteredApps = group.apps.filter { app ->
                            val exists = installedPackages.contains(app.appPackage)
                            if (!exists) {
                                removedAppCount += 1
                            }
                            exists
                        }
                        if (filteredApps.isEmpty()) {
                            droppedGroupCount += 1
                            return@mapNotNull null
                        }

                        val sanitized = group.copy(
                            // Keep backup IDs stable so saved main-page card ordering remains valid.
                            id = if (group.id > 0L) group.id else 0L,
                            apps = filteredApps,
                            timeRemaining = 0L,
                            nextResetTime = 0L,
                            nextAddTime = 0L,
                            perAppUsage = emptyList()
                        )
                        val limitInMillis =
                            ((sanitized.timeHrLimit * 60L) + sanitized.timeMinLimit.toLong())
                                .coerceAtLeast(0L) * 60_000L
                        val now = System.currentTimeMillis()
                        val nextReset = if (sanitized.resetMinutes > 0) {
                            now + TimeUnit.MINUTES.toMillis(sanitized.resetMinutes.toLong())
                        } else {
                            TimeManager.nextMidnight(now)
                        }
                        val nextAdd = if (sanitized.cumulativeTime && sanitized.resetMinutes > 0) {
                            nextReset
                        } else {
                            0L
                        }
                        sanitized.copy(
                            timeRemaining = limitInMillis,
                            nextResetTime = nextReset,
                            nextAddTime = nextAdd
                        )
                    }
                    dao.replaceAllAppLimitGroups(importedGroups)
                    val importedIds = importedGroups.map { it.id }.filter { it > 0L }
                    val appSettingsToApply = backup.appSettings.copy(
                        groupCardOrder = backup.appSettings.groupCardOrder?.let { savedOrder ->
                            GroupCardOrderStore.sanitizeOrder(savedOrder, importedIds)
                        }?.takeIf { it.isNotEmpty() }
                    )
                    AppLimitBackupManager.applyAppSettings(groupPrefs, appSettingsToApply)

                    val activeGroupCount = dao.getActiveGroupCount()
                    BackgroundChecker.applyDesiredServiceState(
                        context.applicationContext,
                        activeGroupCount > 0
                    )
                    ImportSummary(
                        importedGroupCount = importedGroups.size,
                        removedAppCount = removedAppCount,
                        droppedGroupCount = droppedGroupCount,
                        limitsActive = activeGroupCount > 0
                    )
                }
            }
            backupInProgress = false
            result.onSuccess { summary ->
                ThemeModeManager.apply(context)
                context.sendBroadcast(Intent("COLORS_CHANGED").setPackage(context.packageName))
                val message = buildString {
                    append("Imported ${summary.importedGroupCount}. Removed ${summary.removedAppCount} apps, ${summary.droppedGroupCount} groups.")
                    if (summary.limitsActive) {
                        append(" Limits are now active.")
                    }
                }
                Toast.makeText(
                    appContext,
                    message,
                    Toast.LENGTH_LONG
                ).show()
                lastRestoreResultMessage = message
            }.onFailure { error ->
                val message = "Import failed: ${error.localizedMessage ?: "Unknown error"}"
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                lastRestoreResultMessage = message
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
                title = {
                    Text(
                        text = "App Limit Backup",
                        maxLines = 1,
                        softWrap = false
                    )
                },
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
                .verticalScrollWithIndicator()
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "If an app from the backup is not installed on this phone, its app limit entry is skipped during import.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            lastRestoreResultMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Last restore result: $message",
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
