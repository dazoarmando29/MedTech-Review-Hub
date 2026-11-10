package io.medtech.backend.service

import io.medtech.backend.config.AppConfig
import io.medtech.backend.domain.Annotation
import io.medtech.backend.domain.AnnotationInput
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.PdfDocumentView
import io.medtech.backend.repository.PdfRepository
import io.medtech.backend.security.Sanitizer
import java.io.File

class PdfService(
    private val config: AppConfig,
    private val pdfs: PdfRepository = PdfRepository(),
) {
    private fun userDir(userId: String): File {
        val dir = File(config.mediaRoot, userId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun upload(userId: String, filename: String, contentType: String, bytes: ByteArray): PdfDocumentView {
        val cleanName = Sanitizer.cleanToken(filename, 255).ifBlank { "document.pdf" }
        if (bytes.size.toLong() > config.uploadMaxBytes) {
            throw ApiException.payloadTooLarge("Upload exceeds the maximum size of ${config.uploadMaxBytes} bytes")
        }
        if (bytes.isEmpty()) {
            throw ApiException.validation("Uploaded file is empty")
        }
        val looksLikePdfName = cleanName.endsWith(".pdf", ignoreCase = true)
        val looksLikePdfType = contentType.lowercase().startsWith("application/pdf")
        if (!looksLikePdfName && !looksLikePdfType) {
            throw ApiException.validation("Only PDF files are accepted", mapOf("file" to "must be application/pdf"))
        }
        if (!hasPdfMagic(bytes)) {
            throw ApiException.validation("Only PDF files are accepted", mapOf("file" to "content is not a PDF"))
        }
        val provisionalId = io.medtech.backend.db.newId()
        val target = File(userDir(userId), "$provisionalId.pdf")
        target.writeBytes(bytes)
        val view = pdfs.insert(userId, cleanName, bytes.size.toLong(), target.absolutePath)
        return view
    }

    suspend fun list(userId: String): List<PdfDocumentView> = pdfs.list(userId)

    suspend fun file(userId: String, pdfId: String): Pair<PdfRepository.OwnedPdf, ByteArray> {
        val owned = pdfs.owned(userId, pdfId) ?: throw ApiException.notFound("Document not found")
        val file = File(owned.storagePath)
        if (!file.isFile) throw ApiException.notFound("Document file missing")
        return owned to file.readBytes()
    }

    suspend fun delete(userId: String, pdfId: String) {
        val owned = pdfs.owned(userId, pdfId) ?: throw ApiException.notFound("Document not found")
        File(owned.storagePath).delete()
        pdfs.delete(userId, pdfId)
    }

    suspend fun annotations(userId: String, pdfId: String): List<Annotation> {
        pdfs.owned(userId, pdfId) ?: throw ApiException.notFound("Document not found")
        return pdfs.annotations(userId, pdfId)
    }

    suspend fun replaceAnnotations(userId: String, pdfId: String, annotations: List<AnnotationInput>): List<Annotation> {
        pdfs.owned(userId, pdfId) ?: throw ApiException.notFound("Document not found")
        return pdfs.replaceAnnotations(userId, pdfId, annotations)
    }

    private fun hasPdfMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        val header = bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII)
        return header == "%PDF-"
    }
}
