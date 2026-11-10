package io.medtech.backend.service

import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.BySubjectStat
import io.medtech.backend.domain.QuizAnswerFeedback
import io.medtech.backend.domain.QuizAnswerRequest
import io.medtech.backend.domain.QuizAttemptSummary
import io.medtech.backend.domain.QuizAttemptView
import io.medtech.backend.domain.QuizMode
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.QuizResult
import io.medtech.backend.domain.QuizStartRequest
import io.medtech.backend.domain.QuizStatus
import io.medtech.backend.domain.ReportRequest
import io.medtech.backend.domain.ReportResponse
import io.medtech.backend.domain.WeakTopic
import io.medtech.backend.domain.encodeJson
import io.medtech.backend.repository.AnalyticsRepository
import io.medtech.backend.repository.ContentRepository
import io.medtech.backend.repository.QuizRepository
import io.medtech.backend.security.Sanitizer
import kotlin.math.round

class QuizService(
    private val quizzes: QuizRepository = QuizRepository(),
    private val contentService: ContentService,
    private val contentRepo: ContentRepository = ContentRepository(),
    private val analytics: AnalyticsRepository = AnalyticsRepository(),
) {

    suspend fun start(userId: String, request: QuizStartRequest): QuizAttemptView {
        val all = contentService.allQuestions()
        val selectedIds: List<String> = when {
            !request.questionIds.isNullOrEmpty() -> {
                val wanted = request.questionIds!!.toSet()
                all.filter { it.id in wanted }.map { it.id }
            }
            else -> {
                var pool = all
                if (!request.subjectIds.isNullOrEmpty()) pool = pool.filter { it.subjectId in request.subjectIds!! }
                if (!request.topicIds.isNullOrEmpty()) pool = pool.filter { it.topicId in request.topicIds!! }
                if (request.mode == QuizMode.ADAPTIVE) {
                    // Documented difficulty selection: interleave EASY/MEDIUM/HARD (this is not ASCP CAT).
                    val count = request.questionCount ?: 10
                    val easy = pool.filter { it.difficulty.name == "EASY" }
                    val medium = pool.filter { it.difficulty.name == "MEDIUM" }
                    val hard = pool.filter { it.difficulty.name == "HARD" }
                    val picked = mutableListOf<QuizQuestion>()
                    var i = 0
                    while (picked.size < count && i < count * 3) {
                        val tier = listOf(easy, medium, hard)[i % 3]
                        val candidate = tier.getOrNull(i / 3)
                        if (candidate != null) picked += candidate
                        i++
                    }
                    picked.ifEmpty { pool.shuffled().take(count) }.map { it.id }
                } else {
                    pool.shuffled().take(request.questionCount ?: 10).map { it.id }
                }
            }
        }
        if (selectedIds.isEmpty()) throw ApiException.validation("No questions matched the requested filters")
        val attemptId = quizzes.createAttempt(userId, request.mode, selectedIds)
        val row = quizzes.attemptRow(userId, attemptId)!!
        return viewFor(userId, row, includeResult = false)
    }

    suspend fun answer(userId: String, attemptId: String, request: QuizAnswerRequest): QuizAnswerFeedback {
        val row = quizzes.attemptRow(userId, attemptId) ?: throw ApiException.notFound("Quiz attempt not found")
        if (row.status == QuizStatus.COMPLETED) throw ApiException.validation("Quiz attempt is already finished")
        if (request.questionId !in row.questionIds) throw ApiException.validation("Question is not part of this quiz attempt")
        val question = contentService.allQuestions().find { it.id == request.questionId }
            ?: throw ApiException.notFound("Question not found")
        if (question.choices.none { it.id == request.choiceId }) {
            throw ApiException.validation("choiceId is not one of the question choices")
        }
        val isCorrect = question.correctChoiceId == request.choiceId
        quizzes.insertAnswer(userId, attemptId, request.questionId, request.choiceId, isCorrect)
        analytics.recordAnswers(userId, question.subjectId, question.topicId, if (isCorrect) 1 else 0, 1)
        return if (row.mode == QuizMode.EXAM) {
            QuizAnswerFeedback(accepted = true)
        } else {
            QuizAnswerFeedback(
                accepted = true,
                correct = isCorrect,
                correctChoiceId = question.correctChoiceId,
                explanation = question.explanation,
                rationales = question.rationales,
                misconception = question.misconception,
            )
        }
    }

    suspend fun finish(userId: String, attemptId: String): QuizResult {
        val row = quizzes.attemptRow(userId, attemptId) ?: throw ApiException.notFound("Quiz attempt not found")
        if (row.status == QuizStatus.COMPLETED) {
            row.resultJson?.let { return io.medtech.backend.domain.decodeJson(it) }
            throw ApiException.validation("Quiz attempt has no stored result")
        }
        val questionById = contentService.allQuestions().associateBy { it.id }
        val answers = quizzes.answers(userId, attemptId).associateBy { it.questionId }
        val questions = row.questionIds.mapNotNull { questionById[it] }

        var correct = 0
        val bySubject = mutableMapOf<String, Pair<Int, Int>>()
        val byTopic = mutableMapOf<String, Pair<Int, Int>>()
        val incorrect = mutableListOf<String>()
        for (question in questions) {
            val answer = answers[question.id]
            val isCorrect = answer != null && answer.isCorrect
            if (isCorrect) correct++ else incorrect += question.id
            val (c, t) = bySubject[question.subjectId] ?: (0 to 0)
            bySubject[question.subjectId] = (if (isCorrect) c + 1 else c) to (t + 1)
            val (tc, tt) = byTopic[question.topicId] ?: (0 to 0)
            byTopic[question.topicId] = (if (isCorrect) tc + 1 else tc) to (tt + 1)
        }
        val total = questions.size
        val score = if (total > 0) round(correct.toDouble() / total * 100) / 100 else 0.0
        val timeSeconds = ((nowMillis() - row.startedAtMillis) / 1000).coerceAtLeast(0)
        val result = QuizResult(
            score = score,
            correct = correct,
            total = total,
            timeSeconds = timeSeconds,
            bySubject = bySubject.map { (subject, counts) -> BySubjectStat(subject, counts.first, counts.second) }
                .sortedBy { it.subjectId },
            weakTopics = byTopic.map { (topic, counts) ->
                WeakTopic(topicId = topic, accuracy = if (counts.second > 0) counts.first.toDouble() / counts.second else 0.0)
            }.filter { it.accuracy < 0.6 }.sortedBy { it.accuracy },
            incorrectQuestionIds = incorrect,
        )
        quizzes.finishAttempt(userId, attemptId, result)
        return result
    }

    suspend fun get(userId: String, attemptId: String): QuizAttemptView {
        val row = quizzes.attemptRow(userId, attemptId) ?: throw ApiException.notFound("Quiz attempt not found")
        return viewFor(userId, row, includeResult = true)
    }

    suspend fun list(userId: String, status: QuizStatus?): List<QuizAttemptSummary> =
        quizzes.listAttempts(userId, status).map {
            QuizAttemptSummary(
                id = it.id,
                mode = it.mode,
                status = it.status,
                startedAt = it.startedAtMillis.toInstant(),
                finishedAt = it.finishedAtMillis?.toInstant(),
                score = it.resultJson?.let { json -> runCatching { io.medtech.backend.domain.decodeJson<QuizResult>(json) }.getOrNull()?.score },
                correct = it.correctCount,
                total = it.totalCount,
            )
        }

    suspend fun report(userId: String, questionId: String, request: ReportRequest): ReportResponse {
        val exists = contentService.allQuestions().any { it.id == questionId }
        if (!exists) throw ApiException.notFound("Question not found")
        val id = contentRepo.insertReport(
            userId = userId,
            questionId = questionId,
            reason = Sanitizer.cleanToken(request.reason, 64),
            details = Sanitizer.clean(request.details, 4000),
        )
        return ReportResponse(id = id, status = "OPEN")
    }

    private suspend fun viewFor(userId: String, row: QuizRepository.AttemptRow, includeResult: Boolean): QuizAttemptView {
        val questionById = contentService.allQuestions().associateBy { it.id }
        val questions = row.questionIds.mapNotNull { questionById[it] }
        val completed = row.status == QuizStatus.COMPLETED
        val maskAnswers = row.mode == QuizMode.EXAM && !completed
        val served = questions.map { q ->
            if (maskAnswers) {
                q.copy(correctChoiceId = null, explanation = null, rationales = null, misconception = null)
            } else {
                q
            }
        }
        val result = if (includeResult && completed) {
            row.resultJson?.let { runCatching { io.medtech.backend.domain.decodeJson<QuizResult>(it) }.getOrNull() }
        } else null
        return QuizAttemptView(
            id = row.id,
            mode = row.mode,
            status = row.status,
            startedAt = row.startedAtMillis.toInstant(),
            finishedAt = row.finishedAtMillis?.toInstant(),
            questions = served,
            answeredCount = quizzes.answers(userId, row.id).size,
            totalQuestions = row.questionIds.size,
            result = result,
        )
    }
}
