package com.medtech.reviewhub.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.db.LoveNoteEntity
import com.medtech.reviewhub.data.repo.LoveNoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Little Notes for You" - private personal notes (create/edit/delete, optional
 * show-on date, favorite flag). Stored in Room; never synced publicly; included
 * in the JSON data export only. The app never auto-generates notes.
 */
class NotesViewModel(private val repository: LoveNoteRepository) : ViewModel() {

    data class NotesState(
        val loading: Boolean = true,
        val notes: List<LoveNoteEntity> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { notes ->
                _state.update { it.copy(loading = false, notes = notes) }
            }
        }
    }

    fun create(text: String, showOnDate: String?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.create(text, showOnDate)
            _state.update { it.copy(message = "Note saved (private, just for you).") }
        }
    }

    fun update(note: LoveNoteEntity, text: String, showOnDate: String?) {
        viewModelScope.launch {
            repository.update(note.copy(text = text.trim(), showOnDate = showOnDate?.trim()?.takeIf { it.isNotBlank() }))
        }
    }

    fun toggleFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, favorite) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
