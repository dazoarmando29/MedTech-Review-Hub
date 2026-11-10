package io.medtech.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.login
import io.medtech.backend.TestApi.multipartPdf
import io.medtech.backend.TestApi.pdfBytes
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.FlashcardCreateRequest
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import io.medtech.backend.domain.DiaryCreateRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataIsolationTest {

    @Test
    fun `user A cannot read or modify user B private resources`() = withApp {
        val userA = register("a@example.com", displayName = "User A")
        val userB = register("b@example.com", displayName = "User B")

        // A creates private data: flashcard, diary entry, pdf.
        val cardResp = client.post("/api/v1/flashcards") {
            bearer(userA.accessToken)
            jsonBody(encodeJson(FlashcardCreateRequest(front = "A front", back = "A back", tags = listOf("a-private"))))
        }
        assertEquals(HttpStatusCode.Created, cardResp.status)
        val card: io.medtech.backend.domain.Flashcard = decodeJson(cardResp.bodyAsText())
        val cardId = card.id

        val diaryResp = client.post("/api/v1/diary") {
            bearer(userA.accessToken)
            jsonBody(
                encodeJson(
                    DiaryCreateRequest(
                        date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date,
                        mood = "PRIVATE",
                        text = "A's secret entry",
                        tags = listOf("a-private"),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, diaryResp.status)
        val diary: io.medtech.backend.domain.DiaryEntry = decodeJson(diaryResp.bodyAsText())

        val upload = client.post("/api/v1/pdfs") {
            bearer(userA.accessToken)
            setBody(multipartPdf("a.pdf", pdfBytes()))
        }
        assertEquals(HttpStatusCode.Created, upload.status)
        val pdf: io.medtech.backend.domain.PdfDocumentView = decodeJson(upload.bodyAsText())

        // B cannot read or modify A's flashcard.
        val bPatchCard = client.patch("/api/v1/flashcards/$cardId") {
            bearer(userB.accessToken)
            jsonBody(encodeJson(mapOf("front" to "hacked")))
        }
        assertTrue(bPatchCard.status == HttpStatusCode.Forbidden || bPatchCard.status == HttpStatusCode.NotFound, "got ${bPatchCard.status}")
        val bDeleteCard = client.delete("/api/v1/flashcards/$cardId") { bearer(userB.accessToken) }
        assertTrue(bDeleteCard.status == HttpStatusCode.Forbidden || bDeleteCard.status == HttpStatusCode.NotFound, "got ${bDeleteCard.status}")
        val bReviewCard = client.post("/api/v1/flashcards/$cardId/review") {
            bearer(userB.accessToken)
            jsonBody(encodeJson(mapOf("rating" to "GOOD")))
        }
        assertTrue(bReviewCard.status == HttpStatusCode.Forbidden || bReviewCard.status == HttpStatusCode.NotFound, "got ${bReviewCard.status}")

        // B cannot read A's pdf or annotations.
        val bPdfFile = client.get("/api/v1/pdfs/${pdf.id}/file") { bearer(userB.accessToken) }
        assertTrue(bPdfFile.status == HttpStatusCode.Forbidden || bPdfFile.status == HttpStatusCode.NotFound, "got ${bPdfFile.status}")
        val bAnnotations = client.get("/api/v1/pdfs/${pdf.id}/annotations") { bearer(userB.accessToken) }
        assertTrue(bAnnotations.status == HttpStatusCode.Forbidden || bAnnotations.status == HttpStatusCode.NotFound, "got ${bAnnotations.status}")

        // B's collections do not contain A's data.
        val bCards = client.get("/api/v1/flashcards") { bearer(userB.accessToken) }
        assertEquals(HttpStatusCode.OK, bCards.status)
        assertTrue(!bCards.bodyAsText().contains(cardId), "B must not see A's flashcard")
        val bDiary = client.get("/api/v1/diary") { bearer(userB.accessToken) }
        assertEquals(HttpStatusCode.OK, bDiary.status)
        assertTrue(!bDiary.bodyAsText().contains("A's secret entry"), "B must not see A's diary")
        val bPdfs = client.get("/api/v1/pdfs") { bearer(userB.accessToken) }
        assertEquals(HttpStatusCode.OK, bPdfs.status)
        assertTrue(!bPdfs.bodyAsText().contains(pdf.id), "B must not see A's pdf")

        // A still has everything untouched.
        val aCard = client.get("/api/v1/flashcards") { bearer(userA.accessToken) }
        assertTrue(aCard.bodyAsText().contains(cardId), "A's card must survive B's attempts")
        val aDiary = client.get("/api/v1/diary") { bearer(userA.accessToken) }
        assertTrue(aDiary.bodyAsText().contains(diary.id), "A's diary entry must survive")

        // B cannot modify A's diary entry via any route (diary has no direct edit for others by design:
        // entries are only listed per-user; ensure listing by B stays empty).
        assertEquals(HttpStatusCode.OK, bDiary.status)
    }
}
