package com.medtech.reviewhub.ui.pdf

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.pdf.PdfTextExtractor
import com.medtech.reviewhub.data.repo.PdfRepository
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.model.NormalizedRect
import com.medtech.reviewhub.domain.model.PdfAnnotation
import com.medtech.reviewhub.domain.model.PdfDocumentMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PDF reader state: page rendering via android.graphics.pdf.PdfRenderer,
 * page navigation, resume position, bookmarks, highlights (page+rect+color+text
 * in Room), notes, and best-effort in-app text search
 * ("No extractable text in this PDF (likely scanned)" on failure).
 */
class PdfViewModel(private val repository: PdfRepository) : ViewModel() {

    data class PdfState(
        val loading: Boolean = true,
        val documents: List<PdfDocumentMeta> = emptyList(),
        val currentDoc: PdfDocumentMeta? = null,
        val page: Int = 0,
        val pageCount: Int = 0,
        val pageBitmap: Bitmap? = null,
        val annotations: List<PdfAnnotation> = emptyList(),
        val bookmarks: List<PdfAnnotation> = emptyList(),
        val searchText: String = "",
        val searchResults: List<String> = emptyList(),
        val extractionNote: String? = null,
        val highlightDraft: NormalizedRect? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(PdfState())
    val state: StateFlow<PdfState> = _state.asStateFlow()

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    fun load() {
        viewModelScope.launch {
            repository.observePdfs().collect { docs ->
                _state.update { it.copy(loading = false, documents = docs) }
            }
        }
    }

    fun open(doc: PdfDocumentMeta) {
        viewModelScope.launch {
            closeRenderer()
            try {
                descriptor = ParcelFileDescriptor.open(File(doc.localPath), ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor!!)
                val startPage = repository.lastPage(doc.id).coerceIn(0, (renderer?.pageCount ?: 1) - 1)
                repository.touchOpened(doc.id)
                _state.update {
                    it.copy(
                        currentDoc = doc,
                        pageCount = renderer?.pageCount ?: 0,
                        page = startPage,
                        annotations = repository.annotations(doc.id),
                        bookmarks = repository.bookmarks(doc.id)
                    )
                }
                renderPage(startPage)
            } catch (e: Exception) {
                _state.update { it.copy(message = "Could not open PDF: ${e.message}") }
            }
        }
    }

    private suspend fun renderPage(index: Int) = withContext(Dispatchers.IO) {
        val r = renderer ?: return@withContext
        try {
            r.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(
                    (page.width * 1.5f).toInt().coerceAtMost(2200),
                    (page.height * 1.5f).toInt().coerceAtMost(2200),
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                _state.update { it.copy(pageBitmap = bitmap, page = index) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(message = "Render failed: ${e.message}") }
        }
    }

    fun goToPage(index: Int) {
        viewModelScope.launch {
            val count = _state.value.pageCount
            if (index in 0 until count) {
                renderPage(index)
                _state.value.currentDoc?.let { repository.saveProgress(it.id, index, count) }
            }
        }
    }

    fun nextPage() = goToPage(_state.value.page + 1)
    fun prevPage() = goToPage(_state.value.page - 1)

    fun setHighlightDraft(rect: NormalizedRect?) = _state.update { it.copy(highlightDraft = rect) }

    fun addHighlight(color: HighlightColor, text: String, note: String) {
        viewModelScope.launch {
            val s = _state.value
            val doc = s.currentDoc ?: return@launch
            repository.addHighlight(doc.id, s.page, color, text, note, s.highlightDraft)
            _state.update {
                it.copy(
                    highlightDraft = null,
                    annotations = repository.annotations(doc.id),
                    message = "Highlight saved."
                )
            }
        }
    }

    fun addNote(text: String, note: String) {
        viewModelScope.launch {
            val s = _state.value
            val doc = s.currentDoc ?: return@launch
            repository.addNote(doc.id, s.page, text, note)
            _state.update { it.copy(annotations = repository.annotations(doc.id), message = "Note saved.") }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val s = _state.value
            val doc = s.currentDoc ?: return@launch
            val added = repository.toggleBookmark(doc.id, s.page)
            _state.update {
                it.copy(bookmarks = repository.bookmarks(doc.id), message = if (added) "Bookmarked page ${s.page + 1}." else "Bookmark removed.")
            }
        }
    }

    fun deleteAnnotation(a: PdfAnnotation) {
        viewModelScope.launch {
            repository.deleteAnnotation(a)
            val doc = _state.value.currentDoc
            _state.update { it.copy(annotations = doc?.let { d -> repository.annotations(d.id) } ?: emptyList()) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val s = _state.value
            val doc = s.currentDoc ?: return@launch
            if (query.isBlank()) {
                _state.update { it.copy(searchText = query, searchResults = emptyList(), extractionNote = null) }
                return@launch
            }
            val results = repository.searchText(doc.id, query)
            _state.update {
                it.copy(
                    searchText = query,
                    searchResults = results,
                    extractionNote = if (results.isEmpty()) PdfTextExtractor.NO_TEXT_MESSAGE else null
                )
            }
        }
    }

    fun importFromUri(uri: android.net.Uri, name: String) {
        viewModelScope.launch {
            val doc = repository.importFromUri(uri, name)
            _state.update {
                it.copy(message = if (doc != null) "Imported $name" else "Import failed.")
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun closeRenderer() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    override fun onCleared() {
        closeRenderer()
        super.onCleared()
    }
}
