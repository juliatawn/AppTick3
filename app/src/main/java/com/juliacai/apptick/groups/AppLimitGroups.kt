package com.juliacai.apptick.groups

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppLimitGroupsList(
    groups: List<AppLimitGroup>,
    onCardClick: (AppLimitGroup) -> Unit,
    onEditClick: (AppLimitGroup) -> Unit,
    onLockClick: (AppLimitGroup) -> Unit,
    isEditingLocked: Boolean,
    onPauseToggle: (AppLimitGroup) -> Unit = {},
    onDelete: (AppLimitGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(groups, key = { it.id }) { group ->
            AppLimitGroupItem(
                group = group,
                isEditingLocked = isEditingLocked,
                onLockClick = onLockClick,
                onPauseToggle = onPauseToggle,
                onEdit = onEditClick,
                onDelete = onDelete,
                onCardClick = onCardClick
            )
        }
    }
}
