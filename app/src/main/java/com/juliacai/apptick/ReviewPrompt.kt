package com.juliacai.apptick

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit

private const val PREF_LAUNCH_COUNT = "launch_count"
private const val PREF_RESUME_COUNT = "resume_count"
private const val PREF_REVIEW_PROMPT_SHOWN = "review_prompt_shown"
private const val STARTS_BEFORE_REVIEW_PROMPT = 3
private const val RESUMES_BEFORE_REVIEW_PROMPT = 8
private const val PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.juliacai.apptick"

fun incrementLaunchCount(prefs: SharedPreferences) {
    val current = prefs.getLong(PREF_LAUNCH_COUNT, 0L)
    prefs.edit { putLong(PREF_LAUNCH_COUNT, current + 1L) }
}

fun incrementResumeCount(prefs: SharedPreferences) {
    val current = prefs.getLong(PREF_RESUME_COUNT, 0L)
    prefs.edit { putLong(PREF_RESUME_COUNT, current + 1L) }
}

fun shouldShowReviewPrompt(prefs: SharedPreferences): Boolean {
    if (prefs.getBoolean(PREF_REVIEW_PROMPT_SHOWN, false)) return false
    return prefs.getLong(PREF_LAUNCH_COUNT, 0L) >= STARTS_BEFORE_REVIEW_PROMPT ||
        prefs.getLong(PREF_RESUME_COUNT, 0L) >= RESUMES_BEFORE_REVIEW_PROMPT
}

fun markReviewPromptShown(prefs: SharedPreferences) {
    prefs.edit { putBoolean(PREF_REVIEW_PROMPT_SHOWN, true) }
}

@Composable
fun ReviewPromptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enjoying AppTick?") },
        text = {
            Text(
                text = "If you could leave a review here it would mean a lot. Open to feedback and suggestions as well.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Leave a review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe later")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ReviewPromptDialogPreview() {
    AppTheme {
        ReviewPromptDialog(onDismiss = {})
    }
}
