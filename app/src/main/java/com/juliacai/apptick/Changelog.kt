package com.juliacai.apptick

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

private const val PREF_LAST_SEEN_CHANGELOG_VERSION = "last_seen_changelog_version"

private const val CHANGELOG_TITLE = "CHANGELOG\n2026.4.31v49"

private const val CHANGELOG_BODY = """
- Updated notification in notification shade to be less intrusive (notifications are required to keep AppTick up and running while you use your apps)
- Updated drag and drop of groups to be smoother and not easy to accidentally move - has wiggle!

---
    
2026.4.18v48
- Fixed edit not updating time reset immediately (bug fix)

---

2026.4.11v46
- "LOCK NOW" button for Lockdown mode users to lock limits faster
- Minor UI improvements

---

2026.4.10v45

- More reliable app limits with Accessibility Service optional toggle (Enhanced App Detection), improves notification shade, split screen, and floating window app limiting.
- Fixed bugs

---

2026.3.10v3 - MAJOR UPDATES

New premium features:
- Floating Time Left Bubble -- it remembers where you placed it per app. Draggable, shows time left in hours and minutes.
- Time Range -- set the time range your app limits are active, then choose when the app limit you specified isn't active to either block the app completely unless during the time range OR to have no app limits so freely use the app.
- Reset Time Limit Interval -- reset time limits at any hour/min interval you set. Ex. Get 10mins of app time every 1.5hrs.
- Cumulative Time (optional add on to Reset Limit) -- carryover unused app time to the next reset interval. Ex. Every 1.5hrs get 10 more minutes to use your app limit, so say I use 5mins in that 1.5hrs then the next 1.5hrs I get 15mins (5 + 10 mins).
- Backup and restore option -- can backup AppTick app limit groups and settings to a file, and can import the file on any of your devices that have AppTick (local save).
- Lockdown mode (fixed) -- lock app limit settings from being changed, set a day to change setting OR set weekday(s) that you can change them.
- Fingerprint/biometrics and Security key are optional to add to Password mode.
- Dark mode & light mode toggle.
- Color palette for AppTick.
- Duplicate groups.

New FREE features:
- Group details page, you can click on the card in the main group to see how much time each app has been used for and how much time is left for the app limit.
- Groups are now editable.
- Ability to turn off time limits so the app is just blocked on the days you select.
- Drag and drop the Group cards (on the Main Page) and App cards (in the Group Details Page) any order you like.
- Notification shows time left if the current app in use is in an active app group time limit.
- The list of app icons on the Group cards are scrollable.
- NO ADS!

Reliability updates:
- Prompt to turn off battery optimization.
- Fixed crashes.
- More reliable blocking.
"""

fun shouldShowChangelogOnLaunch(prefs: SharedPreferences, versionCode: Long): Boolean {
    val lastSeenVersion = prefs.getLong(PREF_LAST_SEEN_CHANGELOG_VERSION, -1L)
    return lastSeenVersion != versionCode
}

fun markChangelogSeen(prefs: SharedPreferences, versionCode: Long) {
    prefs.edit { putLong(PREF_LAST_SEEN_CHANGELOG_VERSION, versionCode) }
}

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CHANGELOG_TITLE) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScrollWithIndicator(scrollState = scrollState, indicatorColor = rememberScrollbarColor())
            ) {
                Text(
                    text = CHANGELOG_BODY.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ChangelogDialogPreview() {
    AppTheme {
        ChangelogDialog(onDismiss = {})
    }
}
