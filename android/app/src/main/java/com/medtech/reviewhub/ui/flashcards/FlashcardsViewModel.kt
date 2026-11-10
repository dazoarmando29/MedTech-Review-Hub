package com.medtech.reviewhub.ui.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.repo.FlashcardRepository
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.domain.CardState
import com.medtech.reviewhub.domain.Rating
import com.medtech.reviewhub.domain.model.FlashcardRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Flashcards: new/learning/review queues (FSRS-4.5 via [FlashcardRepository]),
 * daily new/review limits, favorites, notes, manual creation, CSV import/export,
 * review history. Storage is Room — fully offline.
 */
class FlashcardsViewModel(
    private val repository: FlashcardRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    data class DeckStats(val deckId: String, val total: Int, val due: Int)

    data class FlashcardsState(
        val loading: Boolean = true,
        val queueNew: List<FlashcardRecord> = emptyList(),
        val queueLearning: List<FlashcardRecord> = emptyList(),
        val queueReview: List<FlashcardRecord> = emptyList(),
        val currentIndex: Int = 0,
        val flipped: Boolean = false,
        val sessionReviewed: Int = 0,
        val decks: List<DeckStats> = emptyList(),
        val favorites: List<FlashcardRecord> = emptyList(),
        val allCards: List<FlashcardRecord> = emptyList(),
        val history: List<com.medtech.reviewhub.domain.ReviewLogEntry> = emptyList(),
        val lastSchedule: String? = null,
        val exportCsv: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(FlashcardsState())
    val state: StateFlow<FlashcardsState> = _state.asStateFlow()

    val current: FlashcardRecord?
        get() {
            val s = _state.value
            val queue = s.queueNew + s.queueLearning + s.queueReview
            return queue.getOrNull(s.currentIndex)
        }

    fun load() {
        viewModelScope.launch {
            val s = settings.current()
            val now = System.currentTimeMillis()
            val queue = repository.queue(now, s.dailyNewLimit, s.dailyReviewLimit)
            val all = repository.allCards()
            val decks = all.groupBy { it.deckId }.map { (deck, cards) ->
                DeckStats(deck, cards.size, cards.count { it.schedule.dueAtMs <= now })
            }
            _state.update {
                it.copy(
                    loading = false,
                    queueNew = queue.newCards,
                    queueLearning = queue.learning,
                    queueReview = queue.review,
                    decks = decks,
                    favorites = repository.favoriteCards(),
                    allCards = all
                )
            }
        }
    }

    fun flip() = _state.update { it.copy(flipped = !it.flipped) }

    fun rate(rating: Rating) {
        val card = current ?: return
        viewModelScope.launch {
            val schedule = repository.review(card.id, rating)
            val history = repository.history(card.id)
            _state.update {
                it.copy(
                    flipped = false,
                    currentIndex = it.currentIndex + 1,
                    sessionReviewed = it.sessionReviewed + 1,
                    history = history,
                    lastSchedule = schedule?.let { s ->
                        "Next review: ${java.text.DateFormat.getDateInstance().format(java.util.Date(s.dueAtMs))} " +
                            "(${s.state.name.lowercase()}, S=${"%.2f".format(s.stability)}d)"
                    }
                )
            }
        }
    }

    fun toggleFavorite(cardId: String) {
        viewModelScope.launch {
            val card = repository.card(cardId) ?: return@launch
            repository.setFavorite(cardId, !card.favorite)
            load()
        }
    }

    fun saveNote(cardId: String, note: String) {
        viewModelScope.launch {
            repository.setNote(cardId, note)
            load()
        }
    }

    fun createCard(front: String, back: String, tags: List<String>, type: String) {
        viewModelScope.launch {
            repository.createManual(front = front, back = back, tags = tags, type = type)
            load()
            _state.update { it.copy(message = "Card created.") }
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            repository.delete(cardId)
            load()
        }
    }

    fun exportAll() {
        viewModelScope.launch {
            val csv = repository.exportCsv(repository.allCards())
            _state.update { it.copy(exportCsv = csv) }
        }
    }

    fun importCsv(text: String) {
        viewModelScope.launch {
            val count = repository.importCsv(text)
            load()
            _state.update { it.copy(message = "Imported $count card(s).") }
        }
    }

    suspend fun cardHistory(cardId: String) = repository.history(cardId)
}
