package com.medtech.reviewhub.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.model.QuizResult
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood

// ---------------------------------------------------------------------------
// Quiz setup
// ---------------------------------------------------------------------------

@Composable
fun QuizSetupScreen(
    subjects: List<Pair<String, String>>,
    topics: List<Pair<String, String>>,
    onBack: () -> Unit,
    onStart: (mode: QuizMode, style: QuizStyle, subjectId: String?, topicId: String?) -> Unit
) {
    var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(QuizMode.RANDOM) }
    var style by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(QuizStyle.STUDY) }
    var subjectId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var topicId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Quiz engine", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        QuizMode.entries.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == m, onClick = { mode = m })
                Text(m.label)
            }
        }

        if (mode == QuizMode.ADAPTIVE) {
            Spacer(Modifier.height(8.dp))
            Banner(text = QuizScoringDisclaimer)
        }

        Spacer(Modifier.height(12.dp))
        Text("Feedback", style = MaterialTheme.typography.titleMedium)
        QuizStyle.entries.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = style == s, onClick = { style = s })
                Text(
                    when (s) {
                        QuizStyle.STUDY -> "Study mode — instant feedback + full rationales"
                        QuizStyle.EXAM -> "Exam mode — feedback only after finishing"
                    }
                )
            }
        }

        if (mode == QuizMode.SUBJECT) {
            Spacer(Modifier.height(12.dp))
            Text("Subject", style = MaterialTheme.typography.titleMedium)
            subjects.forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = subjectId == id, onClick = { subjectId = id })
                    Text(name)
                }
            }
        }
        if (mode == QuizMode.TOPIC) {
            Spacer(Modifier.height(12.dp))
            Text("Topic", style = MaterialTheme.typography.titleMedium)
            topics.forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = topicId == id, onClick = { topicId = id })
                    Text(name)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(
                onClick = { onStart(mode, style, subjectId, topicId) },
                modifier = Modifier.weight(1f),
                enabled = (mode != QuizMode.SUBJECT || subjectId != null) && (mode != QuizMode.TOPIC || topicId != null)
            ) { Text("Start quiz") }
        }
        if (mode == QuizMode.MOCK_EXAM) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Mock exam: 100 questions, 150 minutes, exam mode. " +
                    "Pass percentages shown afterwards are study estimates, not ASCP scores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val QuizScoringDisclaimer = com.medtech.reviewhub.domain.QuizScoring.ADAPTIVE_DISCLAIMER

// ---------------------------------------------------------------------------
// Quiz run
// ---------------------------------------------------------------------------

@Composable
fun QuizRunScreen(
    vm: QuizViewModel,
    onAnswer: (String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit
) {
    val state by vm.state.collectAsState()
    val q = state.questions.getOrNull(state.index) ?: return
    val chosen = state.answers[q.id]

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.index + 1} / ${state.questions.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (state.timeLimitSeconds != null) {
                Text(
                    "%02d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onExit) { Text("Exit") }
        }
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            q.vignette?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Text(q.stem, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            q.choices.forEach { choice ->
                val isChosen = chosen == choice.id
                val isCorrect = choice.id == q.correctChoiceId
                val showTruth = state.style == QuizStyle.STUDY && chosen != null
                val container = when {
                    showTruth && isCorrect -> MaterialTheme.colorScheme.secondaryContainer
                    showTruth && isChosen && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                    isChosen -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                Card(
                    onClick = { if (chosen == null) onAnswer(choice.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = container)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Text("${choice.id}.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(4.dp))
                        Text(choice.text)
                    }
                }
                if (showTruth && (isChosen || isCorrect)) {
                    Text(
                        q.rationales[choice.id] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    )
                }
            }

            state.feedback?.let { fb ->
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Text(
                        if (fb.correct) "Correct!" else "Not quite — check the rationale above.",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (fb.correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Explanation", style = MaterialTheme.typography.titleSmall)
                    Text(fb.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (!fb.correct && fb.misconception.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Why was I wrong?", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                        Text(fb.misconception, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (q.reference.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(q.reference, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.index > 0) {
                OutlinedButton(onClick = { vm.previous() }) { Text("Previous") }
            }
            if (chosen == null) {
                Button(onClick = { onAnswer("") }, modifier = Modifier.weight(1f)) { Text("Skip (leave blank)") }
            } else if (state.index < state.questions.size - 1) {
                Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Next") }
            } else {
                Button(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("Finish") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

@Composable
fun QuizResultScreen(
    result: QuizResult,
    questions: List<com.medtech.reviewhub.domain.model.QuizQuestion>,
    answers: Map<String, String>,
    mascotKind: MascotKind,
    reduceAnimation: Boolean = false,
    onOpenLesson: (lessonId: String) -> Unit,
    onReviewIncorrect: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        val pct = (result.score * 100).toInt()
        MascotBubble(
            kind = mascotKind,
            mood = if (pct >= 75) MascotMood.CELEBRATING else if (pct >= 50) MascotMood.HAPPY else MascotMood.ENCOURAGING,
            line = when {
                pct >= 75 -> "Grabe! $pct% - pang-topnotcher ang performance!"
                pct >= 50 -> "Solid, $pct%. Konting polish pa sa weak topics."
                else -> "$pct%. okay lang yan. Laban ulit, walang iwanan!"
            },
            reduceAnimation = reduceAnimation
        )
        Spacer(Modifier.height(16.dp))
        Text("Results", style = MaterialTheme.typography.headlineSmall)
        Text("${result.correct} / ${result.total} correct ($pct%)", style = MaterialTheme.typography.titleLarge)
        Text("Time: ${result.timeSeconds / 60}m ${result.timeSeconds % 60}s", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "These are study estimates of your performance — not ASCP scores or pass guarantees.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text("Accuracy by subject", style = MaterialTheme.typography.titleMedium)
        result.bySubject.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(s.subjectId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${s.correct}/${s.total}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        if (result.weakTopics.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Weak topics", style = MaterialTheme.typography.titleMedium)
            result.weakTopics.forEach { w ->
                Text("${w.topicId} — ${(w.accuracy * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Incorrect questions", style = MaterialTheme.typography.titleMedium)
        result.incorrectQuestionIds.forEach { id ->
            val q = questions.firstOrNull { it.id == id } ?: return@forEach
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(q.stem, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Correct: ${q.correctChoiceId} — ${q.choices.firstOrNull { it.id == q.correctChoiceId }?.text ?: ""}",
                    style = MaterialTheme.typography.bodySmall)
                if (q.misconception.isNotBlank()) {
                    Text("Why was I wrong? ${q.misconception}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                q.lessonId?.let { lesson ->
                    TextButton(onClick = { onOpenLesson(lesson) }) { Text("Open related lesson") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReviewIncorrect) { Text("Practice these again") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
        }
    }
}
