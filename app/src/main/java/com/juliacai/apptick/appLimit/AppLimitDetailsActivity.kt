package com.juliacai.apptick.appLimit

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.juliacai.apptick.data.AppTickDatabase
import com.juliacai.apptick.data.toDomainModel
import com.juliacai.apptick.data.toEntity
import com.juliacai.apptick.newAppLimit.TabTimeLimitActivity
import kotlinx.coroutines.launch

class AppLimitDetailsActivity : AppCompatActivity() {

    private val db by lazy { AppTickDatabase.getDatabase(this) }
    private val appLimitGroupDao by lazy { db.appLimitGroupDao() }
    private var group: AppLimitGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getLongExtra("groupId", -1)
        if (groupId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            group = appLimitGroupDao.getGroup(groupId)?.toDomainModel()
            setContent {
                MaterialTheme {
                    AppLimitDetailsScreen(
                        group = group,
                        onEditClick = { group?.let { showEditOptions(it) } },
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }

    private fun showEditOptions(group: AppLimitGroup) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Group")
            .setItems(arrayOf("Edit Settings", "Delete Group")) { _, which ->
                if (which == 0) {
                    val intent = Intent(this, TabTimeLimitActivity::class.java).apply {
                        putExtra("isEdit", true)
                        putExtra("groupId", group.id)
                    }
                    startActivity(intent)
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
