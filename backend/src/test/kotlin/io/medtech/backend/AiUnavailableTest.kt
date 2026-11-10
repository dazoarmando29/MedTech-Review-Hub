package io.medtech.backend

import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiUnavailableTest {

    @Test
    fun `ai endpoints return 503 AI_UNAVAILABLE when unconfigured`() = withApp {
        val user = register("ai@example.com")

        data class Case(val path: String, val body: String)

        val textBody = encodeJson(mapOf("text" to "Hemoglobin carries oxygen."))
        val cases = listOf(
            Case("/api/v1/ai/summarize", encodeJson(mapOf("text" to "Hemoglobin carries oxygen.", "mode" to "PAGE"))),
            Case("/api/v1/ai/flashcards", textBody),
            Case("/api/v1/ai/quiz", textBody),
            Case("/api/v1/ai/explain", encodeJson(mapOf("text" to "Hemoglobin carries oxygen.", "style" to "SIMPLE_ENGLISH"))),
            Case("/api/v1/ai/outline", textBody),
            Case("/api/v1/ai/keyterms", textBody),
            Case("/api/v1/ai/diary-summary", encodeJson(mapOf("days" to 7))),
        )

        for (case in cases) {
            val resp = client.post(case.path) {
                bearer(user.accessToken)
                jsonBody(case.body)
            }
            assertEquals(HttpStatusCode.ServiceUnavailable, resp.status, "${case.path} must be 503 when AI is unconfigured")
            val payload: io.medtech.backend.domain.ErrorResponse = decodeJson(resp.bodyAsText())
            val error = payload.error
            assertEquals("AI_UNAVAILABLE", error.code, "${case.path} must use code AI_UNAVAILABLE")
            assertEquals("AI features are not configured on this server", error.message)
        }
    }

    @Test
    fun `ai endpoints require authentication`() = withApp {
        val resp = client.post("/api/v1/ai/summarize") {
            jsonBody(encodeJson(mapOf("text" to "x", "mode" to "PAGE")))
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        assertTrue(resp.bodyAsText().contains("UNAUTHORIZED"), resp.bodyAsText())
    }
}
