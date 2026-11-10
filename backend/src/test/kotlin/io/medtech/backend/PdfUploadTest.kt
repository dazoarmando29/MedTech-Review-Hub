package io.medtech.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.multipartPdf
import io.medtech.backend.TestApi.pdfBytes
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.AnnotationColor
import io.medtech.backend.domain.AnnotationInput
import io.medtech.backend.domain.AnnotationReplaceRequest
import io.medtech.backend.domain.AnnotationType
import io.medtech.backend.domain.PdfDocumentView
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfUploadTest {

    @Test
    fun `upload rejects non-pdf and accepts valid pdf with annotations`() = withApp {
        val user = register("pdf@example.com")

        // Non-PDF content with a .pdf name must be rejected (magic-bytes check).
        val fake = client.post("/api/v1/pdfs") {
            bearer(user.accessToken)
            setBody(multipartPdf("fake.pdf", "just some text, not a pdf".toByteArray()))
        }
        assertEquals(HttpStatusCode.BadRequest, fake.status)
        assertTrue(fake.text().contains("VALIDATION_ERROR"), fake.text())

        // Wrong content type + wrong name must be rejected.
        val wrongType = client.post("/api/v1/pdfs") {
            bearer(user.accessToken)
            setBody(multipartPdf("notes.txt", "hello".toByteArray(), contentType = "text/plain"))
        }
        assertEquals(HttpStatusCode.BadRequest, wrongType.status)

        // Valid PDF accepted.
        val ok = client.post("/api/v1/pdfs") {
            bearer(user.accessToken)
            setBody(multipartPdf("handbook.pdf", pdfBytes(1024)))
        }
        assertEquals(HttpStatusCode.Created, ok.status)
        val doc: PdfDocumentView = decodeJson(ok.text())
        assertEquals("handbook.pdf", doc.filename)
        assertTrue(doc.sizeBytes > 0)

        // List + download + annotations roundtrip.
        val list = client.get("/api/v1/pdfs") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains(doc.id))

        val file = client.get("/api/v1/pdfs/${doc.id}/file") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, file.status)
        assertTrue(file.bodyAsText().startsWith("%PDF-"), "download must return the PDF bytes")

        val annotationsResp = client.put("/api/v1/pdfs/${doc.id}/annotations") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    AnnotationReplaceRequest(
                        annotations = listOf(
                            AnnotationInput(
                                page = 12,
                                type = AnnotationType.HIGHLIGHT,
                                color = AnnotationColor.YELLOW,
                                selectedText = "Staphylococcus aureus is catalase positive",
                                note = "high yield",
                            ),
                        ),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, annotationsResp.status)
        val annotations: List<io.medtech.backend.domain.Annotation> = decodeJson(annotationsResp.text())
        assertEquals(1, annotations.size)
        assertEquals(12, annotations[0].page)

        val fetchedAnnotations = client.get("/api/v1/pdfs/${doc.id}/annotations") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, fetchedAnnotations.status)

        // Delete removes document + annotations.
        val delete = client.delete("/api/v1/pdfs/${doc.id}") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, delete.status)
        val gone = client.get("/api/v1/pdfs/${doc.id}/annotations") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.NotFound, gone.status)
    }

    @Test
    fun `upload rejects oversize payloads`() {
        // Configure a tiny upload cap so the oversize path is exercised quickly;
        // production default is 50 MB via UPLOAD_MAX_BYTES.
        withApp(config = TestApi.config(uploadMaxBytes = 10_000)) {
            val user = register("big@example.com")
            val big = client.post("/api/v1/pdfs") {
                bearer(user.accessToken)
                setBody(multipartPdf("big.pdf", pdfBytes(20_000)))
            }
            assertEquals(HttpStatusCode.PayloadTooLarge, big.status)
            assertTrue(big.text().contains("PAYLOAD_TOO_LARGE"), big.text())
        }
    }
}
