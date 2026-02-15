package com.juliacai.apptick.block

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.juliacai.apptick.R
import java.util.concurrent.TimeUnit

@Composable
fun BlockWindowScreen(
    appName: String,
    appIcon: Painter?,
    groupName: String,
    appTimeSpent: Long,
    groupTimeSpent: Long,

    isPremium: Boolean,
    primaryColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    backgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.background
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (appIcon != null) {
            Image(
                painter = appIcon,
                contentDescription = "$appName icon",
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            text = stringResource(R.string.time_limit_reached, appName),
            modifier = Modifier.padding(16.dp),
            color = primaryColor,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.group_name, groupName),
            modifier = Modifier.padding(8.dp),
            color = primaryColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        val appHours = TimeUnit.MILLISECONDS.toHours(appTimeSpent)
        val appMinutes = TimeUnit.MILLISECONDS.toMinutes(appTimeSpent) % 60
        Text(
            text = stringResource(R.string.app_time_spent, appHours, appMinutes),
            modifier = Modifier.padding(8.dp),
            color = primaryColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        val groupHours = TimeUnit.MILLISECONDS.toHours(groupTimeSpent)
        val groupMinutes = TimeUnit.MILLISECONDS.toMinutes(groupTimeSpent) % 60
        Text(
            text = stringResource(R.string.group_time_spent, groupHours, groupMinutes),
            modifier = Modifier.padding(8.dp),
            color = primaryColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "AppTick Logo",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AppTick",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
