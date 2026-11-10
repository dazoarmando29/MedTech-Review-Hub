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
import io.medtech.backend.domain.QuizResult
import io.medtech.backend.domain.QuizStartRequest
import io.medtech.backend.domain.QuizStatus
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuizTest {

    @Test
    fun `quiz start answer finish returns result with bySubject breakdown`() = withApp {
        val user = register("quiz@example.com")

        val startResp = client.post("/api/v1/quizzes/start") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    QuizStartRequest(
                        mode = QuizMode.STUDY,
                        questionIds = listOf("bb-q-001", "bb-q-002", "hema-q-001", "hema-q-002"),
                    ),
                ),
            )
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

        val a1 = answer("bb-q-001", "C") // correct
        assertEquals(true, a1.correct)
        assertEquals("C", a1.correctChoiceId)
        assertTrue(a1.explanation != null && a1.explanation!!.isNotBlank(), "STUDY feedback must include explanation")

        val a2 = answer("bb-q-002", "A") // wrong (correct is B)
        assertEquals(false, a2.correct)
        assertEquals("B", a2.correctChoiceId)

        val a3 = answer("hema-q-001", "A") // correct
        assertEquals(true, a3.correct)

        val a4 = answer("hema-q-002", "B") // correct
        assertEquals(true, a4.correct)

        val finishResp = client.post("/api/v1/quizzes/${attempt.id}/finish") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, finishResp.status)
        val result: QuizResult = decodeJson(finishResp.bodyAsText())

        // Scoring math: 3 correct out of 4 => 0.75
        assertEquals(3, result.correct)
        assertEquals(4, result.total)
        assertTrue(kotlin.math.abs(result.score - 0.75) < 0.001, "score must be 0.75, got ${result.score}")
        assertEquals(listOf("bb-q-002"), result.incorrectQuestionIds)

        // bySubject breakdown
        val bb = result.bySubject.first { it.subjectId == "blood-banking" }
        assertEquals(1, bb.correct)
        assertEquals(2, bb.total)
        val hema = result.bySubject.first { it.subjectId == "hematology" }
        assertEquals(2, hema.correct)
        assertEquals(2, hema.total)

        // weakTopics: abo-system accuracy 0.5
        val weak = result.weakTopics.first { it.topicId == "abo-system" }
        assertTrue(kotlin.math.abs(weak.accuracy - 0.5) < 0.001, "abo-system accuracy must be 0.5, got ${weak.accuracy}")

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
        val startResp = client.post("/api/v1/quizzes/start") {
            bearer(user.accessToken)
            jsonBody(encodeJson(QuizStartRequest(mode = QuizMode.EXAM, questionIds = listOf("bb-q-001", "bb-q-002"))))
        }
        assertEquals(HttpStatusCode.Created, startResp.status)
        val attempt: QuizAttemptView = decodeJson(startResp.bodyAsText())
        assertTrue(attempt.questions.all { it.correctChoiceId == null }, "EXAM mode must mask correctChoiceId")
        assertTrue(!startResp.bodyAsText().contains("\"correctChoiceId\""), "EXAM payload must not leak answer keys")

        val answerResp = client.post("/api/v1/quizzes/${attempt.id}/answer") {
            bearer(user.accessToken)
            jsonBody(encodeJson(QuizAnswerRequest("bb-q-001", "C")))
        }
        assertEquals(HttpStatusCode.OK, answerResp.status)
        val feedback: QuizAnswerFeedback = decodeJson(answerResp.bodyAsText())
        assertEquals(true, feedback.accepted)
        assertEquals(null, feedback.correct, "EXAM mode must not reveal correctness while in progress")
    }
}
