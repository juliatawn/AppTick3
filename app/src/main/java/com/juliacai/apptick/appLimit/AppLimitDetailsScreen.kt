package com.juliacai.apptick.appLimit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.MainViewModel
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.GroupAppItem
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Limit Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            FloatingActionButton(onClick = { group?.let { onEditClick(it) } }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Group")
            }
        }
    ) { paddingValues ->
        if (group == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Group not found.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = group!!.name ?: "Unnamed Group",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
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
            Text("Time Remaining", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatTimeRemaining(group.timeRemaining),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Limit Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = formatTimeLimitInfo(group), style = MaterialTheme.typography.bodyMedium)
            Text(text = formatActiveDaysInfo(group), style = MaterialTheme.typography.bodyMedium)
            Text(text = formatTimeRangeInfo(group), style = MaterialTheme.typography.bodyMedium)
            Text(text = formatCumulativeInfo(group), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppUsageCard(group: AppLimitGroup) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("App Usage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(300.dp)) { // Set a fixed height for the inner list
                items(group.apps) { app ->
                    val appInfo = AppInfo(
                        appName = app.appName,
                        appPackage = app.appPackage
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
    val hours = milliseconds / 3_600_000
    val minutes = (milliseconds % 3_600_000) / 60_000
    return "$hours hr $minutes min"
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTimeLimitInfo(group: AppLimitGroup): String {
    val type = if (group.limitEach) "each" else "total"
    return "Time limit: ${group.timeHrLimit} hr ${group.timeMinLimit} min $type"
}

private fun formatActiveDaysInfo(group: AppLimitGroup): String {
    return "Active days: ${formatDays(group.weekDays)}"
}

private fun formatTimeRangeInfo(group: AppLimitGroup): String {
    return if (!group.useTimeRange) {
        "No time range restriction"
    } else {
        "Allowed time: %02d:%02d - %02d:%02d".format(
            group.startHour, group.startMinute,
            group.endHour, group.endMinute
        )
    }
}

private fun formatCumulativeInfo(group: AppLimitGroup): String {
    return if (!group.cumulativeTime) {
        "Time resets daily"
    } else {
        "Cumulative time enabled, resets every ${group.resetHours} hours"
    }
}

private fun formatDays(days: List<Int>): String {
    val dayNames = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    return days.mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(", ")
}
