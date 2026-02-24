package com.juliacai.apptick.appLimit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
fun AppUsageRow(appInfo: AppInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = appInfo.appIcon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = appInfo.appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = formatTimeUsed(appInfo.appTimeUse),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatTimeUsed(milliseconds: Long): String {
    val hours = milliseconds / 3_600_000
    val minutes = (milliseconds % 3_600_000) / 60_000
    return "$hours hr $minutes min"
}

@Preview(showBackground = true)
@Composable
private fun AppUsageRowPreview() {
    AppTheme {
        AppUsageRow(
            appInfo = AppInfo(
                appName = "Instagram",
                appPackage = "com.instagram.android",
                appTimeUse = 75 * 60_000L
            )
        )
    }
}
