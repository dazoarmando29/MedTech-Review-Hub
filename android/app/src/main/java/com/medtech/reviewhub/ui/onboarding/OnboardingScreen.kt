package com.medtech.reviewhub.ui.onboarding

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtech.reviewhub.domain.model.ExamTarget
import com.medtech.reviewhub.domain.model.ExplanationStyle
import com.medtech.reviewhub.domain.model.KnowledgeLevel
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood
import kotlinx.coroutines.launch

/**
 * Onboarding wizard: exam target -> date -> level -> subjects -> minutes/day ->
 * schedule -> learning prefs -> explanation style (incl. Taglish) ->
 * notification + focus permission prompts -> generates the local study plan.
 */
@Composable
fun OnboardingScreen(
    subjectNames: Map<String, String>,
    onDone: () -> Unit,
    vm: OnboardingViewModel
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val steps = OnboardingViewModel.Step.entries
    val stepIndex = steps.indexOf(state.step)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Set up your study plan", style = MaterialTheme.typography.headlineSmall)
        Text("Step ${stepIndex + 1} of ${steps.size}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        MascotBubble(
            kind = MascotKind.CAT,
            mood = MascotMood.ENCOURAGING,
            line = "Kaya mo 'to, future RMT! Sagutin mo lang ito, tapos may study plan ka na."
        )
        Spacer(Modifier.height(16.dp))

        when (state.step) {
            OnboardingViewModel.Step.EXAM -> StepExam(state, vm)
            OnboardingViewModel.Step.DATE -> StepDate(state, vm)
            OnboardingViewModel.Step.LEVEL -> StepLevel(state, vm)
            OnboardingViewModel.Step.SUBJECTS -> StepSubjects(state, vm, subjectNames)
            OnboardingViewModel.Step.TIME -> StepTime(state, vm)
            OnboardingViewModel.Step.SCHEDULE -> StepSchedule(state, vm)
            OnboardingViewModel.Step.PREFERENCES -> StepPrefs(state, vm)
            OnboardingViewModel.Step.STYLE -> StepStyle(state, vm)
            OnboardingViewModel.Step.PERMISSIONS -> StepPermissions(state, vm)
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stepIndex > 0) {
                OutlinedButton(onClick = { vm.prevStep() }) { Text("Back") }
            }
            Button(
                onClick = {
                    if (!vm.nextStep()) {
                        scope.launch {
                            vm.finish()
                            onDone()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (stepIndex == steps.lastIndex) "Generate my plan" else "Next")
            }
        }
    }
}

@Composable
private fun StepExam(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("Which exam are you preparing for?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    ExamTarget.entries.forEach { target ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = state.examTarget == target, onClick = { vm.selectExam(target) })
            Text(
                when (target) {
                    ExamTarget.MLS_ASCPI -> "MLS / ASCPi (international)"
                    ExamTarget.PH_BOARD -> "Philippine MTLE (Board Exam)"
                    ExamTarget.CUSTOM -> "Custom / other"
                }
            )
        }
    }
}

@Composable
private fun StepDate(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("When is your target exam date?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    var dateText by remember(state.targetExamDate) { mutableStateOf(state.targetExamDate ?: "") }
    OutlinedTextField(
        value = dateText,
        onValueChange = { dateText = it; vm.setTargetDate(it.ifBlank { null }) },
        label = { Text("YYYY-MM-DD (optional)") },
        modifier = Modifier.fillMaxWidth()
    )
    Text("Leave empty if not scheduled yet — we'll plan for 8 weeks.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StepLevel(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("How would you rate your current knowledge?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    KnowledgeLevel.entries.forEach { level ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = state.knowledgeLevel == level, onClick = { vm.setLevel(level) })
            Text(level.name.lowercase().replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
private fun StepSubjects(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel, names: Map<String, String>) {
    Text("Your strong and weak subjects", style = MaterialTheme.typography.titleMedium)
    Text("Weak subjects get 1.5x study time in your plan.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    vm.allSubjectIds.forEach { id ->
        val name = names[id] ?: id
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            FilterChip(selected = id in state.strongSubjects, onClick = { vm.toggleStrong(id) }, label = { Text("Strong") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = id in state.weakSubjects, onClick = { vm.toggleWeak(id) }, label = { Text("Weak") })
        }
    }
}

@Composable
private fun StepTime(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("How many minutes per day can you study?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text("${state.minutesPerDay} minutes/day", style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary)
    Slider(
        value = state.minutesPerDay.toFloat(),
        onValueChange = { vm.setMinutesPerDay(it.toInt()) },
        valueRange = 10f..300f
    )
}

@Composable
private fun StepSchedule(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("Your preferred weekly schedule", style = MaterialTheme.typography.titleMedium)
    Text("Optional — tap a day to add an evening 18:00-21:00 window.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").forEach { day ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(day.replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
            val has = state.schedule[day]?.isNotEmpty() == true
            FilterChip(
                selected = has,
                onClick = {
                    if (has) vm.clearSchedule(day) else vm.setScheduleWindow(day, 18 * 60, 21 * 60)
                },
                label = { Text(if (has) "18:00–21:00" else "Add window") }
            )
        }
    }
}

@Composable
private fun StepPrefs(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("How do you like to learn?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    listOf("reading", "flashcards", "practice", "questions", "formulas").forEach { pref ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = pref in state.learningPrefs,
                onClick = { vm.toggleLearningPref(pref) },
                label = { Text(pref.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}

@Composable
private fun StepStyle(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("Explanation style", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    ExplanationStyle.entries.forEach { style ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioButton(selected = state.explanationStyle == style, onClick = { vm.setExplanationStyle(style) })
            Text(
                when (style) {
                    ExplanationStyle.FORMAL_ENGLISH -> "Formal English (exam-style)"
                    ExplanationStyle.SIMPLE_ENGLISH -> "Simple English (easy reads)"
                    ExplanationStyle.TAGLISH -> "Taglish (Pinoy style, may halong Tagalog)"
                }
            )
        }
    }
}

@Composable
private fun StepPermissions(state: OnboardingViewModel.OnboardingState, vm: OnboardingViewModel) {
    Text("Notifications & Focus mode", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Study reminders (notifications)", modifier = Modifier.weight(1f))
            FilterChip(selected = state.notificationsEnabled, onClick = { vm.setNotifications(!state.notificationsEnabled) },
                label = { Text(if (state.notificationsEnabled) "On" else "Off") })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Focus mode + app blocker (Android only)", modifier = Modifier.weight(1f))
            FilterChip(selected = state.focusFeatureEnabled, onClick = { vm.setFocusFeature(!state.focusFeatureEnabled) },
                label = { Text(if (state.focusFeatureEnabled) "On" else "Off") })
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Focus mode can show a blocking overlay over distracting apps and needs " +
                "'Display over other apps' + 'Usage access' permissions — you'll be asked " +
                "clearly before anything is enabled. No hidden monitoring.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {}) { Text("You can change all of this later in Settings.") }
}
