package io.medtech.backend

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.QuizAnswerFeedback
import io.medtech.backend.domain.QuizAnswerRequest
import io.medtech.backend.domain.QuizAttemptView
import io.medtech.backend.domain.QuizMode
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.QuizResult
import io.medtech.backend.domain.QuizStartRequest
import io.medtech.backend.domain.QuizStatus
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuizTest {

    /**
     * Picks real questions from the served content library so the test works
     * with both the bundled sample and the full shared/content library.
     */
    private suspend fun io.ktor.client.HttpClient.loadQuestionFixture(): QuestionFixture {
        val resp = get("/api/v1/content/questions")
        val all: List<QuizQuestion> = decodeJson(resp.text())
        assertTrue(all.size >= 4, "question bank must have at least 4 questions")

        // Two questions sharing one topic (for weakTopics accuracy 0.5)
        val sameTopic = all.groupBy { it.topicId }.values.first { it.size >= 2 }
        val (t1, t2) = sameTopic[0] to sameTopic[1]

        // Two more from a different subject (for bySubject breakdown)
        val otherSubject = all.first { it.subjectId != t1.subjectId }
        val otherSubjectSecond = all.first { it.id != otherSubject.id && it.subjectId == otherSubject.subjectId }

        fun wrongChoice(q: QuizQuestion): String = q.choices.first { it.id != q.correctChoiceId }.id

        return QuestionFixture(t1, t2, otherSubject, otherSubjectSecond, ::wrongChoice)
    }

    private class QuestionFixture(
        val sameTopicA: QuizQuestion,
        val sameTopicB: QuizQuestion,
        val otherA: QuizQuestion,
        val otherB: QuizQuestion,
        val wrongChoice: (QuizQuestion) -> String,
    )

    @Test
    fun `quiz start answer finish returns result with bySubject breakdown`() = withApp {
        val user = register("quiz@example.com")
        val f = client.loadQuestionFixture()

        val ids = listOf(f.sameTopicA.id, f.sameTopicB.id, f.otherA.id, f.otherB.id)
        val startResp = client.post("/api/v1/quizzes/start") {
            bearer(user.accessToken)
            jsonBody(encodeJson(QuizStartRequest(mode = QuizMode.STUDY, questionIds = ids)))
        }
        assertEquals(HttpStatusCode.Created, startResp.status)
        val attempt: QuizAttemptView = decodeJson(startResp.bodyAsText())
        assertEquals(QuizStatus.IN_PROGRESS, attempt.status)
        assertEquals(4, attempt.totalQuestions)
        assertEquals(4, attempt.questions.size)

        // STUDY mode serves answer keys for immediate feedback.
        assertTrue(attempt.questions.all { it.correctChoiceId != null }, "STUDY mode must expose correctChoiceId")

        suspend fun answer(questionId: String, choiceId: String): QuizAnswerFeedback {
            val resp = client.post("/api/v1/quizzes/${attempt.id}/answer") {
                bearer(user.accessToken)
                jsonBody(encodeJson(QuizAnswerRequest(questionId, choiceId)))
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            return decodeJson(resp.bodyAsText())
        }

        val a1 = answer(f.sameTopicA.id, f.sameTopicA.correctChoiceId!!)
        assertEquals(true, a1.correct)
        assertEquals(f.sameTopicA.correctChoiceId, a1.correctChoiceId)
        assertTrue(a1.explanation != null && a1.explanation!!.isNotBlank(), "STUDY feedback must include explanation")

        val wrong = f.wrongChoice(f.sameTopicB)
        val a2 = answer(f.sameTopicB.id, wrong)
        assertEquals(false, a2.correct)
        assertEquals(f.sameTopicB.correctChoiceId, a2.correctChoiceId)

        val a3 = answer(f.otherA.id, f.otherA.correctChoiceId!!)
        assertEquals(true, a3.correct)

        val a4 = answer(f.otherB.id, f.otherB.correctChoiceId!!)
        assertEquals(true, a4.correct)

        val finishResp = client.post("/api/v1/quizzes/${attempt.id}/finish") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, finishResp.status)
        val result: QuizResult = decodeJson(finishResp.bodyAsText())

        // Scoring math: 3 correct out of 4 => 0.75
        assertEquals(3, result.correct)
        assertEquals(4, result.total)
        assertTrue(kotlin.math.abs(result.score - 0.75) < 0.001, "score must be 0.75, got ${result.score}")
        assertEquals(listOf(f.sameTopicB.id), result.incorrectQuestionIds)

        // bySubject breakdown
        val subA = result.bySubject.first { it.subjectId == f.sameTopicA.subjectId }
        assertEquals(1, subA.correct)
        assertEquals(2, subA.total)
        val subB = result.bySubject.first { it.subjectId == f.otherA.subjectId }
        assertEquals(2, subB.correct)
        assertEquals(2, subB.total)

        // weakTopics: the shared topic has accuracy 0.5 (one right, one wrong)
        val weak = result.weakTopics.first { it.topicId == f.sameTopicA.topicId }
        assertTrue(kotlin.math.abs(weak.accuracy - 0.5) < 0.001, "topic accuracy must be 0.5, got ${weak.accuracy}")

        // GET attempt after finish exposes the result.
        val fetched = client.get("/api/v1/quizzes/${attempt.id}") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, fetched.status)
        val view: QuizAttemptView = decodeJson(fetched.bodyAsText())
        assertEquals(QuizStatus.COMPLETED, view.status)
        assertEquals(3, view.result?.correct)

        // List attempts with status filter.
        val list = client.get("/api/v1/quizzes?status=COMPLETED") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains(attempt.id))
    }

    @Test
    fun `exam mode masks answers until finished`() = withApp {
        val user = register("exam@example.com")
        val f = client.loadQuestionFixture()
        val startResp = client.post("/api/v1/quizzes/start") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    QuizStartRequest(mode = QuizMode.EXAM, questionIds = listOf(f.sameTopicA.id, f.sameTopicB.id)),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, startResp.status)
        val attempt: QuizAttemptView = decodeJson(startResp.bodyAsText())
        assertTrue(attempt.questions.all { it.correctChoiceId == null }, "EXAM mode must mask correctChoiceId")
        assertTrue(!startResp.bodyAsText().contains("\"correctChoiceId\""), "EXAM payload must not leak answer keys")

        val answerResp = client.post("/api/v1/quizzes/${attempt.id}/answer") {
            bearer(user.accessToken)
            jsonBody(encodeJson(QuizAnswerRequest(f.sameTopicA.id, f.sameTopicA.choices.first().id)))
        }
        assertEquals(HttpStatusCode.OK, answerResp.status)
        val feedback: QuizAnswerFeedback = decodeJson(answerResp.bodyAsText())
        assertEquals(true, feedback.accepted)
        assertEquals(null, feedback.correct, "EXAM mode must not reveal correctness while in progress")
    }
}
