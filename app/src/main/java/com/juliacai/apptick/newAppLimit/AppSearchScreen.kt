package com.juliacai.apptick.newAppLimit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchScreen() {
    var searchTerm by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search Apps",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = searchTerm,
                onValueChange = { searchTerm = it },
                label = { Text("Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            // TODO: Add a LazyColumn here to display search results
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppSearchScreenPreview() {
    AppTheme {
        AppSearchScreen()
    }
}
