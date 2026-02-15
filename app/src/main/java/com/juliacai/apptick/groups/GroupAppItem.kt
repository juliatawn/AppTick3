package com.juliacai.apptick.groups

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppInfo

@Composable
fun GroupAppItem(appInfo: AppInfo, timeLimit: Int, limitEach: Boolean) {
    val timeUsed = appInfo.timeUsed // in minutes
    val timeRemaining = timeLimit - timeUsed
    val progress = (timeUsed.toFloat() / timeLimit).coerceAtMost(1f)

    val context = LocalContext.current
    val iconBitmap = remember(appInfo.appPackage) {
        try {
            appInfo.appPackage?.let { pkg ->
                val drawable = context.packageManager.getApplicationIcon(pkg)
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = "${appInfo.appName} icon",
                        modifier = Modifier.size(48.dp)
                    )
                }
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
