package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.BaseActivity
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.ThemeModeManager
import com.juliacai.apptick.formatClockTime
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.GroupAppItem
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupPage : BaseActivity() {
    private var groupId: Long = -1L
    private var group by mutableStateOf<AppLimitGroup?>(null)
    private var showActionsDialog by mutableStateOf(false)

    private fun refreshGroup() {
        if (groupId <= 0L) return
        lifecycleScope.launch {
            group = AppTickDatabase.getDatabase(applicationContext)
                .appLimitGroupDao()
                .getGroup(groupId)
                ?.toDomainModel()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        refreshGroup()

        setContent {
            val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val customColorModeEnabled = ThemeModeManager.isCustomColorModeEnabled(this)
            val isPremium = prefs.getBoolean("premium", false)

            val savedPrimaryColor = prefs.getInt("custom_primary_color", 0)
            val savedBackgroundColor = prefs.getInt("custom_background_color", 0)
            val savedCardColor = prefs.getInt("custom_card_color", 0)
            val savedIconColor = prefs.getInt("custom_icon_color", 0)
            val appIconColorMode = prefs.getString("app_icon_color_mode", "system") ?: "system"

            val composePrimary = if (savedPrimaryColor != 0) Color(savedPrimaryColor) else Color(0xFF3949AB)
            val defaultBackground = if (isSystemDark) Color.Black else Color.White
            val composeBackground = if (savedBackgroundColor != 0) Color(savedBackgroundColor) else defaultBackground
            val composeCard = if (savedCardColor != 0) Color(savedCardColor) else composeBackground

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
                        surface = composeCard,
                        primaryContainer = composePrimary.copy(alpha = 0.24f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    ).copy(
                        surfaceVariant = composeCard,
                        surfaceContainerLowest = composeCard,
                        surfaceContainerLow = composeCard,
                        surfaceContainer = composeCard,
                        surfaceContainerHigh = composeCard,
                        surfaceContainerHighest = composeCard
                    )
                } else {
                    lightColorScheme(
                        primary = composePrimary,
                        background = composeBackground,
                        surface = composeCard,
                        primaryContainer = composePrimary.copy(alpha = 0.16f),
                        onPrimary = composeIconColor,
                        onBackground = composeIconColor,
                        onSurface = composeIconColor,
                        onPrimaryContainer = composeIconColor
                    ).copy(
                        surfaceVariant = composeCard,
                        surfaceContainerLowest = composeCard,
                        surfaceContainerLow = composeCard,
                        surfaceContainer = composeCard,
                        surfaceContainerHigh = composeCard,
                        surfaceContainerHighest = composeCard
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
                            GroupDetails(
                                group = it,
                                onAppsReordered = { reorderedApps ->
                                    val current = group ?: return@GroupDetails
                                    if (current.apps == reorderedApps) return@GroupDetails
                                    val updatedGroup = current.copy(apps = reorderedApps)
                                    group = updatedGroup
                                    lifecycleScope.launch {
                                        AppTickDatabase.getDatabase(applicationContext)
                                            .appLimitGroupDao()
                                            .updateAppLimitGroup(updatedGroup.toEntity())
                                    }
                                }
                            )
                        }
                    }
                }

                val currentGroup = group
                if (showActionsDialog && currentGroup != null) {
                    GroupActionsDialog(
                        onDismiss = { showActionsDialog = false },
                        onEdit = {
                            showActionsDialog = false
                            val editIntent = Intent(this@GroupPage, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_EDIT_GROUP_ID, currentGroup.id)
                            }
                            startActivity(editIntent)
                            finish()
                        },
                        onDelete = {
                            showActionsDialog = false
                            lifecycleScope.launch {
                                val dao = AppTickDatabase.getDatabase(applicationContext).appLimitGroupDao()
                                dao.deleteAppLimitGroup(currentGroup.toEntity())
                                BackgroundChecker.applyDesiredServiceState(
                                    applicationContext,
                                    dao.getActiveGroupCount() > 0
                                )
                                Toast.makeText(
                                    this@GroupPage,
                                    "Group deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGroup()
    }

    companion object {
        private const val EXTRA_GROUP_ID = "extra_group_id"

        fun newIntent(context: Context, group: AppLimitGroup): Intent {
            return Intent(context, GroupPage::class.java).apply {
                putExtra(EXTRA_GROUP_ID, group.id)
            }
        }
    }
}

@Composable
fun GroupActionsDialog(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Options") },
        text = { Text("Choose what you want to do with this app limit group.") },
        confirmButton = {
            Button(onClick = onEdit) {
                Text("Edit")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun GroupDetails(
    group: AppLimitGroup,
    onAppsReordered: (List<com.juliacai.apptick.appLimit.AppInGroup>) -> Unit = {}
) {
    val context = LocalContext.current
    val usageByPackage = group.perAppUsage.associate { it.appPackage to it.usedMillis }
    val groupUsedMillis = group.perAppUsage.sumOf { it.usedMillis.coerceAtLeast(0L) }
    val listState = rememberLazyListState()
    val orderedApps = remember { mutableStateListOf<com.juliacai.apptick.appLimit.AppInGroup>() }
    var draggingAppPackage by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var orderChangedDuringDrag by remember { mutableStateOf(false) }
    val showCompactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 180
        }
    }
    LaunchedEffect(group.apps) {
        if (orderedApps != group.apps) {
            orderedApps.clear()
            orderedApps.addAll(group.apps)
        }
        draggingAppPackage = null
        draggingOffsetY = 0f
        orderChangedDuringDrag = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
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
                                text = "Active Hours: ${formatTime(context, group.startHour, group.startMinute)} - ${
                                    formatTime(
                                        context,
                                        group.endHour,
                                        group.endMinute
                                    )
                                }",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (group.blockOutsideTimeRange) {
                                    "Outside Range: Block Apps"
                                } else {
                                    "Outside Range: Allow No Limits"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "Active Days: ${formatDays(group.weekDays)}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val resetText = if (group.resetMinutes > 0) {
                            val interval = "${group.resetMinutes / 60}h ${group.resetMinutes % 60}m"
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
            }

            item {
                Text(
                    text = "Tip: Long-press and drag app cards to reorder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(orderedApps, key = { it.appPackage }) { app ->
                val appInfo = AppInfo(
                    appName = app.appName,
                    appPackage = app.appPackage,
                    appTimeUse = usageByPackage[app.appPackage] ?: 0L
                )
                val isDragging = draggingAppPackage == app.appPackage
                val dragModifier = Modifier.pointerInput(app.appPackage, orderedApps.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingAppPackage = app.appPackage
                            draggingOffsetY = 0f
                            orderChangedDuringDrag = false
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingAppPackage != app.appPackage) return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggingOffsetY += dragAmount.y

                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val draggedInfo = visibleItems.firstOrNull { it.key == app.appPackage }
                                ?: return@detectDragGesturesAfterLongPress
                            val draggedCenterY = draggedInfo.offset + (draggedInfo.size / 2f) + draggingOffsetY
                            val targetInfo = visibleItems.firstOrNull { itemInfo ->
                                val targetKey = itemInfo.key as? String ?: return@firstOrNull false
                                targetKey != app.appPackage &&
                                    draggedCenterY >= itemInfo.offset &&
                                    draggedCenterY <= itemInfo.offset + itemInfo.size
                            } ?: return@detectDragGesturesAfterLongPress

                            val fromIndex = orderedApps.indexOfFirst { it.appPackage == app.appPackage }
                            val targetPackage = targetInfo.key as? String ?: return@detectDragGesturesAfterLongPress
                            val toIndex = orderedApps.indexOfFirst { it.appPackage == targetPackage }
                            if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) {
                                return@detectDragGesturesAfterLongPress
                            }

                            val moved = orderedApps.removeAt(fromIndex)
                            orderedApps.add(toIndex, moved)
                            draggingOffsetY += (draggedInfo.offset - targetInfo.offset).toFloat()
                            orderChangedDuringDrag = true
                        },
                        onDragEnd = {
                            if (orderChangedDuringDrag) {
                                onAppsReordered(orderedApps.toList())
                            }
                            draggingAppPackage = null
                            draggingOffsetY = 0f
                            orderChangedDuringDrag = false
                        },
                        onDragCancel = {
                            if (orderChangedDuringDrag) {
                                onAppsReordered(orderedApps.toList())
                            }
                            draggingAppPackage = null
                            draggingOffsetY = 0f
                            orderChangedDuringDrag = false
                        }
                    )
                }
                GroupAppItem(
                    appInfo = appInfo,
                    timeLimit = group.timeHrLimit * 60 + group.timeMinLimit,
                    limitEach = group.limitEach,
                    modifier = dragModifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) draggingOffsetY else 0f },
                    sharedTimeRemainingMinutes = if (group.limitEach) {
                        null
                    } else {
                        (group.timeRemaining / 60_000L).toInt().coerceAtLeast(0)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showCompactHeader,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name ?: "App Limit Group",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Left: ${formatTimeRemaining(group.timeRemaining)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Used: ${formatTimeRemaining(groupUsedMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(nextResetMillis))
}

private fun formatTime(context: Context, hour: Int, minute: Int): String {
    return formatClockTime(context, hour, minute)
}

private fun formatDays(days: List<Int>?): String {
    if (days == null || days.isEmpty() || days.size == 7) return "Everyday"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().joinToString(", ") { dayIndex ->
        if (dayIndex in 1..7) names[dayIndex - 1] else "?"
    }
}
