package com.juliacai.apptick.newAppLimit

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.juliacai.apptick.AppInfo
import com.juliacai.apptick.deviceApps.AppListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
    viewModel: AppLimitViewModel,
    onNextClick: () -> Unit,
    onCancel: () -> Unit
) {
    val appListViewModel: AppListViewModel = viewModel()
    val apps by appListViewModel.filteredApps.collectAsState()
    val selectedApps by viewModel.selectedApps.observeAsState(emptyList())
    val searchTerm by appListViewModel.searchTerm.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Apps to Limit") },
                actions = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    IconButton(onClick = onNextClick) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TextField(
                value = searchTerm,
                onValueChange = appListViewModel::onSearchTermChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Search Apps") }
            )
            if (apps.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(apps) { app ->
                        val isSelected = selectedApps.contains(app)
                        AppListItem(app = app, isSelected = isSelected, onAppSelected = { 
                            val currentSelected = selectedApps.toMutableList()
                            if (currentSelected.contains(app)) {
                                currentSelected.remove(app)
                            } else {
                                currentSelected.add(app)
                            }
                            viewModel.setSelectedApps(currentSelected)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onAppSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAppSelected() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = app.appIcon),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = app.appName ?: "", modifier = Modifier.weight(1f))
        Checkbox(checked = isSelected, onCheckedChange = { onAppSelected() })
    }
}
