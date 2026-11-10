package com.medtech.reviewhub.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.R
import com.medtech.reviewhub.data.prefs.ThemeMode
import com.medtech.reviewhub.domain.model.ExplanationStyle
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.SectionHeader
import com.medtech.reviewhub.ui.components.composeColor
import com.medtech.reviewhub.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onAbout: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.checkForUpdates() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf(state.settings.apiBaseUrl) }
    var showRegister by remember { mutableStateOf(false) }
    var mascotNameField by remember(state.settings.mascotName) { mutableStateOf(state.settings.mascotName) }
    var greetingNameField by remember(state.settings.displayName) { mutableStateOf(state.settings.displayName) }
    var giftNoteField by remember(state.settings.giftNote) { mutableStateOf(state.settings.giftNote) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // ---- Appearance ----
        SectionHeader("Appearance")
        AppCard {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Text(
                "Strawberry (default), Sakura, Lavender, Cozy Night, Minimal or System.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            AppTheme.entries.toList().chunked(3).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { theme ->
                        FilterChip(
                            selected = state.settings.appTheme == theme.id,
                            onClick = { vm.setAppTheme(theme.id) },
                            label = { Text(theme.label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (state.settings.appTheme == AppTheme.SYSTEM.id) {
                Text("System mode follows:", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = state.settings.themeMode == mode, onClick = { vm.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Text scale: ${state.settings.textScalePercent}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = state.settings.textScalePercent.toFloat(),
                onValueChange = { vm.setTextScale(it.toInt()) },
                valueRange = 100f..150f
            )
            Text("Mascot", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MascotKind.entries.forEach { kind ->
                    FilterChip(selected = state.settings.mascotKind == kind, onClick = { vm.setMascot(kind) },
                        label = { Text(if (kind == MascotKind.CAT) "${state.settings.mascotName} (cat)" else "Bantay (dog)") })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = mascotNameField,
                onValueChange = { mascotNameField = it; vm.setMascotName(it) },
                label = { Text("Mascot name") },
                supportingText = { Text("Default: ${stringResource(R.string.mascot_name_default)}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = greetingNameField,
                onValueChange = { greetingNameField = it; vm.setDisplayName(it) },
                label = { Text("Display name (dashboard greeting)") },
                supportingText = { Text("Default: ${stringResource(R.string.display_name_default)}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = giftNoteField,
                onValueChange = { giftNoteField = it; vm.setGiftNote(it) },
                label = { Text("Gift note (optional)") },
                supportingText = { Text(stringResource(R.string.gift_note_default)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.settings.seriousMode, onClick = { vm.setSeriousMode(!state.settings.seriousMode) },
                    label = { Text("Serious Mode") })
                FilterChip(selected = state.settings.reduceAnimation, onClick = { vm.setReduceAnimation(!state.settings.reduceAnimation) },
                    label = { Text("Reduce animation") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.settings.muteMascot, onClick = { vm.setMuteMascot(!state.settings.muteMascot) },
                    label = { Text("Mute mascot") })
                FilterChip(selected = state.settings.disableCommentary, onClick = { vm.setDisableCommentary(!state.settings.disableCommentary) },
                    label = { Text("No commentary") })
            }
            if (state.settings.seriousMode) {
                Spacer(Modifier.height(8.dp))
                Text("Serious Mode hides jokes, mascot commentary, memes and mini-games.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text("Explanation style", style = MaterialTheme.typography.titleSmall)
            ExplanationStyle.entries.forEach { style ->
                Row {
                    FilterChip(selected = false, onClick = { vm.setExplanationStyle(style) },
                        label = { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }

        // ---- Study behavior ----
        SectionHeader("Study")
        AppCard {
            Row {
                FilterChip(selected = state.settings.notificationsEnabled,
                    onClick = { vm.setNotifications(!state.settings.notificationsEnabled) },
                    label = { Text("Notifications") })
                Spacer(Modifier.padding(4.dp))
                FilterChip(selected = state.settings.focusFeatureEnabled,
                    onClick = { vm.setFocusFeature(!state.settings.focusFeatureEnabled) },
                    label = { Text("Focus feature") })
            }
            Spacer(Modifier.height(8.dp))
            Text("Daily limits — new: ${state.settings.dailyNewLimit}, review: ${state.settings.dailyReviewLimit}",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.settings.dailyNewLimit.toFloat(),
                onValueChange = { vm.setDailyLimits(it.toInt(), state.settings.dailyReviewLimit) },
                valueRange = 0f..50f
            )
            Text("Requested retention: ${(state.settings.requestedRetention * 100).toInt()}% (FSRS)",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.settings.requestedRetention,
                onValueChange = { vm.setRetention(it) },
                valueRange = 0.80f..0.95f
            )
        }

        // ---- Highlight meanings ----
        SectionHeader("Highlight color meanings")
        AppCard {
            Text(
                stringResource(R.string.highlight_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.highlight_meanings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            HighlightColor.entries.forEach { color ->
                var meaning by remember(state.settings.highlightMeanings) {
                    mutableStateOf(state.settings.highlightMeanings[color] ?: "")
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color.composeColor())
                    )
                    Text(
                        color.label,
                        modifier = Modifier.weight(0.3f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = meaning,
                        onValueChange = { meaning = it; vm.setHighlightMeaning(color, it) },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true
                    )
                }
            }
        }

        // ---- Permissions (focus) ----
        SectionHeader("Focus permissions")
        AppCard {
            Text("Usage Access & Display-over-other-apps are requested only when you use Focus mode. Revoke any time.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                    Text("Usage access")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")))
                }) { Text("Overlay permission") }
            }
        }

        // ---- Network / account ----
        SectionHeader("Server & account")
        AppCard {
            OutlinedTextField(
                value = apiBase,
                onValueChange = { apiBase = it; vm.setApiBaseUrl(it) },
                label = { Text("API base URL (empty = fully offline)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text("Example: https://your-server/api/v1 — needed only for sync, AI tools and account features.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            if (state.signedInEmail == null) {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password (10+ chars)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (showRegister) {
                    OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.signIn(email, password) }, enabled = email.isNotBlank() && password.length >= 10) {
                        Text("Sign in")
                    }
                    OutlinedButton(onClick = {
                        if (showRegister) vm.register(email, password, displayName.ifBlank { "Student" })
                        else showRegister = true
                    }) { Text(if (showRegister) "Create account" else "Register") }
                }
            } else {
                Text("Signed in as ${state.signedInEmail}", style = MaterialTheme.typography.bodyMedium)
                Row {
                    OutlinedButton(onClick = { vm.signOut() }) { Text("Sign out") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { /* manual sync via AuthRepository in container */ }) { Text("Sync now") }
                }
                Text("Progress sync is best-effort and runs in the background when online.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.authMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        // ---- Data ----
        SectionHeader("Data")
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.exportData() }) { Text("Export JSON") }
                OutlinedButton(onClick = { vm.checkForUpdates(state.settings.dismissedUpdateVersion) }) {
                    Text("Check for updates")
                }
                OutlinedButton(onClick = onAbout) { Text("About & disclaimers") }
            }
            state.updateCheckMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.exportJson?.let { json ->
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(send, "Export study data"))
                }) { Text("Share export via…") }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    state.updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            title = { Text("Update available: v${info.version}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Release notes:", style = MaterialTheme.typography.titleSmall)
                    Text(info.releaseNotes.take(1200), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Updates are never installed automatically — you download and sideload the APK yourself.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (info.downloadUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.dismissUpdateDialog() }) { Text("Later") }
                    TextButton(onClick = { vm.dismissUpdate(info.version) }) { Text("Don't remind for this version") }
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// About + disclaimers
// ---------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("About & disclaimers", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text("MedTech Review Hub — Android", style = MaterialTheme.typography.titleMedium)
            Text(
                "An independent study reviewer for Medical Laboratory Science board exams " +
                    "(ASCPi MLS and Philippine MTLE). Built by students, for students.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text("Disclaimers", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                "This app is an INDEPENDENT review app. It is NOT affiliated with, endorsed by, or sponsored by ASCP (American Society for Clinical Pathology) or any examining body.",
                "\"Adaptive practice (self-adjusting difficulty)\" is a simple documented self-adjusting rule. It is NOT the ASCP computer-adaptive test (CAT).",
                "Practice percentages, readiness scores and streaks are estimates of your study performance. They are NOT ASCP scores and NOT pass guarantees.",
                "Content references real textbooks and guidelines, but reference ranges vary by laboratory, method, age, sex and population — always verify against your local laboratory.",
                "PDF text extraction is best-effort (scanned PDFs may yield no extractable text).",
                "Focus mode cannot guarantee absolute blocking of other apps — Android platform limitation.",
                "AI features require a configured server and may be unavailable (503). The app is fully functional without AI.",
                "Nothing in this app is medical advice for patient care."
            ).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Banner(text = "Made with care for future MLS. Kaya mo 'to!")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}
