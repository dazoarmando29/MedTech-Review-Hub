package com.medtech.reviewhub.ui.quiz

import com.medtech.reviewhub.domain.QuizScoring
import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.domain.model.QuizResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How questions were selected. */
enum class QuizMode(val label: String) {
    SUBJECT("Subject"),
    TOPIC("Topic"),
    RANDOM("Random"),
    WEAK_AREAS("Weak areas"),
    PREVIOUSLY_INCORRECT("Previously incorrect"),
    SAVED("Saved questions"),
    MIXED("Mixed"),
    MOCK_EXAM("Full mock exam (100Q / 150 min)"),
    ADAPTIVE("Adaptive practice (self-adjusting difficulty)")
}

/** Feedback timing. */
enum class QuizStyle { STUDY, EXAM }

/** Persistence hooks (Room-backed in the app; fakes in unit tests). */
interface QuizPersistence {
    suspend fun onQuizStart(questionIds: List<String>, mode: String) {}
    suspend fun onQuizFinish(result: QuizResult, answers: Map<String, String>, questions: List<QuizQuestion>, timeSeconds: Int) {}
    suspend fun onMissed(questions: List<QuizQuestion>, answers: Map<String, String>): Int = 0

    companion object {
        val NONE = object : QuizPersistence {}
    }
}

/**
 * Quiz session state machine — Compose-free and unit-testable.
 *
 * Study mode: instant feedback + full rationales + "Why was I wrong?" (the
 * misconception field). Exam mode: no feedback until finish. Adaptive mode:
 * difficulty selection per [QuizScoring.ADAPTIVE_DISCLAIMER] (NOT ASCP CAT).
 */
class QuizViewModel(
    private val questionBank: List<QuizQuestion>,
    private val persistence: QuizPersistence = QuizPersistence.NONE
) {

    data class Feedback(
        val correct: Boolean,
        val correctChoiceId: String,
        val chosenId: String,
        val explanation: String,
        val rationaleChosen: String,
        val rationaleCorrect: String,
        val misconception: String
    )

    data class QuizState(
        val mode: QuizMode = QuizMode.RANDOM,
        val style: QuizStyle = QuizStyle.STUDY,
        val questions: List<QuizQuestion> = emptyList(),
        val index: Int = 0,
        val answers: Map<String, String> = emptyMap(),
        val feedback: Feedback? = null,
        val finished: Boolean = false,
        val result: QuizResult? = null,
        val elapsedSeconds: Int = 0,
        val timeLimitSeconds: Int? = null,
        val adaptive: QuizScoring.AdaptiveState = QuizScoring.AdaptiveState(),
        val startedAtMs: Long = 0L
    )

    private val _state = MutableStateFlow(QuizState())
    val state: StateFlow<QuizState> = _state.asStateFlow()

    private var questionStartMs: Long = System.currentTimeMillis()

    val current: QuizQuestion?
        get() = _state.value.questions.getOrNull(_state.value.index)

    /**
     * Starts a quiz. [questions] must be non-empty (callers pick by mode from
     * the content question bank). Mock exam: 100 questions / 150 minutes.
     */
    suspend fun start(
        questions: List<QuizQuestion>,
        mode: QuizMode,
        style: QuizStyle,
        nowMs: Long = System.currentTimeMillis()
    ) {
        require(questions.isNotEmpty()) { "questions must not be empty" }
        val limit = when (mode) {
            QuizMode.MOCK_EXAM -> 150 * 60
            else -> null
        }
        _state.value = QuizState(
            mode = mode, style = style, questions = questions, index = 0,
            timeLimitSeconds = limit, startedAtMs = nowMs,
            adaptive = QuizScoring.AdaptiveState(currentBand = initialBand(style))
        )
        questionStartMs = nowMs
        persistence.onQuizStart(questions.map { it.id }, mode.name)
    }

    private fun initialBand(style: QuizStyle): String = "MEDIUM"

    /** Records an answer. In Study mode returns feedback immediately. */
    fun answer(choiceId: String, nowMs: Long = System.currentTimeMillis()): Feedback? {
        val s = _state.value
        val q = s.questions.getOrNull(s.index) ?: return null
        if (s.finished || s.answers.containsKey(q.id)) return s.feedback
        val correct = choiceId == q.correctChoiceId
        val feedback = Feedback(
            correct = correct,
            correctChoiceId = q.correctChoiceId,
            chosenId = choiceId,
            explanation = q.explanation,
            rationaleChosen = q.rationales[choiceId] ?: "",
            rationaleCorrect = q.rationales[q.correctChoiceId] ?: "",
            misconception = q.misconception
        )
        _state.update {
            it.copy(
                answers = it.answers + (q.id to choiceId),
                feedback = if (it.style == QuizStyle.STUDY) feedback else null,
                adaptive = if (it.mode == QuizMode.ADAPTIVE) QuizScoring.recordAnswer(
                    it.adaptive.copy(currentBand = QuizScoring.nextBand(it.adaptive)),
                    q.difficulty.uppercase(),
                    correct
                ) else it.adaptive
            )
        }
        return feedback
    }

    /** Moves to the next question; returns false at the end. */
    fun next(nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = _state.value
        if (s.index + 1 >= s.questions.size) return false
        questionStartMs = nowMs
        _state.update { it.copy(index = it.index + 1, feedback = null) }
        return true
    }

    fun previous(): Boolean {
        val s = _state.value
        if (s.index == 0) return false
        _state.update { it.copy(index = it.index - 1, feedback = null) }
        return true
    }

    /** Advances elapsed time; also enforces the mock-exam time limit. */
    fun markTime(nowMs: Long = System.currentTimeMillis()) {
        val s = _state.value
        val elapsed = ((nowMs - s.startedAtMs) / 1000L).toInt().coerceAtLeast(0)
        _state.update { it.copy(elapsedSeconds = elapsed) }
    }

    fun timeExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val limit = _state.value.timeLimitSeconds ?: return false
        markTime(nowMs)
        return _state.value.elapsedSeconds >= limit
    }

    /** Computes results, persists them and generates cards from missed questions. */
    suspend fun finish(generateCardsForMissed: Boolean = true): QuizResult {
        val s = _state.value
        val result = QuizScoring.score(
            s.questions.mapNotNull { q -> s.answers[q.id]?.let { QuizScoring.Answer(q, it) } },
            s.elapsedSeconds
        )
        _state.update { it.copy(finished = true, result = result) }
        persistence.onQuizFinish(result, s.answers, s.questions, s.elapsedSeconds)
        if (generateCardsForMissed) {
            val missed = s.questions.filter { s.answers[it.id] != null && s.answers[it.id] != it.correctChoiceId }
            persistence.onMissed(missed, s.answers)
        }
        return result
    }

    /** Reveal feedback in exam review after finishing ("Why was I wrong?"). */
    fun feedbackFor(questionId: String): Feedback? {
        val s = _state.value
        val q = s.questions.firstOrNull { it.id == questionId } ?: return null
        val chosen = s.answers[questionId] ?: return null
        return Feedback(
            correct = chosen == q.correctChoiceId,
            correctChoiceId = q.correctChoiceId,
            chosenId = chosen,
            explanation = q.explanation,
            rationaleChosen = q.rationales[chosen] ?: "",
            rationaleCorrect = q.rationales[q.correctChoiceId] ?: "",
            misconception = q.misconception
        )
    }

    companion object {
        const val MOCK_EXAM_QUESTIONS = 100
        const val MOCK_EXAM_MINUTES = 150

        /**
         * Question selection per mode from the content question bank.
         * [weakTopicIds] and [incorrectIds] come from local history; [savedIds]
         * from bookmarked questions.
         */
        fun select(
            bank: List<QuizQuestion>,
            mode: QuizMode,
            subjectId: String? = null,
            topicId: String? = null,
            weakTopicIds: Set<String> = emptySet(),
            incorrectIds: Set<String> = emptySet(),
            savedIds: Set<String> = emptySet(),
            limit: Int = 20
        ): List<QuizQuestion> {
            val picked = when (mode) {
                QuizMode.SUBJECT -> bank.filter { it.subjectId == subjectId }
                QuizMode.TOPIC -> bank.filter { it.topicId == topicId }
                QuizMode.WEAK_AREAS -> bank.filter { it.topicId in weakTopicIds || it.subjectId in weakTopicIds }
                QuizMode.PREVIOUSLY_INCORRECT -> bank.filter { it.id in incorrectIds }
                QuizMode.SAVED -> bank.filter { it.id in savedIds }
                QuizMode.MOCK_EXAM -> bank.shuffled(java.util.Random(0)).take(MOCK_EXAM_QUESTIONS)
                QuizMode.ADAPTIVE, QuizMode.MIXED, QuizMode.RANDOM -> bank.shuffled()
            }
            val count = if (mode == QuizMode.MOCK_EXAM) MOCK_EXAM_QUESTIONS else limit
            return picked.take(count)
        }
    }
}
