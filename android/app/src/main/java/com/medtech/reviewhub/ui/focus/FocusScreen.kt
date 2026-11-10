package com.medtech.reviewhub.ui.focus

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.model.PomodoroPreset
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood

/**
 * Focus mode screen: Pomodoro timer (25/5, 50/10, custom), distraction app
 * picker with per-app icons/labels, Usage Access + Overlay permission flows
 * with clear explanations, graceful notification fallback when permissions are
 * missing, and completed-session stats.
 */
@Composable
fun FocusScreen(
    timerVm: FocusTimerViewModel,
    appsVm: FocusAppsViewModel,
    mascotKind: MascotKind,
    focusFeatureEnabled: Boolean
) {
    val timer by timerVm.state.collectAsState()
    val appsState by appsVm.state.collectAsState()
    val context = LocalContext.current

    var showUsageDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appsVm.load()
        appsVm.refreshPermissions()
    }

    // Drive the timer from the UI clock.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(FocusTimerViewModel.TICK_MS)
            timerVm.tick()
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Focus mode", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pomodoro timer + distraction blocker (Android only). " +
                "Blocking is best-effort: Android cannot guarantee full app blocking — " +
                "see the honest notes below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        MascotBubble(
            kind = mascotKind,
            mood = when (timer.phase) {
                FocusTimerViewModel.Phase.WORK -> MascotMood.STUDYING
                FocusTimerViewModel.Phase.BREAK -> MascotMood.SLEEPY
                FocusTimerViewModel.Phase.DONE -> MascotMood.CELEBRATING
                else -> MascotMood.HAPPY
            },
            line = when (timer.phase) {
                FocusTimerViewModel.Phase.WORK -> "Focus mode ON. Blood Banking muna bago TikTok!"
                FocusTimerViewModel.Phase.BREAK -> "Break time! Tubig muna, tapos balik tayo."
                FocusTimerViewModel.Phase.DONE -> "Session complete! Deserve mo yan!"
                else -> "Ready when you are. Choose a Pomodoro and start."
            }
        )

        Spacer(Modifier.height(12.dp))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                timerVm.formatRemaining(timer),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                when (timer.phase) {
                    FocusTimerViewModel.Phase.WORK -> "Work — ${timer.workMinutes} min"
                    FocusTimerViewModel.Phase.BREAK -> "Break — ${timer.breakMinutes} min"
                    FocusTimerViewModel.Phase.PAUSED -> "Paused"
                    FocusTimerViewModel.Phase.DONE -> "Done — ${timer.sessionsCompleted} session(s) completed"
                    else -> "Idle"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PomodoroPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = timer.preset == preset,
                        onClick = {
                            timerVm.configure(
                                preset,
                                workMinutes = if (preset == PomodoroPreset.CUSTOM) appsState.customWork else preset.workMinutes,
                                breakMinutes = if (preset == PomodoroPreset.CUSTOM) appsState.customBreak else preset.breakMinutes
                            )
                        },
                        label = { Text(preset.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (timer.phase) {
                    FocusTimerViewModel.Phase.IDLE, FocusTimerViewModel.Phase.DONE ->
                        Button(onClick = {
                            if (appsState.overlayGranted && appsState.usageGranted) {
                                appsVm.startBlockerService()
                            }
                            timerVm.start()
                        }) { Text("Start focus") }
                    FocusTimerViewModel.Phase.PAUSED ->
                        Button(onClick = { timerVm.resume() }) { Text("Resume") }
                    else ->
                        Button(onClick = { timerVm.pause() }) { Text("Pause") }
                }
                OutlinedButton(onClick = {
                    timerVm.reset()
                    appsVm.stopBlockerService()
                }) { Text("Reset") }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Permission status + graceful fallbacks
        if (!appsState.usageGranted) {
            Banner(
                text = "Usage Access not granted — distraction detection is off. " +
                    "You still get focus reminders as notifications.",
                actionLabel = "Enable",
                onAction = { showUsageDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        }
        if (!appsState.overlayGranted) {
            Banner(
                text = "Display-over-other-apps not granted — the blocking overlay is off. " +
                    "Focus reminders still work.",
                actionLabel = "Enable",
                onAction = { showOverlayDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader2("Distraction apps")
        Text(
            "Pick the apps that pull you away. During a focus session (with permissions granted), " +
                "opening one shows a gentle blocking overlay with an easy '5 more minutes' escape " +
                "and an emergency pause. No hidden monitoring.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        appsState.apps.forEach { app ->
            val blocked = app.packageName in appsState.blocked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                AppIcon(app.packageName, context)
                Spacer(Modifier.padding(6.dp))
                Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = blocked,
                    onClick = { appsVm.toggleBlocked(app) },
                    label = { Text(if (blocked) "Blocked" else "Block") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader2("Focus stats")
        AppCard {
            Text("Focus minutes today: ${appsState.focusMinutesToday}", style = MaterialTheme.typography.bodyMedium)
            Text("Completed sessions (all time): ${appsState.completedSessions}", style = MaterialTheme.typography.bodyMedium)
            appsState.recentSessions.take(3).forEach {
                Text("• ${it.presetLabel} — ${it.completedMinutes} min (${it.interruptions} interruptions)",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Banner(
            text = "Honest limitations: Android does not guarantee absolute blocking of other apps. " +
                "Overlays can be dismissed and some devices restrict usage stats. This feature is " +
                "fully optional, transparent, and easy to disable in Settings or by uninstalling."
        )
    }

    if (showUsageDialog) {
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = { Text("Why Usage Access?") },
            text = {
                Text(
                    "To notice when you open a distracting app during YOUR focus session, the app reads " +
                        "which app is in the foreground using Android's Usage Access API. This only happens " +
                        "while a focus session is running. Android will show its own system screen where you " +
                        "can grant access to MedTech Review Hub — or not. Your choice."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showUsageDialog = false
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) { Text("Open system settings") }
            },
            dismissButton = { TextButton(onClick = { showUsageDialog = false }) { Text("Not now") } }
        )
    }

    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Why display over other apps?") },
            text = {
                Text(
                    "The blocking overlay is a full-screen reminder that appears when you open a blocked " +
                        "app during a focus session. It needs the 'Display over other apps' special permission, " +
                        "which Android lets you grant (or revoke any time) in system settings."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showOverlayDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Open system settings") }
            },
            dismissButton = { TextButton(onClick = { showOverlayDialog = false }) { Text("Not now") } }
        )
    }
}

@Composable
private fun SectionHeader2(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun AppIcon(packageName: String, context: Context) {
    val drawable: Drawable? = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    val bitmap: Bitmap? = remember(drawable) { drawable?.toBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun Drawable.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
