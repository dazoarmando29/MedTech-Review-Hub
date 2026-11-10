package com.medtech.reviewhub.data.repo

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.medtech.reviewhub.data.db.PdfAnnotationEntity
import com.medtech.reviewhub.data.db.PdfDao
import com.medtech.reviewhub.data.db.PdfEntity
import com.medtech.reviewhub.data.db.ReadingProgressEntity
import com.medtech.reviewhub.data.pdf.PdfTextExtractor
import com.medtech.reviewhub.domain.model.AnnotationType
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.model.NormalizedRect
import com.medtech.reviewhub.domain.model.PdfAnnotation
import com.medtech.reviewhub.domain.model.PdfDocumentMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * PDF document library: SAF import (ACTION_OPEN_DOCUMENT), local storage for
 * offline reading, annotations (highlights/notes/bookmarks), reading progress,
 * and best-effort text search via [PdfTextExtractor].
 */
class PdfRepository(private val context: Context, private val dao: PdfDao) {

    fun observePdfs(): Flow<List<PdfDocumentMeta>> = dao.pdfs().map { list -> list.map { it.toDomain() } }

    /**
     * Imports a PDF from a SAF content:// [uri]: copies it into app-private
     * storage (PdfRenderer needs a seekable fd) and records metadata.
     */
    suspend fun importFromUri(uri: Uri, displayName: String, folder: String = "Default"): PdfDocumentMeta? =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.pdf")
                input.use { ins -> file.outputStream().use { out -> ins.copyTo(out) } }
                val pageCount = countPages(file)
                val meta = PdfEntity(
                    id = UUID.randomUUID().toString(),
                    displayName = displayName,
                    localPath = file.absolutePath,
                    folder = folder,
                    pageCount = pageCount,
                    sizeBytes = file.length(),
                    addedAtMs = System.currentTimeMillis(),
                    lastOpenedAtMs = null
                )
                dao.upsertPdf(meta)
                meta.toDomain()
            } catch (e: Exception) {
                null
            }
        }

    private fun countPages(file: File): Int {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            renderer.pageCount
        } catch (e: Exception) {
            0
        } finally {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
        }
    }

    suspend fun pdf(id: String): PdfDocumentMeta? = dao.pdfById(id)?.toDomain()

    suspend fun touchOpened(id: String) {
        val e = dao.pdfById(id) ?: return
        dao.upsertPdf(e.copy(lastOpenedAtMs = System.currentTimeMillis()))
    }

    suspend fun deletePdf(meta: PdfDocumentMeta) {
        dao.pdfById(meta.id)?.let {
            runCatching { File(it.localPath).delete() }
            dao.deletePdf(it)
        }
    }

    // ---- Annotations ----------------------------------------------------

    suspend fun annotations(pdfId: String): List<PdfAnnotation> = dao.annotationsFor(pdfId).map { it.toDomain() }

    suspend fun annotationsForPage(pdfId: String, page: Int): List<PdfAnnotation> =
        dao.annotationsForPage(pdfId, page).map { it.toDomain() }

    suspend fun addHighlight(pdfId: String, page: Int, color: HighlightColor, text: String,
                             note: String, rect: NormalizedRect?): PdfAnnotation {
        val a = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            pdfId = pdfId,
            page = page,
            type = AnnotationType.HIGHLIGHT,
            color = color,
            selectedText = text,
            note = note,
            rect = rect,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertAnnotation(a.toEntity())
        return a
    }

    suspend fun addNote(pdfId: String, page: Int, text: String, note: String): PdfAnnotation {
        val a = PdfAnnotation(
            id = UUID.randomUUID().toString(),
            pdfId = pdfId,
            page = page,
            type = AnnotationType.NOTE,
            color = HighlightColor.YELLOW,
            selectedText = text,
            note = note,
            rect = null,
            createdAt = System.currentTimeMillis()
        )
        dao.upsertAnnotation(a.toEntity())
        return a
    }

    suspend fun toggleBookmark(pdfId: String, page: Int): Boolean {
        val existing = dao.annotationsForPage(pdfId, page).firstOrNull { it.type == "BOOKMARK" }
        return if (existing != null) {
            dao.deleteAnnotation(existing)
            false
        } else {
            dao.upsertAnnotation(
                PdfAnnotationEntity(
                    id = UUID.randomUUID().toString(), pdfId = pdfId, page = page,
                    type = "BOOKMARK", color = "BLUE", selectedText = "", note = "",
                    rectX = 0f, rectY = 0f, rectW = 0f, rectH = 0f, hasRect = false,
                    createdAtMs = System.currentTimeMillis()
                )
            )
            true
        }
    }

    suspend fun bookmarks(pdfId: String): List<PdfAnnotation> =
        dao.annotationsFor(pdfId).filter { it.type == "BOOKMARK" }.map { it.toDomain() }

    suspend fun updateAnnotationNote(a: PdfAnnotation, note: String) {
        dao.upsertAnnotation(a.copy(note = note).toEntity())
    }

    suspend fun deleteAnnotation(a: PdfAnnotation) = dao.deleteAnnotation(a.toEntity())

    suspend fun searchAnnotations(query: String): List<PdfAnnotation> =
        dao.searchAnnotations(query).map { it.toDomain() }

    // ---- Reading progress ------------------------------------------------

    suspend fun saveProgress(pdfId: String, page: Int, pageCount: Int) {
        val key = "pdf:$pdfId"
        dao.pdfById(pdfId)?.let { dao.upsertPdf(it.copy(lastOpenedAtMs = System.currentTimeMillis())) }
        dao.upsertProgress(
            ReadingProgressEntity(
                key = key,
                progressPercent = if (pageCount > 0) page.toFloat() / pageCount else 0f,
                lastOffset = page,
                bookmarked = false,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun lastPage(pdfId: String): Int = dao.progress("pdf:$pdfId")?.lastOffset ?: 0

    // ---- Text extraction / search ---------------------------------------

    /**
     * Best-effort in-app text search. Clearly labeled best-effort: scanned
     * PDFs return [PdfTextExtractor.NO_TEXT_MESSAGE].
     */
    suspend fun searchText(pdfId: String, query: String): List<String> = withContext(Dispatchers.IO) {
        val file = File(dao.pdfById(pdfId)?.localPath ?: return@withContext emptyList())
        if (!file.exists()) return@withContext emptyList()
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext emptyList()
        PdfTextExtractor.search(bytes, query)
    }

    suspend fun extractText(pdfId: String): PdfTextExtractor.Extraction = withContext(Dispatchers.IO) {
        val file = File(dao.pdfById(pdfId)?.localPath ?: return@withContext PdfTextExtractor.Extraction("", 0, 0))
        if (!file.exists()) return@withContext PdfTextExtractor.Extraction("", 0, 0)
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return@withContext PdfTextExtractor.Extraction("", 0, 0)
        PdfTextExtractor.extract(bytes)
    }
}

// ---- Entity mapping ----------------------------------------------------

private fun PdfEntity.toDomain() = PdfDocumentMeta(
    id = id, displayName = displayName, localPath = localPath, folder = folder,
    pageCount = pageCount, sizeBytes = sizeBytes, addedAt = addedAtMs, lastOpenedAt = lastOpenedAtMs
)

private fun PdfAnnotationEntity.toDomain() = PdfAnnotation(
    id = id, pdfId = pdfId, page = page,
    type = runCatching { AnnotationType.valueOf(type) }.getOrDefault(AnnotationType.HIGHLIGHT),
    color = runCatching { HighlightColor.valueOf(color) }.getOrDefault(HighlightColor.YELLOW),
    selectedText = selectedText, note = note,
    rect = if (hasRect) NormalizedRect(rectX, rectY, rectW, rectH) else null,
    createdAt = createdAtMs
)

private fun PdfAnnotation.toEntity() = PdfAnnotationEntity(
    id = id, pdfId = pdfId, page = page, type = type.name, color = color.name,
    selectedText = selectedText, note = note,
    rectX = rect?.x ?: 0f, rectY = rect?.y ?: 0f, rectW = rect?.w ?: 0f, rectH = rect?.h ?: 0f,
    hasRect = rect != null, createdAtMs = createdAt
)
