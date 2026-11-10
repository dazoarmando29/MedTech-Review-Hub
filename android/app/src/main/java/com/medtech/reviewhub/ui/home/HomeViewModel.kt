package com.medtech.reviewhub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.content.ContentAssetsLoader
import com.medtech.reviewhub.data.prefs.AppSettings
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.data.repo.FlashcardRepository
import com.medtech.reviewhub.data.repo.HabitRepository
import com.medtech.reviewhub.data.repo.QuizRepository
import com.medtech.reviewhub.data.repo.StudyRepository
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.MascotMood
import com.medtech.reviewhub.domain.MascotLines
import com.medtech.reviewhub.domain.model.PlanItem
import com.medtech.reviewhub.domain.model.StreakInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home dashboard: today's goals checklist, streak, totals, flashcards due,
 * questions today, recent quiz accuracy, weakest subject, exam countdown,
 * weekly minutes calendar, "Continue reading", "Resume quiz", suggested topic,
 * mascot greeting.
 *
 * Personal touch (shared/DESIGN.md v2 "Voice & copy"): a PH-timezone (Asia/
 * Manila) greeting with the user's display name, a MotivationCard line rotated
 * deterministically by day-of-year, and an optional gift-note card.
 */
class HomeViewModel(
    private val content: ContentAssetsLoader,
    private val studyRepository: StudyRepository,
    private val flashcardRepository: FlashcardRepository,
    private val quizRepository: QuizRepository,
    private val habitRepository: HabitRepository,
    private val settingsRepository: SettingsRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    companion object {
        /** Greeting and motivation copy rotate on Philippine time. */
        val PH_MANILA: ZoneId = ZoneId.of("Asia/Manila")
    }

    data class Dashboard(
        val loading: Boolean = true,
        val displayName: String = AppSettings.DEFAULT_DISPLAY_NAME,
        val hourOfDay: Int = 12,
        val dayOfYear: Int = 1,
        val giftNote: String = "",
        val mascotName: String = AppSettings.DEFAULT_MASCOT_NAME,
        val mascotMood: MascotMood = MascotMood.HAPPY,
        val mascotKind: MascotKind = MascotKind.CAT,
        val mascotLine: String = "",
        val streakDays: Int = 0,
        val streakInfo: StreakInfo? = null,
        val totalMinutes: Int = 0,
        val minutesToday: Int = 0,
        val flashcardsDue: Int = 0,
        val questionsAnsweredToday: Int = 0,
        val recentQuizAccuracy: Double? = null,
        val weakestSubject: Pair<String, String>? = null,
        val examCountdownDays: Int? = null,
        val weeklyMinutes: List<Pair<String, Int>> = emptyList(),
        val todayItems: List<Pair<PlanItem, Boolean>> = emptyList(),
        val continueLessonId: String? = null,
        val continueLessonTitle: String = "",
        val resumeQuiz: Boolean = false,
        val suggestedTopic: Pair<String, String>? = null,
        val settings: AppSettings = AppSettings()
    )

    private val _dashboard = MutableStateFlow(Dashboard())
    val dashboard: StateFlow<Dashboard> = _dashboard.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val now = nowProvider()
            val today = LocalDate.now()
            val todayStart = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

            content.load()
            val plan = runCatching {
                studyRepository.planOrGenerate(
                    subjectTopics = content.subjects().associate { s -> s.id to s.topics },
                    profile = settingsRepository.currentProfile()
                )
            }.getOrNull()
            val items = plan?.let { studyRepository.todayItems(today) }.orEmpty()

            val counts = flashcardRepository.counts(now)
            val streak = habitRepository.streak(today)
            val minutesToday = studyRepository.minutesBetween(todayStart, now + 1)
            val totalMinutes = studyRepository.totalMinutes()
            val questionsToday = quizRepository.questionsAnsweredToday(todayStart)
            val accuracy = quizRepository.recentAccuracy(5)

            val weekly = (6L downTo 0L).map { offset ->
                val day = today.minusDays(offset)
                val from = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val to = day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                day.toString() to studyRepository.minutesBetween(from, to)
            }

            val profile = settingsRepository.currentProfile()
            val countdown = profile.targetExamDate?.let { date ->
                runCatching {
                    val target = LocalDate.parse(date)
                    (target.toEpochDay() - today.toEpochDay()).toInt()
                }.getOrNull()
            }

            val weakest = profile.weakestSubjects.firstOrNull()?.let { id ->
                id to content.subjectName(id)
            }

            val continueLesson = studyRepository.lastLessonWithProgress()
            val continueTitle = continueLesson?.let { content.lesson(it)?.title } ?: ""
            val resumeQuiz = quizRepository.unfinishedAttempt() != null
            val suggested = content.topics(weakest?.first ?: content.subjects().firstOrNull()?.id ?: "")
                .firstOrNull()?.let { it.id to it.name }

            // Greeting + motivation rotate on Philippine time (Asia/Manila).
            val phNow = java.time.ZonedDateTime.now(PH_MANILA)
            val mood = when {
                streak.current >= 7 -> MascotMood.CELEBRATING
                streak.restDayMessage != null -> MascotMood.ENCOURAGING
                items.isNotEmpty() -> MascotMood.HAPPY
                else -> MascotMood.STUDYING
            }
            _dashboard.update {
                Dashboard(
                    loading = false,
                    displayName = settings.displayName,
                    hourOfDay = phNow.hour,
                    dayOfYear = phNow.dayOfYear,
                    giftNote = settings.giftNote,
                    mascotName = settings.mascotName,
                    mascotMood = mood,
                    mascotKind = settings.mascotKind,
                    mascotLine = if (settings.muteMascot || settings.seriousMode) "" else
                        MascotLines.line(settings.mascotKind, mood, seed = phNow.dayOfYear.toLong()),
                    streakDays = streak.current,
                    streakInfo = streak,
                    totalMinutes = totalMinutes,
                    minutesToday = minutesToday,
                    flashcardsDue = counts.first + counts.second + counts.third,
                    questionsAnsweredToday = questionsToday,
                    recentQuizAccuracy = accuracy,
                    weakestSubject = weakest,
                    examCountdownDays = countdown,
                    weeklyMinutes = weekly,
                    todayItems = items.map { it to it.completed },
                    continueLessonId = continueLesson,
                    continueLessonTitle = continueTitle,
                    resumeQuiz = resumeQuiz,
                    suggestedTopic = suggested,
                    settings = settings
                )
            }
        }
    }

    fun togglePlanItem(item: PlanItem, completed: Boolean) {
        viewModelScope.launch {
            studyRepository.setItemCompleted(LocalDate.now().toString(), item.id, completed)
            load()
        }
    }
}
