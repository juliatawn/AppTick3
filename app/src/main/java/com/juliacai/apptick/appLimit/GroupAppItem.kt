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
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.juliacai.apptick.AppInfo

@Composable
fun GroupAppItem(appInfo: AppInfo, timeLimit: Int, limitEach: Boolean) {
    val timeUsed = appInfo.timeUsed // in minutes
    val timeRemaining = timeLimit - timeUsed
    val progress = (timeUsed.toFloat() / timeLimit).coerceAtMost(1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = rememberAsyncImagePainter(model = appInfo.appIcon),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = appInfo.appName, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text("$timeUsed min used", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("$timeRemaining min remaining", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
