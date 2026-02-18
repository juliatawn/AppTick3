package com.juliacai.apptick.appLimit

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.juliacai.apptick.MainActivity
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.groups.AppLimitGroup
import kotlinx.coroutines.launch

class AppLimitDetailsActivity : AppCompatActivity() {

    private val db by lazy { AppTickDatabase.getDatabase(this) }
    private val appLimitGroupDao by lazy { db.appLimitGroupDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getLongExtra("GROUP_ID", -1)
        if (groupId == -1L) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                AppLimitDetailsScreen(
                    groupId = groupId,
                    viewModel = androidx.lifecycle.ViewModelProvider(this@AppLimitDetailsActivity)[com.juliacai.apptick.MainViewModel::class.java],
                    onEditClick = { selectedGroup -> showEditOptions(selectedGroup) },
                    onBackClick = { finish() }
                )
            }
        }
    }

    private fun showEditOptions(group: AppLimitGroup) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Group")
            .setItems(arrayOf("Edit Settings", "Delete Group")) { dialog: DialogInterface, which: Int ->
                if (which == 0) {
                    val intent = Intent(this@AppLimitDetailsActivity, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_EDIT_GROUP_ID, group.id)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    showDeleteConfirmation(group)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(group: AppLimitGroup) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete this app limit group?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    appLimitGroupDao.deleteAppLimitGroup(group.toEntity())
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
