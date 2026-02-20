package com.juliacai.apptick.premiumMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.verticalScrollWithIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownTimeScreen(
    selectedDate: String,
    selectedTime: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Lockdown Time") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScrollWithIndicator(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = onDateClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Date: $selectedDate")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onTimeClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Time: $selectedTime")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onConfirmClick, modifier = Modifier.fillMaxWidth()) {
                Text("Confirm")
            }
        }
    }
}
