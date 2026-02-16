package com.juliacai.apptick.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juliacai.apptick.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Unified permission onboarding screen that presents all 3 required permissions
 * (Overlay, Usage Stats, Notifications) as steps in a single flow with animated
 * transitions and a progress indicator.
 *
 * Auto-advances past already-granted permissions on each resume.
 */

// ── Data model for each permission step ───────────────────────────────────────

private data class PermissionStepData(
    val title: String,
    val description: String,
    val whyNeeded: String,
    val howToSteps: List<String>,
    val buttonText: String = "Open Settings"
)

private val steps = listOf(
    PermissionStepData(
        title = "Overlay Permission",
        description = "AppTick needs to display blocking screens over other apps when time limits are reached.",
        whyNeeded = "• Show blocking screens when time limits are reached\n• Display usage statistics and warnings\n• Prevent access to blocked apps\n• Help you stay focused",
        howToSteps = listOf(
            "Tap the button below to open Settings",
            "Find 'AppTick' in the list",
            "Enable 'Display over other apps'",
            "Return to AppTick"
        )
    ),
    PermissionStepData(
        title = "Usage Access Permission",
        description = "AppTick needs to track app usage so it can enforce the time limits you set.",
        whyNeeded = "• Track time spent in apps\n• Set and enforce time limits\n• Show usage statistics\n• Block apps when limits are reached",
        howToSteps = listOf(
            "Tap the button below to open Settings",
            "Find 'AppTick' in the list",
            "Enable 'Usage Access'",
            "Return to AppTick"
        )
    ),
    PermissionStepData(
        title = "Notification Permission",
        description = "AppTick needs notifications to show time limit warnings and keep its background service running.",
        whyNeeded = "• Show time limit warnings before apps are blocked\n• Display remaining time in the notification bar\n• Keep the usage-tracking service running reliably",
        howToSteps = listOf(
            "Tap the button below to open Settings",
            "Go to Notifications",
            "Enable notifications for AppTick"
        )
    )
)

// ── Permission check helpers ──────────────────────────────────────────────────

private fun isOverlayGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)

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
    else -> true
}

private fun launchSettingsForStep(stepIndex: Int, context: Context) {
    val intent = when (stepIndex) {
        0 -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        1 -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        2 -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        else -> return
    }
    context.startActivity(intent)
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun PermissionOnboardingScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    // Re-check permissions on every resume to auto-advance
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeCounter = rememberResumeTrigger()

    LaunchedEffect(resumeCounter) {
        // Advance past any already-granted steps
        var step = currentStep
        while (step < steps.size && isStepGranted(step, context)) {
            step++
        }
        if (step >= steps.size) {
            onAllGranted()
        } else {
            currentStep = step
        }
    }

    if (currentStep >= steps.size) return

    val stepData = steps[currentStep]

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator dots
            StepIndicator(total = steps.size, current = currentStep)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Step ${currentStep + 1} of ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Animated step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "permissionStep"
            ) { step ->
                PermissionStepContent(
                    data = steps[step],
                    stepIndex = step,
                    onOpenSettings = { launchSettingsForStep(step, context) }
                )
            }
        }
    }
}

// ── Step content ──────────────────────────────────────────────────────────────

@Composable
private fun PermissionStepContent(
    data: PermissionStepData,
    stepIndex: Int,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        when (stepIndex) {
            0 -> Image(
                painter = painterResource(id = R.drawable.ic_overlay),
                contentDescription = null,
                modifier = Modifier.height(64.dp)
            )
            1 -> Image(
                painter = painterResource(id = R.drawable.ic_usage_stats),
                contentDescription = null,
                modifier = Modifier.height(64.dp)
            )
            2 -> Image(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                modifier = Modifier.height(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // How-to card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How to enable:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                data.howToSteps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step")
                    if (index < data.howToSteps.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Why-needed card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Why we need this:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(data.whyNeeded)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(data.buttonText)
        }
    }
}

// ── Step indicator (dots) ─────────────────────────────────────────────────────

@Composable
private fun StepIndicator(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

// ── Resume trigger helper ─────────────────────────────────────────────────────

/**
 * Returns an incrementing counter that changes every time the lifecycle resumes,
 * so LaunchedEffect can re-run permission checks.
 */
@Composable
private fun rememberResumeTrigger(): Int {
    val counter = rememberSaveable { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle) {
        val flow = MutableStateFlow(0)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                flow.value++
            }
        }
        lifecycle.addObserver(observer)
        flow.collect { counter.intValue = it }
    }

    return counter.intValue
}
