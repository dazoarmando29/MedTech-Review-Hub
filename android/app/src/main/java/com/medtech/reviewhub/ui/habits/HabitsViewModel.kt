package com.medtech.reviewhub.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.repo.HabitRepository
import com.medtech.reviewhub.domain.model.DiaryEntry
import com.medtech.reviewhub.domain.model.Habit
import com.medtech.reviewhub.domain.model.HabitKind
import com.medtech.reviewhub.domain.model.HabitLog
import com.medtech.reviewhub.domain.model.Mood
import com.medtech.reviewhub.domain.model.StreakInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Habit tracker + study diary: daily checklist, weekly/monthly heatmap, mood
 * tracking, private diary, streak with the rest-day rule, weekly summary.
 */
class HabitsViewModel(private val repository: HabitRepository) : ViewModel() {

    data class HabitRow(val habit: Habit, val amountToday: Int, val done: Boolean)

    data class HabitsState(
        val loading: Boolean = true,
        val habits: List<HabitRow> = emptyList(),
        val streak: StreakInfo? = null,
        val heatmap: Map<String, Int> = emptyMap(),
        val diary: List<DiaryEntry> = emptyList(),
        val todayLogs: List<HabitLog> = emptyList(),
        val weeklySummary: String = "",
        val aiSummary: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(HabitsState())
    val state: StateFlow<HabitsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val todayLogs = repository.logsForDate(today)
            val habits = repository.habitsOnce().map { habit ->
                val amount = todayLogs.firstOrNull { it.habitId == habit.id }?.amount ?: 0
                HabitRow(habit, amount, amount >= habit.targetPerDay)
            }
            val streak = repository.streak(today)

            // Heatmap: last 35 days with total habit amounts per day.
            val from = today.minusDays(34)
            val logs = repository.logsBetween(from, today)
            val heatmap = logs.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }

            val weekLogs = repository.logsBetween(today.minusDays(6), today)
            val summary = buildString {
                append("Last 7 days: ")
                val activeDays = weekLogs.filter { it.amount > 0 }.map { it.date }.distinct().size
                append("$activeDays active day(s), ")
                append("${weekLogs.sumOf { it.amount }} total habit units.")
                streak.restDayMessage?.let { append(" ").append(it) }
            }

            _state.value = HabitsState(
                loading = false,
                habits = habits,
                streak = streak,
                heatmap = heatmap,
                diary = repository.recentDiary(30),
                todayLogs = todayLogs,
                weeklySummary = summary
            )
        }
    }

    fun addHabit(name: String, target: Int, kind: HabitKind) {
        viewModelScope.launch {
            repository.addHabit(name, target, kind)
            load()
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
            load()
        }
    }

    fun logHabit(habitId: String, delta: Int) {
        viewModelScope.launch {
            repository.logAmount(habitId, LocalDate.now(), delta)
            load()
            // Contextual encouragement when all daily goals are complete.
            val rows = _state.value.habits
            if (rows.isNotEmpty() && rows.all { it.done }) {
                com.medtech.reviewhub.ui.motivation.EncouragementBus.trigger(
                    com.medtech.reviewhub.domain.ContextKind.AFTER_GOALS
                )
            }
        }
    }

    fun addDiary(mood: Mood, text: String, tags: List<String>) {
        viewModelScope.launch {
            repository.addDiary(LocalDate.now(), mood, text, tags)
            load()
        }
    }

    fun deleteDiary(entry: DiaryEntry) {
        viewModelScope.launch {
            repository.deleteDiary(entry)
            load()
        }
    }

    /** Optional AI diary summary — the caller supplies the generated text. */
    fun setAiSummary(text: String) {
        _state.value = _state.value.copy(aiSummary = text)
    }

    fun recentDiaryText(): String = _state.value.diary.take(7).joinToString("\n\n") { "${it.date} [${it.mood.name}]: ${it.text}" }
}
