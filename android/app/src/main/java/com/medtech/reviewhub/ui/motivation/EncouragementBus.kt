package com.medtech.reviewhub.ui.motivation

import com.medtech.reviewhub.domain.ContextKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global contextual-encouragement bus: screens push events (lesson finished,
 * flashcard session done, wrong quiz answers, daily goals complete, long focus
 * session) and the banner overlay in MainActivity shows a small mascot + quote
 * banner. Respecting the settings (motivational banners on/off, quote
 * frequency) is the observer's job.
 */
object EncouragementBus {

    data class Event(
        val kind: ContextKind,
        val seed: Long,
        val id: Long,
        val detail: String = ""
    )

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private var nextId = 1L

    fun trigger(kind: ContextKind, detail: String = "", seed: Long = System.currentTimeMillis()) {
        _event.value = Event(kind = kind, seed = seed, id = nextId++, detail = detail)
    }

    fun dismiss() {
        _event.value = null
    }
}
