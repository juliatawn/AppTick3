package com.juliacai.apptick.block

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTickLogo
import com.juliacai.apptick.verticalScrollWithIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.tooling.preview.Preview
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.ui.theme.getSampleAppIcon

@Composable
fun BlockWindowScreen(
    appName: String,
    appIcon: Painter?,
    groupName: String,
    blockReason: String?,
    appTimeSpent: Long,
    groupTimeSpent: Long,
    timeLimitMinutes: Int,
    limitEach: Boolean,
    useTimeRange: Boolean,
    blockOutsideTimeRange: Boolean,
    blockedForOutsideRange: Boolean,
    nextResetTime: Long,
    isPremium: Boolean,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    val limitMillis = TimeUnit.MINUTES.toMillis(timeLimitMinutes.toLong())
    val relevantTimeSpent = if (limitEach) appTimeSpent else groupTimeSpent
    val usageRatio = if (limitMillis > 0) {
        (relevantTimeSpent.toFloat() / limitMillis.toFloat()).coerceIn(0f, 1f)
    } else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScrollWithIndicator()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // ── App Icon ──────────────────────────────────────────────────────
        if (appIcon != null) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = appIcon,
                    contentDescription = "$appName icon",
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            text = "Blocked from $appName",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = groupName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Available At",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${formatNextResetTime(nextResetTime)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Spacer(Modifier.height(12.dp))

                    if (!blockReason.isNullOrBlank()) {
                        Text(
                            text = "Block Reason",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = blockReason,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Time Details Card ─────────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow(label = "App Time Used", value = formatTime(appTimeSpent), primaryColor = primaryColor)
                Spacer(Modifier.height(12.dp))
                InfoRow(label = "Group Time Used", value = formatTime(groupTimeSpent), primaryColor = primaryColor)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── AppTick branding ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        ) {
            AppTickLogo(
                containerSize = 24.dp,
                iconSize = 14.dp,
                backgroundColorOverride = Color(0xFF6F34AD)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AppTick",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, primaryColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatNextResetTime(nextResetMillis: Long): String {
    if (nextResetMillis <= 0L) return "Not scheduled"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(nextResetMillis))
}

private fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Preview
@Composable
private fun BlockWindowScreenPreview() {
    AppTheme {
        BlockWindowScreen(
            appName = "YouTube",
            appIcon = getSampleAppIcon(),
            groupName = "Entertainment",
            blockReason = "This is a test block reason.",
            appTimeSpent = TimeUnit.MINUTES.toMillis(45),
            groupTimeSpent = TimeUnit.HOURS.toMillis(2),
            timeLimitMinutes = 60,
            limitEach = false,
            useTimeRange = true,
            blockOutsideTimeRange = true,
            blockedForOutsideRange = false,
            nextResetTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(12),
            isPremium = true
        )
    }
}
