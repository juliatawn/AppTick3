package com.juliacai.apptick.appLimit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.MainViewModel
import com.juliacai.apptick.formatClockTime
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.GroupAppItem
import com.juliacai.apptick.lazyColumnScrollIndicator
import com.juliacai.apptick.rememberScrollbarColor
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitDetailsScreen(
    groupId: Long,
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onEditClick: (AppLimitGroup) -> Unit,
) {
    val group by viewModel.getGroup(groupId).observeAsState()
    val detailsListState = rememberLazyListState()
    val scrollbarColor = rememberScrollbarColor()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Limit Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { group?.let { onEditClick(it) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Group")
            }
        }
    ) { paddingValues ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Group not found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = detailsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .lazyColumnScrollIndicator(detailsListState, scrollbarColor),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = group!!.name ?: "Unnamed Group",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    TimeRemainingCard(group = group!!)
                }

                item {
                    SettingsSummaryCard(group = group!!)
                }

                item {
                    AppUsageCard(group = group!!)
                }
            }
        }
    }
}

@Composable
private fun TimeRemainingCard(group: AppLimitGroup) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Time Remaining", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            
            val totalLimitMinutes = group.timeHrLimit * 60 + group.timeMinLimit
            val remainingMinutes = group.timeRemaining / 60000
            val usageRatio = if (totalLimitMinutes > 0) {
                1f - (remainingMinutes.toFloat() / totalLimitMinutes.toFloat())
            } else 0f
            
            LinearProgressIndicator(
                progress = { usageRatio.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (remainingMinutes <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTimeRemaining(group.timeRemaining),
                style = MaterialTheme.typography.displaySmall,
                color = if (group.timeRemaining <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            if (group.cumulativeTime) {
                val limitInMillis = (group.timeHrLimit * 60L + group.timeMinLimit.toLong()) * 60_000L
                val carriedOver = (group.timeRemaining - limitInMillis).coerceAtLeast(0L)
                if (carriedOver > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Carried over: ${formatTimeRemaining(carriedOver)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Next reset: ${formatDateTime(group.nextResetTime)}", style = MaterialTheme.typography.bodyMedium)
            if (group.cumulativeTime) {
                Spacer(Modifier.height(4.dp))
                Text("Next time addition: ${formatDateTime(group.nextAddTime)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsSummaryCard(group: AppLimitGroup) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            SettingRow(label = "Daily Limit", value = "${group.timeHrLimit} hr ${group.timeMinLimit} min " + if (group.limitEach) "(Per App)" else "(Total)")
            SettingRow(label = "Active Days", value = formatDays(group.weekDays))
            SettingRow(label = "Time Range", value = formatTimeRangeInfo(group, context))
            SettingRow(label = "Type", value = formatResetType(group))
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppUsageCard(group: AppLimitGroup) {
    val usageByPackage = group.perAppUsage.associate { it.appPackage to it.usedMillis }
    val usageListState = rememberLazyListState()
    val scrollbarColor = rememberScrollbarColor()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("App Usage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                state = usageListState,
                modifier = Modifier
                    .height(300.dp)
                    .lazyColumnScrollIndicator(usageListState, scrollbarColor)
            ) {
                items(group.apps) { app ->
                    val appInfo = AppInfo(
                        appName = app.appName,
                        appPackage = app.appPackage,
                        appTimeUse = usageByPackage[app.appPackage] ?: 0L
                    )
                    GroupAppItem(
                        appInfo = appInfo,
                        timeLimit = group.timeMinLimit + group.timeHrLimit * 60,
                        limitEach = group.limitEach
                    )
                }
            }
        }
    }
}

private fun formatTimeRemaining(milliseconds: Long): String {
    if (milliseconds <= 0) return "Limit Reached"
    val hours = milliseconds / 3_600_000
    val minutes = (milliseconds % 3_600_000) / 60_000
    return if (hours > 0) "$hours h $minutes m" else "$minutes min"
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTimeRangeInfo(group: AppLimitGroup, context: Context): String {
    return if (!group.useTimeRange) {
        "All Day"
    } else {
        val mode = if (group.blockOutsideTimeRange) "Block outside range" else "Allow outside range"
        "${formatClockTime(context, group.startHour, group.startMinute)} - ${
            formatClockTime(
                context,
                group.endHour,
                group.endMinute
            )
        } ($mode)"
    }
}

private fun formatResetType(group: AppLimitGroup): String {
    if (group.resetMinutes <= 0) return "Standard (Resets Daily)"
    val interval = "${group.resetMinutes / 60}h ${group.resetMinutes % 60}m"
    return if (group.cumulativeTime) {
        "Cumulative (Daily + every $interval)"
    } else {
        "Standard (Daily every $interval)"
    }
}

private fun formatDays(days: List<Int>): String {
    if (days.isEmpty() || days.size == 7) return "Everyday"
    val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(", ")
}
