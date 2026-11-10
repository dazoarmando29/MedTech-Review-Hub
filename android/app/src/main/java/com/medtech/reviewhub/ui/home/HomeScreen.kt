package com.medtech.reviewhub.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.R
import com.medtech.reviewhub.domain.model.PlanItem
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.ui.components.MascotFace
import com.medtech.reviewhub.ui.components.SectionHeader
import com.medtech.reviewhub.ui.components.StatTile
import com.medtech.reviewhub.ui.components.StreakFlame
import com.medtech.reviewhub.ui.motivation.DailyQuoteCard

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenLesson: (String) -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenNotes: () -> Unit = {},
    motivationBank: com.medtech.reviewhub.domain.MotivationBank = com.medtech.reviewhub.domain.Motivation.FALLBACK,
    favoriteQuotes: Set<String> = emptySet(),
    onToggleFavoriteQuote: (String) -> Unit = {}
) {
    val state by vm.dashboard.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    val decorations = state.settings.appTheme != "minimal" && !state.settings.reduceAnimation

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        // ---- Greeting (PH timezone aware) ----
        GreetingBanner(state = state, decorations = decorations)
        Spacer(Modifier.height(12.dp))

        // ---- "A Little Motivation for You" (shared/content/motivation.json) ----
        var quoteOffset by remember { mutableStateOf(0) }
        DailyQuoteCard(
            bank = motivationBank,
            dayOfYear = state.dayOfYear,
            offset = quoteOffset,
            favoriteQuotes = favoriteQuotes,
            onShowAnother = { quoteOffset++ },
            onRandom = {
                quoteOffset = (System.currentTimeMillis() % 365).toInt() - state.dayOfYear
            },
            onToggleFavorite = onToggleFavoriteQuote
        )
        Spacer(Modifier.height(12.dp))

        // ---- "Little Notes for You" (private personal notes, never auto-generated) ----
        state.loveNote?.let { note ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "A note for you",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onOpenNotes) { Text("Little Notes for You") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- Optional gift note card ----
        GiftNoteCard(
            note = state.giftNote.ifBlank { stringResource(R.string.gift_note_default) }
        )

        if (state.mascotLine.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            MascotBubble(
                kind = state.mascotKind,
                mood = state.mascotMood,
                line = state.mascotLine,
                name = state.mascotName,
                reduceAnimation = state.settings.reduceAnimation
            )
        }

        Spacer(Modifier.height(12.dp))

        state.examCountdownDays?.let { days ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (days >= 0) "Exam countdown: $days day${if (days == 1) "" else "s"}"
                    else "Exam date passed — plan again whenever you're ready",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Stats grid
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Minutes today", "${state.minutesToday}", Modifier.weight(1f))
            StatTile("Total minutes", "${state.totalMinutes}", Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Flashcards due", "${state.flashcardsDue}", Modifier.weight(1f),
                accent = MaterialTheme.colorScheme.tertiary)
            StatTile("Questions today", "${state.questionsAnsweredToday}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(modifier = Modifier.weight(1f)) {
                StreakFlame(state.streakDays)
                state.streakInfo?.restDayMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppCard(modifier = Modifier.weight(1f)) {
                Text(
                    state.recentQuizAccuracy?.let { "Recent quiz accuracy: ${(it * 100).toInt()}%" }
                        ?: "No quizzes yet",
                    style = MaterialTheme.typography.bodyMedium
                )
                state.weakestSubject?.let { (_, name) ->
                    Spacer(Modifier.height(4.dp))
                    Text("Weakest: $name", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Today's goals")
        if (state.todayItems.isEmpty()) {
            AppCard {
                Text("No plan items for today yet.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOpenLibrary) { Text("Browse the review library") }
            }
        } else {
            state.todayItems.forEach { (item, done) ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, onCheckedChange = { vm.togglePlanItem(item, it) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(planLabel(item), style = MaterialTheme.typography.bodyLarge)
                            item.subjectId?.let {
                                Text(it.replace('-', ' '), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("${item.targetMinutes}m", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("This week")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val maxMinutes = (state.weeklyMinutes.maxOfOrNull { it.second } ?: 1).coerceAtLeast(15)
            state.weeklyMinutes.forEach { (date, minutes) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(((minutes.toFloat() / maxMinutes) * 70).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(date.takeLast(2), style = MaterialTheme.typography.labelSmall)
                    Text("${minutes}m", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Jump back in")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(modifier = Modifier.weight(1f), onClick = {
                state.continueLessonId?.let(onOpenLesson) ?: onOpenLibrary()
            }) {
                Text("Continue reading", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.continueLessonTitle.ifBlank { "Open the library" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppCard(modifier = Modifier.weight(1f), onClick = onOpenQuiz) {
                Text(if (state.resumeQuiz) "Resume quiz" else "Start a quiz", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("Practice makes permanent.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(modifier = Modifier.weight(1f), onClick = onOpenFlashcards) {
                Text("Flashcards due", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("${state.flashcardsDue} waiting", style = MaterialTheme.typography.bodySmall)
            }
            AppCard(modifier = Modifier.weight(1f), onClick = onOpenLibrary) {
                Text("Suggested next", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(state.suggestedTopic?.second ?: "Explore topics", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Personal touch (shared/DESIGN.md v2 "Voice & copy")
// ---------------------------------------------------------------------------

@Composable
private fun GreetingBanner(state: HomeViewModel.Dashboard, decorations: Boolean) {
    val greeting = when (state.hourOfDay) {
        in 5..11 -> stringResource(R.string.greeting_morning, state.displayName)
        in 12..17 -> stringResource(R.string.greeting_afternoon, state.displayName)
        else -> stringResource(R.string.greeting_evening, state.displayName)
    }
    val container = if (decorations) {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
        )
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer)
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.greeting_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        MascotFace(kind = state.mascotKind, mood = state.mascotMood, sizeDp = 64)
    }
}

/** Soft pastel gift-note card (shared/DESIGN.md v2 "Optional gift note card"). */
@Composable
private fun GiftNoteCard(note: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp)
    ) {
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

private fun planLabel(item: PlanItem): String = when (item.type) {
    com.medtech.reviewhub.domain.model.PlanItemType.READ -> "Read: ${item.topicId?.replace('-', ' ') ?: "lesson"}"
    com.medtech.reviewhub.domain.model.PlanItemType.FLASHCARDS -> "Flashcards: ${item.topicId?.replace('-', ' ') ?: "deck"}"
    com.medtech.reviewhub.domain.model.PlanItemType.QUIZ -> "Quiz: ${item.topicId?.replace('-', ' ') ?: "mixed"}"
    com.medtech.reviewhub.domain.model.PlanItemType.REVIEW_WRONG -> "Review wrong answers"
    com.medtech.reviewhub.domain.model.PlanItemType.FORMULAS -> "Formula sheet"
}
