package com.juliacai.apptick

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    appLimitGroupCount: Int,
    showLockedIcon: Boolean,
    onFabClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPremiumClick: () -> Unit,
    listContent: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AppTick") },
                actions = {
                    IconButton(onClick = onPremiumClick) {
                        if (showLockedIcon) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock modes are locked"
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_unlocked),
                                contentDescription = "Open lock modes"
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFabClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_app_limit)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (appLimitGroupCount == 0) {
                    Text(
                        text = stringResource(id = R.string.add_app_limit),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    listContent()
                }
            }

        }
    }
}
