package com.juliacai.apptick.groups

import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppLimitGroupsList(
    groups: List<AppLimitGroup>,
    onGroupClick: (AppLimitGroup) -> Unit,
    onLockClick: (AppLimitGroup) -> Unit,
    onPauseToggle: (AppLimitGroup) -> Unit = {},
    onDelete: (AppLimitGroup) -> Unit = {},
    prefs: SharedPreferences
) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(groups, key = { it.id }) { group ->
            AppLimitGroupItem(
                group = group,
                onPauseToggle = onPauseToggle,
                onEdit = onGroupClick,
                onDelete = onDelete,
                onClick = onGroupClick
            )
        }
    }
}
