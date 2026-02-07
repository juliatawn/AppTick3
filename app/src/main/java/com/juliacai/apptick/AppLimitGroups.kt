package com.juliacai.apptick

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.juliacai.apptick.appLimit.AppLimitGroup

@Composable
fun AppLimitGroupsList(groups: List<AppLimitGroup>) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(groups) { group ->
            AppLimitGroupItem(group = group)
        }
    }
}

@Composable
fun AppLimitGroupItem(group: AppLimitGroup) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = group.name ?: "Unnamed Group",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        // TODO: Add more details from the AppLimitGroup
    }
}

@Preview(showBackground = true)
@Composable
fun AppLimitGroupsListPreview() {
    MaterialTheme {
        AppLimitGroupsList(
            groups = listOf(
                AppLimitGroup(id = 1, name = "Social Media", timeHrLimit = 1),
                AppLimitGroup(id = 2, name = "Games", timeMinLimit = 30),
            )
        )
    }
}
