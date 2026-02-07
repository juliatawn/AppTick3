package com.juliacai.apptick.appLimit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.juliacai.apptick.R
import com.juliacai.apptick.appLimit.AppLimitGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitGroupItem(
    group: AppLimitGroup,
    onPauseToggle: (AppLimitGroup) -> Unit,
    onEdit: (AppLimitGroup) -> Unit,
    onDelete: (AppLimitGroup) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = group.name.toString(), modifier = Modifier.weight(1f))
                IconButton(onClick = { onPauseToggle(group) }) {
                    Icon(
                        painter = painterResource(id = if (group.paused) R.drawable.ic_play else R.drawable.ic_pause),
                        contentDescription = "Toggle Pause"
                    )
                }
                IconButton(onClick = { onEdit(group) }) {
                    Icon(painter = painterResource(id = R.drawable.ic_edit), contentDescription = "Edit")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow {
                items(group.apps.take(5)) { app ->
                    AsyncImage(
                        model = app.appIcon,
                        contentDescription = app.appName,
                        modifier = Modifier.size(48.dp).padding(end = 8.dp)
                    )
                }
                if (group.apps.size > 5) {
                    item {
                        Text(text = "...")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = formatTimeLimitInfo(group))
            Text(text = formatActiveDaysInfo(group))
            if (group.useTimeRange) {
                Text(text = formatTimeRangeInfo(group))
            }
            Text(text = formatResetInfo(group))
        }
    }
}

private fun formatTimeLimitInfo(group: AppLimitGroup): String {
    val type = if (group.limitEach) "each" else "total"
    return "${group.timeHrLimit} hr ${group.timeMinLimit} min $type"
}

private fun formatActiveDaysInfo(group: AppLimitGroup): String {
    return "Active: ${formatDays(group.weekDays)}"
}

private fun formatTimeRangeInfo(group: AppLimitGroup): String {
    return "Time range: %02d:%02d - %02d:%02d".format(
        group.startHour, group.startMinute,
        group.endHour, group.endMinute
    )
}

private fun formatResetInfo(group: AppLimitGroup): String {
    if (!group.cumulativeTime) return "Resets daily"
    return "Resets every ${group.resetHours} hours"
}

private fun formatDays(days: List<Int>): String {
    val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(", ")
}
