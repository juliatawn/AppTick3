package com.juliacai.apptick.groups

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.juliacai.apptick.R
import com.juliacai.apptick.formatTimeRanges
import com.juliacai.apptick.formatClockTime
import com.juliacai.apptick.getConfiguredTimeRanges
import com.juliacai.apptick.isNowWithinAnyTimeRange
import androidx.compose.ui.tooling.preview.Preview
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.appLimit.AppInGroup
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitGroupItem(
    group: AppLimitGroup,
    isExpanded: Boolean,
    isEditingLocked: Boolean,
    onExpandToggle: (AppLimitGroup) -> Unit,
    onLockClick: (AppLimitGroup) -> Unit,
    onPauseToggle: (AppLimitGroup) -> Unit,
    onEdit: (AppLimitGroup) -> Unit,
    onDelete: (AppLimitGroup) -> Unit,
    onCardClick: ((AppLimitGroup) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lineSpacing = 2.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = { onCardClick?.invoke(group) ?: onEdit(group) }
    ) {
        Column(
            modifier = Modifier.padding(
                start = 21.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(3f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onPauseToggle(group) }) {
                            Icon(
                                painter = painterResource(id = if (group.paused) R.drawable.ic_play else R.drawable.ic_pause),
                                contentDescription = "Toggle Pause"
                            )
                        }
                        if (group.paused) {
                            Text(
                                text = "PAUSED",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    IconButton(onClick = { onEdit(group) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
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
                        Image(
                            bitmap = appInfo,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isGroupCurrentlyLimited(group)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append("Time left: ")
                        }
                        append(formatTimeLeft(group))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append("Time limit: ")
                        }
                        append(formatConfiguredTimeLimit(group))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                    modifier = Modifier
                        .size(20.dp)
                        .offset(x = (-12).dp)
                        .clickable { onExpandToggle(group) }
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append("Active days: ")
                        }
                        append(formatActiveDaysInfo(group))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (group.useTimeRange) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append("Time range: ")
                            }
                            append(formatTimeRangeInfo(group, context))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append("Outside range: ")
                            }
                            append(if (group.blockOutsideTimeRange) "Block apps" else "Allow no limits")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatResetInfo(group),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun formatConfiguredTimeLimit(group: AppLimitGroup): String {
    val totalMinutes = (group.timeHrLimit * 60 + group.timeMinLimit).coerceAtLeast(0)
    if (totalMinutes == 0) {
        return "No limit"
    }

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add("$hours hr")
        if (minutes > 0) add("$minutes min")
    }
    return parts.joinToString(" ")
}

private fun isGroupCurrentlyLimited(
    group: AppLimitGroup,
    now: Calendar = Calendar.getInstance()
): Boolean {
    if (group.paused) return false
    val hasPositiveLimit = (group.timeHrLimit * 60 + group.timeMinLimit) > 0
    if (!hasPositiveLimit) return false

    val activeDays = group.weekDays.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }
    val dayOfWeek = when (now.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
    if (dayOfWeek !in activeDays) return false

    if (!group.useTimeRange) return true
    return isNowWithinAnyTimeRange(group.getConfiguredTimeRanges(), now.timeInMillis)
}

private fun formatTimeLeft(group: AppLimitGroup): String {
    val totalMinutes = (totalGroupTimeLeftMillis(group) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add("$hours hr")
        if (minutes > 0 || hours == 0) add("$minutes min")
    }
    return parts.joinToString(" ")
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

private fun formatActiveDaysInfo(group: AppLimitGroup): String {
    return "${formatDays(group.weekDays)}"
}

private fun formatTimeRangeInfo(group: AppLimitGroup, context: android.content.Context): String {
    val ranges = group.getConfiguredTimeRanges()
    if (ranges.isEmpty()) {
        return "${formatClockTime(context, group.startHour, group.startMinute)} - ${
            formatClockTime(context, group.endHour, group.endMinute)
        }"
    }
    return formatTimeRanges(context, group)
}

private fun formatResetInfo(group: AppLimitGroup): AnnotatedString {
    if (group.resetMinutes <= 0) {
        return buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("Resets: ")
            }
            append("Daily")
        }
    }
    val hours = group.resetMinutes / 60
    val minutes = group.resetMinutes % 60
    val interval = "${hours}h ${minutes}m"
    return buildAnnotatedString {
        withStyle(
            style = SpanStyle(fontWeight = FontWeight.SemiBold),
        ) {
            append(if (group.cumulativeTime) "Cumulative: " else "Resets: ")
        }
        append(
            if (group.cumulativeTime) {
                "Daily + every $interval"
            } else {
                "Daily every $interval"
            }
        )
    }
}

private fun formatDays(days: List<Int>): String {
    if (days.isEmpty() || days.size == 7) return "Everyday"
    val dayNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return days.sorted().mapNotNull { dayNames.getOrNull(it - 1) }.joinToString(", ")
}

@Preview(showBackground = true)
@Composable
fun AppLimitGroupItemPreview() {
    AppTheme {
        AppLimitGroupItem(
            group = AppLimitGroup(
                name = "Social Media",
                timeHrLimit = 1,
                timeMinLimit = 30,
                limitEach = false,
                weekDays = listOf(1, 2, 3, 4, 5),
                apps = listOf(
                    AppInGroup(appName = "App 1", appPackage = "com.example.app1", appIcon = null),
                    AppInGroup(appName = "App 2", appPackage = "com.example.app2", appIcon = null)
                ),
                paused = false,
                useTimeRange = true,
                blockOutsideTimeRange = true,
                startHour = 9,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
                resetMinutes = 60,
                cumulativeTime = false
            ),
            isExpanded = true,
            isEditingLocked = false,
            onExpandToggle = {},
            onLockClick = {},
            onPauseToggle = {},
            onEdit = {},
            onDelete = {},
            onCardClick = {}
        )
    }
}
