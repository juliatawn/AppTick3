package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.BaseActivity
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.ThemeModeManager
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.GroupAppItem
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupPage : BaseActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val group = intent.getSerializableExtra(EXTRA_GROUP) as? AppLimitGroup
        var showActionsDialog by mutableStateOf(false)

        setContent {
            val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(this)
            val isPremium = prefs.getBoolean("premium", false)

            val savedPrimaryColor = prefs.getInt("custom_primary_color", 0)
            val savedBackgroundColor = prefs.getInt("custom_background_color", 0)
            val savedIconColor = prefs.getInt("custom_icon_color", 0)
            val appIconColorMode = prefs.getString("app_icon_color_mode", "system") ?: "system"

            val composePrimary = if (savedPrimaryColor != 0) Color(savedPrimaryColor) else Color(0xFF3949AB)
            val defaultBackground = if (isSystemDark) Color.Black else Color.White
            val composeBackground = if (savedBackgroundColor != 0) Color(savedBackgroundColor) else defaultBackground

            val systemThemeIconColor =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isSystemDark) dynamicDarkColorScheme(this).primary else dynamicLightColorScheme(this).primary
                } else {
                    null
                }
            val fallbackIconColor =
                if (androidx.core.graphics.ColorUtils.calculateLuminance(composeBackground.toArgb()) > 0.5) Color.Black else Color.White
            val composeIconColor =
                if (isPremium && appIconColorMode == "custom" && savedIconColor != 0) Color(savedIconColor)
                else systemThemeIconColor?.takeIf { customColorModeEnabled } ?: fallbackIconColor

            val colorScheme = if (customColorModeEnabled) {
                val useDarkScheme = composeBackground.luminance() < 0.4f
                if (useDarkScheme) {
                    darkColorScheme(
                        primary = composePrimary,
                        background = composeBackground,
                        surface = composeBackground,
                        primaryContainer = composePrimary.copy(alpha = 0.24f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    )
                } else {
                    lightColorScheme(
                        primary = composePrimary,
                        background = composeBackground,
                        surface = composeBackground,
                        primaryContainer = composePrimary.copy(alpha = 0.16f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    )
                }
            } else if (isSystemDark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("AppTick") },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    },
                    floatingActionButton = {
                        if (group != null) {
                            FloatingActionButton(onClick = { showActionsDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit or delete group")
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        group?.let {
                            GroupDetails(it)
                        }
                    }
                }

                if (showActionsDialog && group != null) {
                    AlertDialog(
                        onDismissRequest = { showActionsDialog = false },
                        title = { Text("Group Options") },
                        text = { Text("Choose what you want to do with this app limit group.") },
                        confirmButton = {
                            Button(onClick = {
                                showActionsDialog = false
                                val editIntent = Intent(this@GroupPage, MainActivity::class.java).apply {
                                    putExtra(MainActivity.EXTRA_EDIT_GROUP_ID, group.id)
                                }
                                startActivity(editIntent)
                                finish()
                            }) {
                                Text("Edit")
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    showActionsDialog = false
                                    lifecycleScope.launch {
                                        val dao = AppTickDatabase.getDatabase(applicationContext).appLimitGroupDao()
                                        dao.deleteAppLimitGroup(group.toEntity())
                                        if (dao.getActiveGroupCount() <= 0) {
                                            stopService(Intent(this@GroupPage, BackgroundChecker::class.java))
                                        }
                                        Toast.makeText(
                                            this@GroupPage,
                                            "Group deleted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        finish()
                                    }
                                }) {
                                    Text("Delete")
                                }
                                OutlinedButton(onClick = { showActionsDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_GROUP = "extra_group"

        fun newIntent(context: Context, group: AppLimitGroup): Intent {
            return Intent(context, GroupPage::class.java).apply {
                putExtra(EXTRA_GROUP, group)
            }
        }
    }
}

@Composable
fun GroupDetails(group: AppLimitGroup) {
    val usageByPackage = group.perAppUsage.associate { it.appPackage to it.usedMillis }
    val groupUsedMillis = group.perAppUsage.sumOf { it.usedMillis.coerceAtLeast(0L) }
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = group.name ?: "App Limit Group",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = formatTimeRemaining(group.timeRemaining),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Time Left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Time Used: ${formatTimeRemaining(groupUsedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (group.cumulativeTime) {
                    val limitInMillis = (group.timeHrLimit * 60 + group.timeMinLimit) * 60_000L
                    val carriedOver = (group.timeRemaining - limitInMillis).coerceAtLeast(0L)
                    if (carriedOver > 0) {
                        Text(
                            text = "(Includes ${formatTimeRemaining(carriedOver)} carried over)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${group.timeHrLimit}h ${group.timeMinLimit}m Limit") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(if (group.limitEach) "Limit for EACH" else "Limit for ALL") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
                
                if (group.cumulativeTime) {
                     AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Cumulative Time Enabled") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }

                if (group.useTimeRange) {
                    Text(
                        text = "Active Hours: ${formatTime(group.startHour, group.startMinute)} - ${formatTime(group.endHour, group.endMinute)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "Active Days: ${formatDays(group.weekDays)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                val resetText = if (group.resetHours > 0) {
                    val interval = "${group.resetHours / 60}h ${group.resetHours % 60}m"
                    if (group.cumulativeTime) {
                        "Cumulative: Daily + every $interval"
                    } else {
                        "Resets: Daily every $interval"
                    }
                } else {
                    "Resets: Daily"
                }
                Text(
                    text = resetText,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Next Reset: ${formatNextReset(group.nextResetTime)}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(group.apps) { app ->
                val appInfo = AppInfo(
                    appName = app.appName,
                    appPackage = app.appPackage,
                    appTimeUse = usageByPackage[app.appPackage] ?: 0L
                )
                GroupAppItem(
                    appInfo = appInfo,
                    timeLimit = group.timeHrLimit * 60 + group.timeMinLimit,
                    limitEach = group.limitEach
                )
            }
        }
    }
}

private fun formatTimeRemaining(milliseconds: Long): String {
    val hours = milliseconds / 3_600_000
    val minutes = (milliseconds % 3_600_000) / 60_000
    return "${hours}h ${minutes}m"
}

private fun formatNextReset(nextResetMillis: Long): String {
    if (nextResetMillis <= 0L) return "Not scheduled"
    return SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(nextResetMillis))
}

private fun formatTime(hour: Int, minute: Int): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
}

private fun formatDays(days: List<Int>?): String {
    if (days == null || days.isEmpty()) return "None"
    if (days.size == 7) return "Every Day"
    // Assuming 1 = Monday based on SetTimeLimitsScreen
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().joinToString(", ") { dayIndex ->
        if (dayIndex in 1..7) names[dayIndex - 1] else "?"
    }
}
