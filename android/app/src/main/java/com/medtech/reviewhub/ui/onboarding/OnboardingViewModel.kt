package com.medtech.reviewhub.ui.onboarding

import com.medtech.reviewhub.domain.StudyPlanGenerator
import com.medtech.reviewhub.domain.model.ExamTarget
import com.medtech.reviewhub.domain.model.ExplanationStyle
import com.medtech.reviewhub.domain.model.KnowledgeLevel
import com.medtech.reviewhub.domain.model.StudyPlan
import com.medtech.reviewhub.domain.model.Topic
import com.medtech.reviewhub.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Onboarding wizard state machine. Compose-free and unit-testable: the screen
 * renders [OnboardingState] and calls the methods below. Persistence happens
 * through the injected [onFinish] callback (Room/DataStore in the app, a fake
 * in tests); subject/topic data comes from the suspend [topicProvider].
 */
class OnboardingViewModel(
    private val topicProvider: suspend () -> Map<String, List<Topic>> = { emptyMap() },
    private val onFinish: suspend (UserProfile, StudyPlan) -> Unit = { _, _ -> }
) {

    enum class Step { EXAM, DATE, LEVEL, SUBJECTS, TIME, SCHEDULE, PREFERENCES, STYLE, PERMISSIONS }

    data class OnboardingState(
        val step: Step = Step.EXAM,
        val examTarget: ExamTarget = ExamTarget.MLS_ASCPI,
        val targetExamDate: String? = null,
        val knowledgeLevel: KnowledgeLevel = KnowledgeLevel.BEGINNER,
        val strongSubjects: Set<String> = emptySet(),
        val weakSubjects: Set<String> = emptySet(),
        val minutesPerDay: Int = 60,
        val schedule: Map<String, List<List<Int>>> = emptyMap(),
        val learningPrefs: Set<String> = emptySet(),
        val explanationStyle: ExplanationStyle = ExplanationStyle.FORMAL_ENGLISH,
        val notificationsEnabled: Boolean = true,
        val focusFeatureEnabled: Boolean = true,
        val error: String? = null,
        val completed: Boolean = false
    )

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    val allSubjectIds: List<String> =
        StudyPlanGenerator.weightsFor(ExamTarget.MLS_ASCPI).keys.toList()

    fun selectExam(target: ExamTarget) = _state.update { it.copy(examTarget = target, error = null) }

    fun setTargetDate(date: String?) = _state.update { it.copy(targetExamDate = date, error = null) }

    fun setLevel(level: KnowledgeLevel) = _state.update { it.copy(knowledgeLevel = level, error = null) }

    fun toggleStrong(subjectId: String) = _state.update {
        it.copy(
            strongSubjects = if (subjectId in it.strongSubjects) it.strongSubjects - subjectId else it.strongSubjects + subjectId,
            weakSubjects = it.weakSubjects - subjectId,
            error = null
        )
    }

    fun toggleWeak(subjectId: String) = _state.update {
        it.copy(
            weakSubjects = if (subjectId in it.weakSubjects) it.weakSubjects - subjectId else it.weakSubjects + subjectId,
            strongSubjects = it.strongSubjects - subjectId,
            error = null
        )
    }

    fun setMinutesPerDay(minutes: Int) = _state.update { it.copy(minutesPerDay = minutes.coerceIn(10, 600), error = null) }

    fun setScheduleWindow(day: String, startMinute: Int, endMinute: Int) = _state.update {
        val windows = (it.schedule[day].orEmpty() + listOf(listOf(startMinute, endMinute)))
            .sortedBy { w -> w.first() }
        it.copy(schedule = it.schedule + (day to windows), error = null)
    }

    fun clearSchedule(day: String) = _state.update { it.copy(schedule = it.schedule - day, error = null) }

    fun toggleLearningPref(pref: String) = _state.update {
        it.copy(learningPrefs = if (pref in it.learningPrefs) it.learningPrefs - pref else it.learningPrefs + pref, error = null)
    }

    fun setExplanationStyle(style: ExplanationStyle) = _state.update { it.copy(explanationStyle = style, error = null) }

    fun setNotifications(enabled: Boolean) = _state.update { it.copy(notificationsEnabled = enabled) }
    fun setFocusFeature(enabled: Boolean) = _state.update { it.copy(focusFeatureEnabled = enabled) }

    /** Validates the current step; returns null when valid. */
    fun validate(step: Step = _state.value.step): String? {
        val s = _state.value
        return when (step) {
            Step.EXAM -> if (s.examTarget == ExamTarget.CUSTOM && s.targetExamDate == null) null else null
            Step.DATE -> {
                if (s.targetExamDate != null && !s.targetExamDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    "Please use the date format YYYY-MM-DD."
                } else null
            }
            Step.LEVEL -> null
            Step.SUBJECTS -> {
                if (s.weakSubjects.isEmpty() && s.strongSubjects.isEmpty()) {
                    "Pick at least one subject you find strong or weak (or skip both)."
                } else if (s.weakSubjects.intersect(s.strongSubjects).isNotEmpty()) {
                    "A subject cannot be both strong and weak."
                } else null
            }
            Step.TIME -> if (s.minutesPerDay < 10) "Choose at least 10 minutes per day." else null
            Step.SCHEDULE, Step.PREFERENCES, Step.STYLE, Step.PERMISSIONS -> null
        }
    }

    /** Advances one step. Returns false when already on the last step. */
    fun nextStep(): Boolean {
        val error = validate()
        if (error != null) {
            _state.update { it.copy(error = error) }
            return true
        }
        val steps = Step.entries
        val idx = steps.indexOf(_state.value.step)
        return if (idx < steps.lastIndex) {
            _state.update { it.copy(step = steps[idx + 1], error = null) }
            true
        } else false
    }

    fun prevStep(): Boolean {
        val steps = Step.entries
        val idx = steps.indexOf(_state.value.step)
        return if (idx > 0) {
            _state.update { it.copy(step = steps[idx - 1], error = null) }
            true
        } else false
    }

    fun buildProfile(): UserProfile {
        val s = _state.value
        return UserProfile(
            examTarget = s.examTarget,
            targetExamDate = s.targetExamDate,
            knowledgeLevel = s.knowledgeLevel,
            strongestSubjects = s.strongSubjects.toList(),
            weakestSubjects = s.weakSubjects.toList(),
            minutesPerDay = s.minutesPerDay,
            preferredSchedule = s.schedule,
            learningPreferences = s.learningPrefs.toList(),
            explanationStyle = s.explanationStyle,
            notificationsEnabled = s.notificationsEnabled,
            focusFeatureEnabled = s.focusFeatureEnabled,
            onboardingCompleted = true
        )
    }

    /** Builds the profile + local study plan (StudyPlanGenerator rules). */
    suspend fun generate(): Pair<UserProfile, StudyPlan> {
        val profile = buildProfile()
        val plan = StudyPlanGenerator.generate(profile, topicProvider())
        return profile to plan
    }

    /** Generates and persists; marks onboarding completed. */
    suspend fun finish(): StudyPlan {
        val (profile, plan) = generate()
        onFinish(profile, plan)
        _state.update { it.copy(completed = true) }
        return plan
    }
}
