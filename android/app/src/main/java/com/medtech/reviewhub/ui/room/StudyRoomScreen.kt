package com.medtech.reviewhub.ui.room

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood
import com.medtech.reviewhub.domain.MotivationBank
import com.medtech.reviewhub.domain.model.PomodoroPreset
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.ui.components.SectionHeader
import com.medtech.reviewhub.ui.focus.FocusAppsViewModel
import com.medtech.reviewhub.ui.focus.FocusScreen
import com.medtech.reviewhub.ui.focus.FocusTimerViewModel
import com.medtech.reviewhub.ui.motivation.DailyQuoteCard
import com.medtech.reviewhub.ui.motivation.EncouragementBus
import com.medtech.reviewhub.ui.music.MusicViewModel

/**
 * "My Cozy Study Room" - full-screen focus interface combining the Pomodoro
 * timer (reusing [FocusTimerViewModel]), music controls, the daily quote,
 * a study goal input, the mascot with mood changes across the session, session
 * progress and 5 gradient background scenes. A distraction-free toggle hides
 * the decorations. The existing Android app blocker is integrated exactly as
 * the classic focus screen does - the classic panel is embedded below so
 * nothing is weakened or removed.
 */
@Composable
fun StudyRoomScreen(
    timerVm: FocusTimerViewModel,
    appsVm: FocusAppsViewModel,
    musicVm: MusicViewModel,
    roomVm: StudyRoomViewModel,
    mascotKind: MascotKind,
    focusFeatureEnabled: Boolean,
    reduceAnimation: Boolean,
    motivationBank: MotivationBank,
    dayOfYear: Int,
    favoriteQuotes: Set<String>,
    onToggleFavoriteQuote: (String) -> Unit
) {
    val timer by timerVm.state.collectAsState()
    val appsState by appsVm.state.collectAsState()
    val room by roomVm.state.collectAsState()
    val music by musicVm.player.collectAsState()

    val scene = room.scene
    val night = scene == "night" && !room.distractionFree
    val onScene = if (night) Color.White else MaterialTheme.colorScheme.onBackground

    // Phase-end reactions: gentle chime + optional calmer music on break.
    LaunchedEffect(timer.phase) {
        when (timer.phase) {
            FocusTimerViewModel.Phase.BREAK -> {
                roomVm.onPhaseEnd()
                if (room.musicInteracted && room.musicOnBreaks) {
                    musicVm.playCategory("cozy-sleepy")
                }
            }
            FocusTimerViewModel.Phase.DONE -> roomVm.onPhaseEnd()
            else -> {}
        }
    }

    // Auto-play the chosen playlist when a session starts - only after the user
    // has pressed play at least once (never force audio without interaction).
    LaunchedEffect(timer.phase) {
        if (timer.phase == FocusTimerViewModel.Phase.WORK &&
            room.autoPlayOnStart && room.musicInteracted && !music.isPlaying
        ) {
            if (room.defaultPlaylistId.isNotBlank()) musicVm.playSuggested(room.defaultPlaylistId)
            else musicVm.playQuickPick("morning")
        }
    }

    // Long focus session (>45 min continuous work): gentle "want a short break?"
    var workStartMs by remember { mutableStateOf(0L) }
    var longSessionTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(timer.phase) {
        if (timer.phase == FocusTimerViewModel.Phase.WORK) {
            if (workStartMs == 0L) {
                workStartMs = System.currentTimeMillis()
                longSessionTriggered = false
            }
        } else {
            workStartMs = 0L
            longSessionTriggered = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val start = workStartMs
            if (start != 0L && !longSessionTriggered &&
                System.currentTimeMillis() - start > 45 * 60_000L
            ) {
                longSessionTriggered = true
                EncouragementBus.trigger(
                    com.medtech.reviewhub.domain.ContextKind.LONG_SESSION,
                    seed = start
                )
            }
        }
    }

    val mascotMood = when (timer.phase) {
        FocusTimerViewModel.Phase.WORK -> MascotMood.STUDYING
        FocusTimerViewModel.Phase.BREAK -> MascotMood.SLEEPY
        FocusTimerViewModel.Phase.DONE -> MascotMood.CELEBRATING
        FocusTimerViewModel.Phase.PAUSED -> MascotMood.ENCOURAGING
        else -> MascotMood.HAPPY
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SceneBackground(scene = scene, distractionFree = room.distractionFree,
            reduceAnimation = reduceAnimation)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("My Cozy Study Room", style = MaterialTheme.typography.headlineSmall, color = onScene)
            Text(
                "Pomodoro focus with cozy music, a daily quote and Mochi beside you.",
                style = MaterialTheme.typography.bodySmall,
                color = onScene.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(12.dp))

            // ---- Scenes + distraction-free toggle ----
            if (!room.distractionFree) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudyRoomViewModel.SCENES.forEach { (id, label, _) ->
                        FilterChip(
                            selected = scene == id,
                            onClick = { roomVm.setScene(id) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Distraction-free mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Hides the scene decorations for a clean focus surface.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = room.distractionFree, onCheckedChange = { roomVm.setDistractionFree(it) })
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- Pomodoro card ----
            AppCard {
                Text(
                    timerVm.formatRemaining(timer),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when (timer.phase) {
                        FocusTimerViewModel.Phase.WORK -> "Work - ${timer.workMinutes} min"
                        FocusTimerViewModel.Phase.BREAK -> "Break - ${timer.breakMinutes} min"
                        FocusTimerViewModel.Phase.PAUSED -> "Paused"
                        FocusTimerViewModel.Phase.DONE ->
                            "Done - ${timer.sessionsCompleted} session(s) this sitting"
                        else -> "Ready when you are"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Pomodoros completed today: " +
                        "${appsState.focusMinutesToday / timer.workMinutes.coerceAtLeast(1)} " +
                        "(focus minutes today: ${appsState.focusMinutesToday})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PomodoroPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = timer.preset == preset,
                            onClick = {
                                timerVm.configure(
                                    preset,
                                    workMinutes = if (preset == PomodoroPreset.CUSTOM) appsState.customWork
                                    else preset.workMinutes,
                                    breakMinutes = if (preset == PomodoroPreset.CUSTOM) appsState.customBreak
                                    else preset.breakMinutes
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
                                // Exactly as the classic focus screen: start the
                                // blocker service only with both permissions granted.
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gentle chime at phase end", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Switch(checked = room.chimeEnabled, onCheckedChange = { roomVm.setChime(it) })
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- Study goal ----
            var goalField by remember(room.goal) { mutableStateOf(room.goal) }
            AppCard {
                Text("Current study goal", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = goalField,
                    onValueChange = {
                        goalField = it
                        roomVm.setGoal(it)
                    },
                    label = { Text("e.g. Finish Blood Banking chapter 3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---- Daily quote ----
            var quoteOffset by remember { mutableStateOf(0) }
            DailyQuoteCard(
                bank = motivationBank,
                dayOfYear = dayOfYear,
                offset = quoteOffset,
                favoriteQuotes = favoriteQuotes,
                onShowAnother = { quoteOffset++ },
                onRandom = {
                    quoteOffset = (System.currentTimeMillis() % 365).toInt() - dayOfYear
                },
                onToggleFavorite = onToggleFavoriteQuote
            )
            Spacer(Modifier.height(12.dp))

            // ---- Music panel ----
            AppCard {
                Text("Cozy music", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (music.trackId == null) "Nothing playing yet."
                    else if (music.isPlaying) "Now playing: ${music.title}"
                    else "Paused: ${music.title}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (room.musicOnBreaks) "On breaks: switches to a calmer category."
                    else "On breaks: music pauses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { musicVm.togglePlayPause() }) {
                        Icon(
                            if (music.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (music.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(onClick = { musicVm.playQuickPick("morning") }) { Text("Morning playlist") }
                    OutlinedButton(onClick = { musicVm.playQuickPick("late-night") }) { Text("Late-night") }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- Mascot (mood changes across the session) ----
            MascotBubble(
                kind = mascotKind,
                mood = mascotMood,
                line = when (timer.phase) {
                    FocusTimerViewModel.Phase.WORK -> "Focus mode ON. I'll be right here while you focus."
                    FocusTimerViewModel.Phase.BREAK -> "Break time! Tubig muna, tapos balik tayo."
                    FocusTimerViewModel.Phase.DONE -> "Session complete! Deserve mo yan!"
                    else -> "Cozy room, cozy focus. Let's go!"
                },
                reduceAnimation = reduceAnimation
            )
            Spacer(Modifier.height(12.dp))

            if (!focusFeatureEnabled) {
                Text(
                    "Focus feature is disabled in Settings - the timer still works, " +
                        "the app blocker stays off.",
                    style = MaterialTheme.typography.bodySmall, color = onScene
                )
                Spacer(Modifier.height(8.dp))
            }

            // Surface keeps the classic panel readable on the dark Cozy Night scene.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .padding(4.dp)
            ) {
                androidx.compose.foundation.layout.Column {
                    SectionHeader("Focus mode - app blocker & stats (classic panel)")
                    Text(
                        "The distraction app picker, permission flows and session stats below " +
                            "are the exact same focus panel as before - nothing was removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    FocusScreen(
                        timerVm = timerVm,
                        appsVm = appsVm,
                        mascotKind = mascotKind,
                        focusFeatureEnabled = focusFeatureEnabled
                    )
                }
            }
        }
    }
}

/** 5 background scenes: simple gradient compositions + light decorations. */
@Composable
private fun SceneBackground(scene: String, distractionFree: Boolean, reduceAnimation: Boolean) {
    if (distractionFree || scene == "minimal") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }
    val brush = when (scene) {
        "sakura" -> Brush.verticalGradient(listOf(Color(0xFFFFE3EE), Color(0xFFFFF7FA)))
        "rainy" -> Brush.verticalGradient(listOf(Color(0xFFE7D6F5), Color(0xFFFDE7F1)))
        "night" -> Brush.verticalGradient(listOf(Color(0xFF2A2035), Color(0xFF4A3560)))
        "lavender" -> Brush.verticalGradient(listOf(Color(0xFFE8D9FF), Color(0xFFF8E7FF)))
        else -> Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
        )
    }
    Box(modifier = Modifier.fillMaxSize().background(brush)) {
        if (!reduceAnimation && scene == "sakura") {
            val transition = rememberInfiniteTransition(label = "petals")
            val drift by transition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
                label = "petalDrift"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val petals = 14
                for (i in 0 until petals) {
                    val x = ((i * 97) % 100) / 100f * size.width
                    val y = ((i * 53) % 100) / 100f * size.height
                    val yy = (y + drift * size.height * ((i % 3) + 1) * 0.33f) % size.height
                    drawCircle(Color(0xFFF8BBCF), radius = 6f + (i % 3) * 3f, center = Offset(x, yy))
                }
            }
        } else {
            SceneDecorations(scene)
        }
    }
}

@Composable
private fun SceneDecorations(scene: String) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        when (scene) {
            "rainy" -> {
                for (i in 0 until 22) {
                    val x = ((i * 83) % 100) / 100f * size.width
                    val y = ((i * 47) % 100) / 100f * size.height
                    drawLine(
                        Color(0xFFB39DDB),
                        start = Offset(x, y),
                        end = Offset(x - 12f, y + 46f),
                        strokeWidth = 3f
                    )
                }
            }
            "night" -> {
                drawCircle(Color(0xFFFFE9B8), radius = 46f, center = Offset(size.width * 0.82f, size.height * 0.12f))
                for (i in 0 until 24) {
                    val x = ((i * 61) % 100) / 100f * size.width
                    val y = ((i * 37) % 100) / 100f * size.height * 0.6f
                    drawCircle(Color(0xFFF8BBD0), radius = 3f, center = Offset(x, y))
                }
            }
            "lavender" -> {
                for (i in 0 until 12) {
                    val x = ((i * 71) % 100) / 100f * size.width
                    val y = size.height * 0.82f + ((i * 29) % 40)
                    drawCircle(Color(0xFFC9B2F5), radius = 10f + (i % 3) * 5f, center = Offset(x, y))
                }
            }
            "sakura" -> {
                for (i in 0 until 14) {
                    val x = ((i * 97) % 100) / 100f * size.width
                    val y = ((i * 53) % 100) / 100f * size.height
                    drawCircle(Color(0xFFF8BBCF), radius = 6f + (i % 3) * 3f, center = Offset(x, y))
                }
            }
        }
    }
}
