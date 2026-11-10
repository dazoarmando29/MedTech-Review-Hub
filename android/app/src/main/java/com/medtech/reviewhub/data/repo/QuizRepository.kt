package com.medtech.reviewhub.data.repo

import com.medtech.reviewhub.data.db.QuizAttemptEntity
import com.medtech.reviewhub.data.db.QuizDao
import com.medtech.reviewhub.domain.QuizScoring
import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.domain.model.QuizResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Quiz persistence + question-bank access. The question bank comes from the
 * bundled content JSON (works fully offline).
 */
class QuizRepository(
    private val quizDao: QuizDao,
    private val flashcards: FlashcardRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAttempts(): Flow<List<QuizAttemptEntity>> = quizDao.all()

    suspend fun recentAttempts(limit: Int = 20): List<QuizAttemptEntity> = quizDao.recentFinished(limit)

    suspend fun unfinishedAttempt(): QuizAttemptEntity? = quizDao.unfinished()

    suspend fun attempt(id: String): QuizAttemptEntity? = quizDao.byId(id)

    suspend fun startAttempt(
        mode: String,
        questionIds: List<String>,
        subjectId: String? = null,
        topicId: String? = null,
        difficultyBand: String? = null
    ): QuizAttemptEntity {
        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            mode = mode,
            startedAtMs = System.currentTimeMillis(),
            finishedAtMs = null,
            questionIdsCsv = questionIds.joinToString(","),
            answersJson = "{}",
            incorrectIdsCsv = "",
            correct = 0,
            total = questionIds.size,
            timeSeconds = 0,
            subjectId = subjectId,
            topicId = topicId,
            difficultyBand = difficultyBand
        )
        quizDao.upsert(attempt)
        return attempt
    }

    suspend fun saveAnswers(attemptId: String, answers: Map<String, String>) {
        val a = quizDao.byId(attemptId) ?: return
        quizDao.upsert(a.copy(answersJson = encodeAnswers(answers)))
    }

    suspend fun finishAttempt(
        attemptId: String,
        answers: Map<String, String>,
        questions: List<QuizQuestion>,
        timeSeconds: Int,
        result: QuizResult
    ) {
        val a = quizDao.byId(attemptId) ?: return
        quizDao.upsert(
            a.copy(
                finishedAtMs = System.currentTimeMillis(),
                answersJson = encodeAnswers(answers),
                incorrectIdsCsv = result.incorrectQuestionIds.joinToString(","),
                correct = result.correct,
                total = result.total,
                timeSeconds = timeSeconds
            )
        )
    }

    /** Creates flashcards from missed questions (source.kind = QUIZ_MISS). */
    suspend fun generateCardsForMissed(questions: List<QuizQuestion>, answers: Map<String, String>): Int {
        var count = 0
        for (q in questions) {
            val chosen = answers[q.id] ?: continue
            if (chosen == q.correctChoiceId) continue
            val correct = q.choices.firstOrNull { it.id == q.correctChoiceId }?.text ?: q.correctChoiceId
            flashcards.createFromMissedQuestion(
                questionId = q.id,
                stem = q.stem,
                answer = correct,
                subjectId = q.subjectId,
                topicId = q.topicId,
                explanation = q.explanation
            )
            count++
        }
        return count
    }

    /** Question ids answered incorrectly in past finished attempts. */
    suspend fun previouslyIncorrectIds(): Set<String> =
        quizDao.recentFinished(50)
            .flatMap { it.incorrectIdsCsv.split(',').filter { id -> id.isNotBlank() } }
            .toSet()

    suspend fun answersFor(attempt: QuizAttemptEntity): Map<String, String> = decodeAnswers(attempt.answersJson)

    private fun encodeAnswers(answers: Map<String, String>): String =
        answers.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeAnswers(raw: String): Map<String, String> =
        if (raw.isBlank() || raw == "{}") emptyMap()
        else raw.split(';')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .toMap()

    suspend fun questionsAnsweredToday(todayStartMs: Long): Int =
        quizDao.recent(200).filter { it.startedAtMs >= todayStartMs }
            .sumOf { decodeAnswers(it.answersJson).size }

    /** Reconstructed QuizResult of the last finished attempts for dashboard stats. */
    suspend fun recentAccuracy(lastN: Int = 5): Double? {
        val attempts = quizDao.recentFinished(lastN)
        val total = attempts.sumOf { it.total }
        if (total == 0) return null
        return attempts.sumOf { it.correct }.toDouble() / total
    }

    companion object {
        fun computeResult(
            questions: List<QuizQuestion>,
            answers: Map<String, String>,
            timeSeconds: Int
        ): QuizResult {
            val answered = questions.mapNotNull { q ->
                answers[q.id]?.let { QuizScoring.Answer(q, it) }
            }
            return QuizScoring.score(answered, timeSeconds)
        }
    }
}
