package com.medtech.reviewhub.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.music.ChimePlayer
import com.medtech.reviewhub.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Study Room state: background scene, current study goal, distraction-free
 * toggle, chime preference and the small phase-end reactions (gentle chime,
 * calmer music on break). The Pomodoro state machine itself is the existing
 * [com.medtech.reviewhub.ui.focus.FocusTimerViewModel] (reused, not forked).
 */
class StudyRoomViewModel(
    private val settings: SettingsRepository,
    private val chimePlayer: ChimePlayer
) : ViewModel() {

    data class RoomState(
        val scene: String = "sakura",
        val goal: String = "",
        val distractionFree: Boolean = false,
        val chimeEnabled: Boolean = true,
        val musicOnBreaks: Boolean = true,
        val autoPlayOnStart: Boolean = false,
        val musicInteracted: Boolean = false,
        val defaultPlaylistId: String = "",
        val musicDuringReading: Boolean = true
    )

    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.update {
                    it.copy(
                        scene = s.studyRoomScene,
                        goal = s.studyRoomGoal,
                        chimeEnabled = s.timerChime,
                        musicOnBreaks = s.musicDuringBreaks,
                        autoPlayOnStart = s.autoPlayMusicOnSession,
                        musicInteracted = s.musicUserInteracted,
                        defaultPlaylistId = s.defaultPlaylistId
                    )
                }
            }
        }
    }

    fun setScene(scene: String) {
        _state.update { it.copy(scene = scene) }
        viewModelScope.launch { settings.setStudyRoomScene(scene) }
    }

    fun setGoal(goal: String) {
        _state.update { it.copy(goal = goal) }
        viewModelScope.launch { settings.setStudyRoomGoal(goal) }
    }

    fun setDistractionFree(enabled: Boolean) {
        _state.update { it.copy(distractionFree = enabled) }
    }

    fun setChime(enabled: Boolean) {
        _state.update { it.copy(chimeEnabled = enabled) }
        viewModelScope.launch { settings.setTimerChime(enabled) }
    }

    /** Gentle synthesized chime at a Pomodoro phase end (never loud). */
    fun onPhaseEnd() {
        if (_state.value.chimeEnabled) chimePlayer.play()
    }

    companion object {
        /** 5 background scenes (simple vector/gradient compositions). */
        val SCENES = listOf(
            Triple("sakura", "Sakura Morning", "sakura"),
            Triple("rainy", "Rainy Pink Cafe", "rainy"),
            Triple("night", "Cozy Night", "night"),
            Triple("lavender", "Lavender Evening", "lavender"),
            Triple("minimal", "Minimal Focus", "minimal")
        )
    }
}
