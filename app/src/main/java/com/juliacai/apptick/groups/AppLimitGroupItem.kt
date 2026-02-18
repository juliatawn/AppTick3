package com.juliacai.apptick.groups

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.juliacai.apptick.R
import com.juliacai.apptick.formatClockTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitGroupItem(
    group: AppLimitGroup,
    isEditingLocked: Boolean,
    onLockClick: (AppLimitGroup) -> Unit,
    onPauseToggle: (AppLimitGroup) -> Unit,
    onEdit: (AppLimitGroup) -> Unit,
    onDelete: (AppLimitGroup) -> Unit,
    onCardClick: ((AppLimitGroup) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = { onCardClick?.invoke(group) ?: onEdit(group) }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name.toString(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (isEditingLocked) {
                    IconButton(onClick = { onLockClick(group) }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock to pause"
                        )
                    }
                    IconButton(onClick = { onLockClick(group) }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock to edit"
                        )
                    }
                } else {
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            val context = LocalContext.current
            LazyRow {
                items(group.apps) { app ->
                    val appInfo = remember(app.appPackage) {
                        try {
                            app.appPackage?.let { pkg ->
                                val drawable = context.packageManager.getApplicationIcon(pkg)
                                val bitmap = createBitmap(
                                    drawable.intrinsicWidth.coerceAtLeast(1),
                                    drawable.intrinsicHeight.coerceAtLeast(1)
                                )
                                val canvas = android.graphics.Canvas(bitmap)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                bitmap.asImageBitmap()
                            }
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    if (appInfo != null) {
                        androidx.compose.foundation.Image(
                            bitmap = appInfo,
                            contentDescription = app.appName,
                            modifier = Modifier.size(48.dp).padding(end = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = formatTimeLimitInfo(group))
            Text(text = formatActiveDaysInfo(group))
            if (group.useTimeRange) {
                Text(text = formatTimeRangeInfo(group, context))
                Text(
                    text = if (group.blockOutsideTimeRange) {
                        "Outside range: Block apps"
                    } else {
                        "Outside range: Allow no limits"
                    }
                )
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

private fun formatTimeRangeInfo(group: AppLimitGroup, context: android.content.Context): String {
    return "Time range: ${formatClockTime(context, group.startHour, group.startMinute)} - ${
        formatClockTime(context, group.endHour, group.endMinute)
    }"
}

private fun formatResetInfo(group: AppLimitGroup): String {
    if (group.resetMinutes <= 0) return "Resets: Daily"
    val hours = group.resetMinutes / 60
    val minutes = group.resetMinutes % 60
    val interval = "${hours}h ${minutes}m"
    return if (group.cumulativeTime) {
        "Cumulative: Daily + every $interval"
    } else {
        "Resets: Daily every $interval"
    }
}

private fun formatDays(days: List<Int>): String {
    if (days.isEmpty() || days.size == 7) return "Everyday"
    val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(", ")
}
