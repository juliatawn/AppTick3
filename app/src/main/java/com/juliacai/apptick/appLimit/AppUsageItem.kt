package com.juliacai.apptick.appLimit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme
import coil.compose.rememberAsyncImagePainter
import com.juliacai.apptick.AppInfo

@Composable
fun AppUsageItem(appInfo: AppInfo, timeLimit: Int) {
    val usageMinutes = appInfo.appTimeUse / 60000
    val safeTimeLimit = timeLimit.coerceAtLeast(0)
    val progress = if (safeTimeLimit > 0) {
        (usageMinutes.toFloat() / safeTimeLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = appInfo.appIcon),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appInfo.appName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Time Used: $usageMinutes min", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Time Limit: $timeLimit min", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppUsageItemPreview() {
    AppTheme {
        AppUsageItem(
            appInfo = AppInfo(
                appName = "YouTube",
                appPackage = "com.google.android.youtube",
                appTimeUse = 42 * 60_000L
            ),
            timeLimit = 90
        )
    }
}
