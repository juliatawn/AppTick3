package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.widthIn
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.BaseActivity
import com.juliacai.apptick.LockDecision
import com.juliacai.apptick.LockMode
import com.juliacai.apptick.LockPolicy
import com.juliacai.apptick.LockState
import com.juliacai.apptick.LockdownType
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.formatTimeRanges
import com.juliacai.apptick.lazyColumnScrollIndicator
import com.juliacai.apptick.backgroundProcesses.BackgroundChecker
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.AppUsageStat
import com.juliacai.apptick.groups.GroupAppItem
import com.juliacai.apptick.rememberScrollbarColor
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
            val isPremium = prefs.getBoolean("premium", false)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            var showDuplicatePremiumDialog by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }

            var hasReorderedApps by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean(PREF_APPS_REORDERED, false))
            }

            val palette = AppTheme.currentPalette(this, isSystemDark)
            val colorScheme = AppTheme.colorSchemeFromPalette(palette)

            MaterialTheme(colorScheme = colorScheme) {
                val canEditGroup = !isLimitEditingLocked()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "AppTick",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
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
                        if (group != null && canEditGroup) {
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
                                showReorderHint = !hasReorderedApps,
                                onDismissReorderHint = {
                                    if (!hasReorderedApps) {
                                        hasReorderedApps = true
                                        prefs.edit { putBoolean(PREF_APPS_REORDERED, true) }
                                    }
                                },
                                onAppsReordered = { reorderedApps ->
                                    val current = group ?: return@GroupDetails
                                    if (current.apps == reorderedApps) return@GroupDetails
                                    if (!hasReorderedApps) {
                                        hasReorderedApps = true
                                        prefs.edit { putBoolean(PREF_APPS_REORDERED, true) }
                                    }
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
                        onDuplicate = {
                            showActionsDialog = false
                            if (!isPremium) {
                                showDuplicatePremiumDialog = true
                                return@GroupActionsDialog
                            }
                            val duplicateIntent = Intent(this@GroupPage, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_DUPLICATE_GROUP_ID, currentGroup.id)
                            }
                            startActivity(duplicateIntent)
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

                if (showDuplicatePremiumDialog) {
                    AlertDialog(
                        onDismissRequest = { showDuplicatePremiumDialog = false },
                        title = { Text("Premium Feature") },
                        text = { Text("Duplicate is available in Premium Mode.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDuplicatePremiumDialog = false
                                    startActivity(Intent(this@GroupPage, MainActivity::class.java).apply {
                                        putExtra(MainActivity.EXTRA_OPEN_LOCK_MODES, true)
                                    })
                                }
                            ) {
                                Text("Buy Premium")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showDuplicatePremiumDialog = false }) {
                                Text("Cancel")
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

    private fun isLimitEditingLocked(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = getSharedPreferences("groupPrefs", MODE_PRIVATE)
        val decision = LockPolicy.evaluateEditingLock(readLockState(prefs), nowMillis)
        if (decision.shouldClearExpiredLockdown) {
            prefs.edit {
                putString("active_lock_mode", "NONE")
                remove("lockdown_end_time")
                remove("lockdown_weekly_used_key")
                putBoolean("lockdown_prompt_after_unlock", true)
            }
        }
        return decision.isLocked
    }

    private fun readLockState(prefs: android.content.SharedPreferences): LockState {
        val activeModeStr = prefs.getString("active_lock_mode", "NONE") ?: "NONE"
        val activeMode = try {
            LockMode.valueOf(activeModeStr)
        } catch (_: Exception) {
            LockMode.NONE
        }

        val typeStr = prefs.getString("lockdown_type", "ONE_TIME") ?: "ONE_TIME"
        val lockdownType = try {
            LockdownType.valueOf(typeStr)
        } catch (_: Exception) {
            LockdownType.ONE_TIME
        }

        val recurringDays = prefs.getString("lockdown_recurring_days", "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()

        return LockState(
            activeLockMode = activeMode,
            passwordUnlocked = prefs.getBoolean("passUnlocked", false),
            securityKeyUnlocked = prefs.getBoolean("securityKeyUnlocked", false),
            lockdownType = lockdownType,
            lockdownEndTimeMillis = prefs.getLong("lockdown_end_time", 0L),
            lockdownRecurringDays = recurringDays,
            lockdownRecurringUsedKey = prefs.getString("lockdown_weekly_used_key", null)
        )
    }

    companion object {
        private const val EXTRA_GROUP_ID = "extra_group_id"
        private const val PREF_APPS_REORDERED = "appsReorderedHintDismissed"

        fun newIntent(context: Context, group: AppLimitGroup): Intent {
            return Intent(context, GroupPage::class.java).apply {
                putExtra(EXTRA_GROUP_ID, group.id)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionsDialog(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit = {},
    onDelete: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Group Options", style = MaterialTheme.typography.headlineSmall)
                Text("Choose what you want to do with this app limit group.")
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onDuplicate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Duplicate")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun GroupDetails(
    group: AppLimitGroup,
    showReorderHint: Boolean,
    onDismissReorderHint: () -> Unit = {},
    onAppsReordered: (List<com.juliacai.apptick.appLimit.AppInGroup>) -> Unit = {}
) {
    val context = LocalContext.current
    val groupPackages = remember(group.apps) { group.apps.map { it.appPackage }.toSet() }
    val usageByPackage = remember(group.perAppUsage, groupPackages) {
        group.perAppUsage
            .asSequence()
            .filter { it.appPackage in groupPackages }
            .associate { it.appPackage to it.usedMillis.coerceAtLeast(0L) }
    }
    val groupUsedMillis = usageByPackage.values.sum()
    val groupTimeLeftMillis = remember(group) { totalGroupTimeLeftMillis(group) }
    val listState = rememberLazyListState()
    val orderedApps = remember { mutableStateListOf<com.juliacai.apptick.appLimit.AppInGroup>() }
    var draggingAppPackage by remember { mutableStateOf<String?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var orderChangedDuringDrag by remember { mutableStateOf(false) }
    var autoScrollSpeedPxPerFrame by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 96.dp.toPx() }
    val maxAutoScrollPxPerFrame = with(density) { 22.dp.toPx() }
    val scrollbarColor = rememberScrollbarColor()
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
        autoScrollSpeedPxPerFrame = 0f
    }
    LaunchedEffect(draggingAppPackage, autoScrollSpeedPxPerFrame) {
        if (draggingAppPackage == null || autoScrollSpeedPxPerFrame == 0f) return@LaunchedEffect
        while (draggingAppPackage != null && autoScrollSpeedPxPerFrame != 0f) {
            val consumed = listState.scrollBy(autoScrollSpeedPxPerFrame)
            if (consumed == 0f) break
            draggingOffsetY += consumed
            withFrameNanos { }
        }
    }

    Box(modifier = Modifier.fillMaxSize() ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.lazyColumnScrollIndicator(listState, scrollbarColor),
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
                            text = formatTimeRemaining(groupTimeLeftMillis),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Time Left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LabelValueText(
                            label = "Time Used:",
                            value = formatTimeRemaining(groupUsedMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LabelValueText(
                            label = "Next Reset:",
                            value = formatNextReset(group.nextResetTime),
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (group.cumulativeTime && !group.limitEach) {
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

                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        val totalMinutes = group.timeHrLimit * 60 + group.timeMinLimit
                                        Text(if (totalMinutes == 0) "Blocks" else "${group.timeHrLimit}h ${group.timeMinLimit}m Limit")
                                    },
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
                        }

                        if (group.useTimeRange) {
                            LabelValueText(
                                label = "Active Hours:",
                                value = formatTimeRanges(context, group),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LabelValueText(
                                label = "Outside Range:",
                                value = if (group.blockOutsideTimeRange) {
                                    "Block Apps"
                                } else {
                                    "Allow No Limits"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        LabelValueText(
                            label = "Active Days:",
                            value = formatDays(group.weekDays),
                            style = MaterialTheme.typography.bodySmall
                        )

                        val resetLabelAndValue = if (group.resetMinutes > 0) {
                            val interval = "${group.resetMinutes / 60}h ${group.resetMinutes % 60}m"
                            if (group.cumulativeTime) {
                                "Cumulative:" to "Daily + every $interval"
                            } else {
                                "Resets:" to "Daily every $interval"
                            }
                        } else {
                            "Resets:" to "Daily"
                        }
                        LabelValueText(
                            label = resetLabelAndValue.first,
                            value = resetLabelAndValue.second,
                            style = MaterialTheme.typography.bodySmall
                        )


                    }
                }
            }

            if (showReorderHint) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = "Long-press and drag app cards to reorder.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                TextButton(
                                    onClick = onDismissReorderHint,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                ) {
                                    Text("DISMISS")
                                }
                            }

                        }
                    }
                }
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
                            autoScrollSpeedPxPerFrame = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingAppPackage != app.appPackage) return@detectDragGesturesAfterLongPress
                            change.consume()
                            draggingOffsetY += dragAmount.y

                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                            val draggedInfo = visibleItems.firstOrNull { it.key == app.appPackage }
                                ?: return@detectDragGesturesAfterLongPress
                            val draggedCenterY = draggedInfo.offset + (draggedInfo.size / 2f) + draggingOffsetY
                            val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                            val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                            val topEdge = viewportStart + edgeThresholdPx
                            val bottomEdge = viewportEnd - edgeThresholdPx
                            autoScrollSpeedPxPerFrame = when {
                                draggedCenterY < topEdge -> {
                                    val intensity = ((topEdge - draggedCenterY) / edgeThresholdPx).coerceIn(0f, 1f)
                                    -maxAutoScrollPxPerFrame * intensity
                                }
                                draggedCenterY > bottomEdge -> {
                                    val intensity = ((draggedCenterY - bottomEdge) / edgeThresholdPx).coerceIn(0f, 1f)
                                    maxAutoScrollPxPerFrame * intensity
                                }
                                else -> 0f
                            }
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
                            autoScrollSpeedPxPerFrame = 0f
                        },
                        onDragCancel = {
                            if (orderChangedDuringDrag) {
                                onAppsReordered(orderedApps.toList())
                            }
                            draggingAppPackage = null
                            draggingOffsetY = 0f
                            orderChangedDuringDrag = false
                            autoScrollSpeedPxPerFrame = 0f
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
                        LabelValueText(
                            label = "Left:",
                            value = formatTimeRemaining(groupTimeLeftMillis),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LabelValueText(
                            label = "Used:",
                            value = formatTimeRemaining(groupUsedMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun totalGroupTimeLeftMillis(group: AppLimitGroup): Long {
    if (!group.limitEach) return group.timeRemaining.coerceAtLeast(0L)

    val limitPerAppMillis = ((group.timeHrLimit * 60L) + group.timeMinLimit.toLong())
        .coerceAtLeast(0L) * 60_000L
    if (limitPerAppMillis <= 0L) return 0L

    val usageByPackage = group.perAppUsage.associate { it.appPackage to it.usedMillis.coerceAtLeast(0L) }
    return group.apps.sumOf { app ->
        (limitPerAppMillis - (usageByPackage[app.appPackage] ?: 0L)).coerceAtLeast(0L)
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

@Composable
private fun LabelValueText(
    label: String,
    value: String,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append("$label ")
            }
            append(value)
        },
        style = style,
        color = color,
        modifier = modifier
    )
}

private fun formatDays(days: List<Int>?): String {
    if (days == null || days.isEmpty() || days.size == 7) return "Everyday"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().joinToString(", ") { dayIndex ->
        if (dayIndex in 1..7) names[dayIndex - 1] else "?"
    }
}

private fun previewGroup(): AppLimitGroup {
    return AppLimitGroup(
        id = 1L,
        name = "Social Media",
        timeHrLimit = 1,
        timeMinLimit = 30,
        timeRemaining = 5_400_000L,
        limitEach = false,
        cumulativeTime = true,
        useTimeRange = true,
        startHour = 9,
        startMinute = 0,
        endHour = 17,
        endMinute = 0,
        blockOutsideTimeRange = true,
        weekDays = listOf(1, 2, 3, 4, 5),
        nextResetTime = 1_768_507_200_000L, // Jan 1, 2026 12:00 PM UTC
        apps = listOf(
            com.juliacai.apptick.appLimit.AppInGroup(
                appName = "Instagram",
                appPackage = "com.instagram.android",
                appIcon = null
            ),
            com.juliacai.apptick.appLimit.AppInGroup(
                appName = "YouTube",
                appPackage = "com.google.android.youtube",
                appIcon = null
            ),
            com.juliacai.apptick.appLimit.AppInGroup(
                appName = "TikTok",
                appPackage = "com.zhiliaoapp.musically",
                appIcon = null
            )
        ),
        perAppUsage = listOf(
            AppUsageStat("com.instagram.android", 1_500_000L),
            AppUsageStat("com.google.android.youtube", 1_050_000L),
            AppUsageStat("com.zhiliaoapp.musically", 720_000L)
        )
    )
}

@Preview(name = "Group Details Light", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
fun GroupDetailsLightPreview() {
    MaterialTheme {
        GroupDetails(
            group = previewGroup(),
            showReorderHint = true
        )
    }
}

@Preview(
    name = "Group Details Dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GroupDetailsDarkPreview() {
    MaterialTheme {
        GroupDetails(
            group = previewGroup(),
            showReorderHint = true
        )
    }
}

@Preview(name = "Group Actions Dialog")
@Composable
fun GroupActionsDialogPreview() {
    MaterialTheme {
        GroupActionsDialog(
            onDismiss = {},
            onEdit = {},
            onDuplicate = {},
            onDelete = {}
        )
    }
}
