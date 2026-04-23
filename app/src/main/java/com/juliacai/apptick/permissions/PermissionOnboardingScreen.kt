package com.juliacai.apptick.permissions

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.juliacai.apptick.AppTheme
import com.juliacai.apptick.R
import com.juliacai.apptick.backgroundProcesses.AppTickAccessibilityService
import com.juliacai.apptick.verticalScrollWithIndicator

/**
 * Single-page permissions screen. Shows every permission AppTick uses in one
 * list so the user can scan what's required vs optional, with a one-line
 * "why" for each. Each row's button deep-links straight to AppTick's entry in
 * the relevant system settings page so the user doesn't have to scroll.
 */

// ── Data model ────────────────────────────────────────────────────────────────

private data class PermissionItem(
    val title: String,
    val why: String,
    val isOptional: Boolean,
    // Extra hint shown under the description. Used on Accessibility because Samsung's
    // Settings app ignores the deep-link extras and lands the user on the general
    // Accessibility page without scrolling to AppTick.
    val hint: String? = null,
)

private val items = listOf(
    PermissionItem(
        title = "Display over other apps",
        why = "Lets AppTick show the block screen on top of apps that have hit their time limit.",
        isOptional = false,
    ),
    PermissionItem(
        title = "Usage access",
        why = "Tracks how long you spend in each app so limits can be enforced.",
        isOptional = false,
    ),
    PermissionItem(
        title = "Notifications",
        why = "Shows time-limit warnings and keeps the tracking service running reliably.",
        isOptional = false,
    ),
    PermissionItem(
        title = "Accessibility",
        why = "Detects the foreground app instantly — more reliable on split-screen, floating windows, and some OEMs.",
        isOptional = true,
        hint = "On Samsung: tap \"Installed apps\" (or \"Downloaded apps\") to find AppTick.",
    ),
)

// ── Permission checks ─────────────────────────────────────────────────────────

private fun isOverlayGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

private fun isAccessibilityGranted(context: Context): Boolean =
    AppTickAccessibilityService.isAccessibilityServiceEnabled(context)

private fun isUsageStatsGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun isNotificationGranted(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun isStepGranted(stepIndex: Int, context: Context): Boolean = when (stepIndex) {
    0 -> isOverlayGranted(context)
    1 -> isUsageStatsGranted(context)
    2 -> isNotificationGranted(context)
    3 -> isAccessibilityGranted(context)
    else -> true
}

// ── Settings deep-links ───────────────────────────────────────────────────────

private fun launchSettingsForStep(stepIndex: Int, context: Context) {
    when (stepIndex) {
        0 -> launchOverlaySettings(context)
        1 -> launchUsageAccessSettings(context)
        2 -> launchNotificationSettings(context)
        3 -> launchAccessibilitySettings(context)
    }
}

private fun launchOverlaySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
    )
}

private fun launchUsageAccessSettings(context: Context) {
    // Package-scoped URI opens directly on AppTick's row on most OEMs; stock Android
    // falls through with ActivityNotFoundException so we retry with the generic list.
    val deep = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri())
    try {
        context.startActivity(deep)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
}

private fun launchNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    )
}

private fun launchAccessibilitySettings(context: Context) {
    // Pass AppTick's service component via the Settings fragment-args bundle so the
    // Accessibility screen scrolls to and highlights our row on supported builds.
    val component = ComponentName(context, AppTickAccessibilityService::class.java).flattenToString()
    val bundle = Bundle().apply {
        putString(":settings:fragment_args_key", component)
    }
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(":settings:fragment_args_key", component)
            putExtra(":settings:show_fragment_args", bundle)
        }
    )
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun PermissionOnboardingScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val resume = rememberResumeTrigger()
    val granted = remember(resume) { BooleanArray(items.size) { isStepGranted(it, context) } }
    val allRequiredGranted = items.indices.all { granted[it] || items[it].isOptional }
    val allGranted = items.indices.all { granted[it] }

    // Auto-advance to the main screen the moment every permission (including the
    // optional Accessibility one) is granted — no need for the user to tap Continue.
    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    // Required perms can't be skipped — only the optional Accessibility row. So:
    //   all granted       → no button (auto-advanced above)
    //   only optional ungranted → show "Skip for now"
    //   any required ungranted → no button (user must grant via per-row Allow)
    val bottomButtonLabel: String? = when {
        allGranted -> null
        allRequiredGranted -> "Skip for now"
        else -> null
    }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollWithIndicator()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (bottomButtonLabel != null) 96.dp else 24.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "All data stays on your device. AppTick doesn't send your app usage or activity anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                items.forEachIndexed { index, item ->
                    PermissionRow(
                        item = item,
                        stepIndex = index,
                        granted = granted[index],
                        onGrant = { launchSettingsForStep(index, context) },
                    )
                    if (index < items.lastIndex) Spacer(Modifier.height(10.dp))
                }
            }

            if (bottomButtonLabel != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onAllGranted,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(bottomButtonLabel)
                    }
                }
            }
        }
    }
}

// ── Row ───────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRow(
    item: PermissionItem,
    stepIndex: Int,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PermissionIcon(stepIndex = stepIndex)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Tag sits above the title so long titles ("Display over other apps")
                // can't push it off-screen on narrow phones.
                Text(
                    text = if (item.isOptional) "OPTIONAL" else "REQUIRED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isOptional)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (granted) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Button(onClick = onGrant) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun PermissionIcon(stepIndex: Int) {
    val modifier = Modifier.size(28.dp)
    when (stepIndex) {
        0 -> Image(
            painter = painterResource(id = R.drawable.ic_overlay),
            contentDescription = null,
            modifier = modifier,
        )
        1 -> Image(
            painter = painterResource(id = R.drawable.ic_usage_stats),
            contentDescription = null,
            modifier = modifier,
        )
        2 -> Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = null,
            modifier = modifier,
        )
        3 -> Image(
            painter = painterResource(id = R.drawable.ic_accessibility),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

// ── Resume trigger helper ─────────────────────────────────────────────────────

/**
 * Returns an incrementing counter that changes every time the lifecycle resumes,
 * so re-reading permission state works after the user returns from system settings.
 */
@Composable
internal fun rememberResumeTrigger(): Int {
    val counter = rememberSaveable { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                counter.intValue++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return counter.intValue
}

@Preview(showBackground = true)
@Composable
private fun PermissionOnboardingScreenPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            items.forEachIndexed { idx, item ->
                PermissionRow(
                    item = item,
                    stepIndex = idx,
                    granted = idx == 0,
                    onGrant = {},
                )
            }
        }
    }
}
