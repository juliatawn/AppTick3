package com.juliacai.apptick.deviceApps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.groups.AppLimitGroup
import com.juliacai.apptick.groups.GroupAppItem

class GroupPage : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val group = intent.getSerializableExtra(EXTRA_GROUP) as? AppLimitGroup

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(group?.name ?: "Group Details") },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        group?.let {
                            GroupDetails(it)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_GROUP = "extra_group"

        fun newIntent(context: Context, group: AppLimitGroup): Intent {
            return Intent(context, GroupPage::class.java).apply {
                putExtra(EXTRA_GROUP, group)
            }
        }
    }
}

@Composable
fun GroupDetails(group: AppLimitGroup) {
    Column {
        Row {
            Text("Group Name: ", style = MaterialTheme.typography.bodyMedium)
            Text(group.name ?: "", style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            Text("Time Limit: ", style = MaterialTheme.typography.bodyMedium)
            Text("${group.timeHrLimit}h ${group.timeMinLimit}m", style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            Text("Limit Type: ", style = MaterialTheme.typography.bodyMedium)
            Text(if (group.limitEach) "Limit for EACH" else "Limit for ALL", style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            Text("Time Remaining: ", style = MaterialTheme.typography.bodyMedium)
            Text(group.timeRemaining.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            Text("Next Reset: ", style = MaterialTheme.typography.bodyMedium)
            Text(group.nextResetTime.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(group.apps) { app ->
                val appInfo = AppInfo(
                    appName = app.appName,
                    appPackage = app.appPackage
                )
                GroupAppItem(
                    appInfo = appInfo,
                    timeLimit = group.timeHrLimit * 60 + group.timeMinLimit,
                    limitEach = group.limitEach
                )
            }
        }
    }
}
