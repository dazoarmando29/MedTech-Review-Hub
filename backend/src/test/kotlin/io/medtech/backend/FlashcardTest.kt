package io.medtech.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.Flashcard
import io.medtech.backend.domain.FlashcardCreateRequest
import io.medtech.backend.domain.FlashcardQueue
import io.medtech.backend.domain.ImportRequest
import io.medtech.backend.domain.ImportResult
import io.medtech.backend.domain.ImportRow
import io.medtech.backend.domain.Rating
import io.medtech.backend.domain.ReviewRequest
import io.medtech.backend.domain.ReviewResponse
import io.medtech.backend.domain.ScheduleState
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlashcardTest {

    @Test
    fun `flashcard crud and fsrs review roundtrip`() = withApp {
        val user = register("cards@example.com")

        // Create
        val createdResp = client.post("/api/v1/flashcards") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    FlashcardCreateRequest(
                        deckTitle = "Hematology deck",
                        front = "What does MCV stand for?",
                        back = "Mean corpuscular volume",
                        tags = listOf("cbc", "high-yield"),
                        subjectId = "hematology",
                        topicId = "cbc",
                        difficulty = 2,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createdResp.status)
        val created: Flashcard = decodeJson(createdResp.bodyAsText())
        assertEquals(ScheduleState.NEW, created.schedule.state)
        assertEquals(0, created.schedule.reps)
        assertEquals("What does MCV stand for?", created.front)

        // Queue shows the new card
        val queueResp = client.get("/api/v1/flashcards/queue") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, queueResp.status)
        val queue: FlashcardQueue = decodeJson(queueResp.bodyAsText())
        assertTrue(queue.new.any { it.id == created.id }, "new card must appear in the new queue")

        // Update (PATCH)
        val patchedResp = client.patch("/api/v1/flashcards/${created.id}") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    io.medtech.backend.domain.FlashcardUpdateRequest(
                        front = "What does MCV measure?",
                        favorite = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, patchedResp.status)
        val patched: Flashcard = decodeJson(patchedResp.bodyAsText())
        assertEquals("What does MCV measure?", patched.front)
        assertTrue(patched.favorite)

        // Review GOOD on a NEW card graduates it to REVIEW with FSRS init stability.
        val now = kotlinx.datetime.Clock.System.now()
        val review1 = client.post("/api/v1/flashcards/${created.id}/review") {
            bearer(user.accessToken)
            jsonBody(encodeJson(ReviewRequest(rating = Rating.GOOD, reviewedAt = now)))
        }
        assertEquals(HttpStatusCode.OK, review1.status)
        val body1: ReviewResponse = decodeJson(review1.bodyAsText())
        val s1 = body1.schedule
        assertEquals(ScheduleState.REVIEW, s1.state)
        assertEquals(1, s1.reps)
        assertEquals(0, s1.lapses)
        assertTrue(kotlin.math.abs(s1.stability - 3.7145) < 0.01, "expected init_stability(GOOD)=3.7145, got ${s1.stability}")
        assertTrue(kotlin.math.abs(s1.difficulty - 5.1618) < 0.01, "expected init_difficulty(GOOD)=5.1618, got ${s1.difficulty}")
        val intervalDays1 = (s1.dueAt.toEpochMilliseconds() - now.toEpochMilliseconds()) / 86_400_000.0
        assertTrue(intervalDays1 in 3.0..5.0, "interval should be ~4 days, got $intervalDays1")

        // FSRS shape fields present in the raw payload.
        val raw = review1.bodyAsText()
        listOf("state", "stability", "difficulty", "dueAt", "lastReviewAt", "reps", "lapses").forEach {
            assertTrue(raw.contains("\"$it\""), "schedule must expose '$it'")
        }

        // Second review two days later with EASY increases stability.
        val review2 = client.post("/api/v1/flashcards/${created.id}/review") {
            bearer(user.accessToken)
            jsonBody(encodeJson(ReviewRequest(rating = Rating.EASY, reviewedAt = now.plus(kotlinx.datetime.DateTimeUnit.DAY * 2))))
        }
        assertEquals(HttpStatusCode.OK, review2.status)
        val body2: ReviewResponse = decodeJson(review2.bodyAsText())
        assertEquals(ScheduleState.REVIEW, body2.schedule.state)
        assertEquals(2, body2.schedule.reps)
        assertTrue(body2.schedule.stability > s1.stability, "EASY must increase stability")

        // Card now carries 2 history entries.
        val fetched = client.get("/api/v1/flashcards?due=true") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, fetched.status)

        val allResp = client.get("/api/v1/flashcards") { bearer(user.accessToken) }
        val all: List<Flashcard> = decodeJson(allResp.bodyAsText())
        val reloaded = all.first { it.id == created.id }
        assertEquals(2, reloaded.history.size, "history must record both reviews")
        assertEquals(Rating.GOOD, reloaded.history[0].rating)

        // Import
        val importResp = client.post("/api/v1/flashcards/import") {
            bearer(user.accessToken)
            jsonBody(encodeJson(ImportRequest(rows = listOf(ImportRow(front = "Imported front", back = "Imported back", tags = listOf("import"))))))
        }
        assertEquals(HttpStatusCode.OK, importResp.status)
        val importResult: ImportResult = decodeJson(importResp.bodyAsText())
        assertEquals(1, importResult.imported)

        // Export CSV
        val export = client.get("/api/v1/flashcards/export?format=csv") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, export.status)
        assertTrue(export.bodyAsText().contains("Imported front"), "export must contain imported card")

        // Delete
        val deleted = client.delete("/api/v1/flashcards/${created.id}") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, deleted.status)
        val afterDelete = client.get("/api/v1/flashcards") { bearer(user.accessToken) }
        assertTrue(!afterDelete.bodyAsText().contains(created.id), "deleted card must disappear")
    }

    private fun kotlinx.datetime.Instant.plus(unit: kotlinx.datetime.DateTimeUnit.DayBased): kotlinx.datetime.Instant =
        kotlinx.datetime.Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + unit.days * 86_400_000L)
}
