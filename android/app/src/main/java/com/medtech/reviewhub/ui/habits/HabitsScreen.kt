package com.medtech.reviewhub.ui.habits

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.model.HabitKind
import com.medtech.reviewhub.domain.model.Mood
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.MascotBubble
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood
import com.medtech.reviewhub.ui.components.SectionHeader

@Composable
fun HabitsScreen(vm: HabitsViewModel, mascotKind: MascotKind, aiEnabled: Boolean, reduceAnimation: Boolean = false, onAiSummary: () -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    var showAdd by remember { mutableStateOf(false) }
    var diaryText by remember { mutableStateOf("") }
    var diaryMood by remember { mutableStateOf(Mood.OKAY) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Habits & diary", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        state.streak?.let { streak ->
            MascotBubble(
                kind = mascotKind,
                mood = if (streak.restDayMessage != null) MascotMood.ENCOURAGING else MascotMood.HAPPY,
                line = streak.restDayMessage
                    ?: "Streak: ${streak.current} day(s)! Best: ${streak.best}. Tuloy lang!",
                reduceAnimation = reduceAnimation
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.weeklySummary.isNotBlank()) {
            Banner(text = state.weeklySummary)
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Today's checklist", actionLabel = if (showAdd) "Close" else "Add habit", onAction = { showAdd = !showAdd })

        if (showAdd) {
            AddHabitForm(onAdd = { name, target, kind -> vm.addHabit(name, target, kind); showAdd = false })
        }

        state.habits.forEach { row ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.habit.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${row.amountToday} / ${row.habit.targetPerDay} ${row.habit.kind.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (row.habit.kind == HabitKind.COUNT) {
                        OutlinedButton(onClick = { vm.logHabit(row.habit.id, 1) }) { Text("+1") }
                    } else {
                        OutlinedButton(onClick = { vm.logHabit(row.habit.id, 5) }) { Text("+5 min") }
                    }
                    if (row.done) {
                        Text("✓", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(onClick = { vm.deleteHabit(row.habit) }) { Text("Remove") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Activity heatmap (last 5 weeks)")
        Heatmap(state.heatmap)

        Spacer(Modifier.height(16.dp))
        SectionHeader("How are you feeling today?")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Mood.entries.forEach { mood ->
                FilterChip(
                    selected = diaryMood == mood,
                    onClick = { diaryMood = mood },
                    label = { Text(mood.label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = diaryText,
            onValueChange = { diaryText = it },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            label = { Text("Private diary entry (stays on this device)") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    vm.addDiary(diaryMood, diaryText, emptyList())
                    diaryText = ""
                },
                enabled = diaryText.isNotBlank()
            ) { Text("Save entry") }
            OutlinedButton(onClick = onAiSummary, enabled = aiEnabled) { Text("AI diary summary") }
        }
        if (!aiEnabled) {
            Text("AI summary needs a server URL in Settings — optional.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.aiSummary?.let {
            Spacer(Modifier.height(8.dp))
            AppCard { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Recent diary")
        state.diary.take(10).forEach { entry ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${entry.date} — ${entry.mood.label}", style = MaterialTheme.typography.titleSmall)
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { vm.deleteDiary(entry) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun Heatmap(heatmap: Map<String, Int>) {
    val today = java.time.LocalDate.now()
    val max = (heatmap.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column {
        for (week in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in 0 until 7) {
                    val date = today.minusDays(((4 - week) * 7 + (6 - day)).toLong())
                    val value = heatmap[date.toString()] ?: 0
                    val alpha = if (value == 0) 0.08f else 0.25f + 0.75f * (value.toFloat() / max)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha))
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AddHabitForm(onAdd: (String, Int, HabitKind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("1") }
    var kind by remember { mutableStateOf(HabitKind.MINUTES) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(name, { name = it }, label = { Text("Habit name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(target, { target = it }, label = { Text("Daily target") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            HabitKind.entries.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name) })
                Spacer(Modifier.padding(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onAdd(name, target.toIntOrNull() ?: 1, kind) },
            enabled = name.isNotBlank()
        ) { Text("Add") }
    }
}
