package com.medtech.reviewhub.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.BuildConfig
import com.medtech.reviewhub.data.network.UpdateChecker
import com.medtech.reviewhub.data.prefs.AppSettings
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.data.prefs.ThemeMode
import com.medtech.reviewhub.data.repo.AuthRepository
import com.medtech.reviewhub.data.repo.FlashcardRepository
import com.medtech.reviewhub.data.repo.StudyRepository
import com.medtech.reviewhub.domain.model.ExplanationStyle
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.MascotKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings: theme, text scale, explanation style, notification prefs, focus
 * permissions status + links, API base URL (default empty = fully offline),
 * account sign-in + sync, data export, highlight color meanings, update check.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val authRepository: AuthRepository,
    private val flashcardRepository: FlashcardRepository,
    private val studyRepository: StudyRepository,
    private val loveNoteRepository: com.medtech.reviewhub.data.repo.LoveNoteRepository? = null,
    private val audioEngine: com.medtech.reviewhub.data.music.AudioEngine? = null,
    private val musicLibrary: com.medtech.reviewhub.data.music.MusicLibrary? = null,
    private val updateChecker: UpdateChecker = UpdateChecker(currentVersion = BuildConfig.VERSION_NAME)
) : ViewModel() {

    data class SettingsState(
        val settings: AppSettings = AppSettings(),
        val signedInEmail: String? = null,
        val updateInfo: UpdateChecker.UpdateInfo? = null,
        val updateCheckMessage: String? = null,
        val authMessage: String? = null,
        val exportJson: String? = null,
        val busy: Boolean = false
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.value = _state.value.copy(settings = s, signedInEmail = s.signedInEmail)
            }
        }
    }

    // ---- Appearance -----------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setAppTheme(themeId: String) = viewModelScope.launch { settings.setAppTheme(themeId) }
    fun setTextScale(percent: Int) = viewModelScope.launch { settings.setTextScale(percent) }
    fun setMascot(kind: MascotKind) = viewModelScope.launch { settings.setMascotKind(kind) }
    fun setMascotName(name: String) = viewModelScope.launch { settings.setMascotName(name) }
    fun setDisplayName(name: String) = viewModelScope.launch { settings.setDisplayName(name) }
    fun setGiftNote(note: String) = viewModelScope.launch { settings.setGiftNote(note) }
    fun setSeriousMode(enabled: Boolean) = viewModelScope.launch { settings.setSeriousMode(enabled) }
    fun setReduceAnimation(enabled: Boolean) = viewModelScope.launch { settings.setReduceAnimation(enabled) }
    fun setMuteMascot(enabled: Boolean) = viewModelScope.launch { settings.setMuteMascot(enabled) }
    fun setDisableCommentary(enabled: Boolean) = viewModelScope.launch { settings.setDisableCommentary(enabled) }
    fun setExplanationStyle(style: ExplanationStyle) = viewModelScope.launch {
        settings.currentProfile().let { settings.saveProfile(it.copy(explanationStyle = style)) }
    }

    // ---- Behavior -------------------------------------------------------

    fun setNotifications(enabled: Boolean) = viewModelScope.launch { settings.setNotificationsEnabled(enabled) }
    fun setFocusFeature(enabled: Boolean) = viewModelScope.launch { settings.setFocusFeatureEnabled(enabled) }
    fun setDailyLimits(newLimit: Int, reviewLimit: Int) = viewModelScope.launch {
        settings.setDailyLimits(newLimit, reviewLimit)
    }
    fun setRetention(value: Float) = viewModelScope.launch { settings.setRequestedRetention(value) }
    fun setHighlightMeaning(color: HighlightColor, meaning: String) = viewModelScope.launch {
        settings.setHighlightMeaning(color, meaning)
    }

    // ---- Study Music / Motivation (v1.2.0) --------------------------------

    fun setDefaultPlaylist(id: String) = viewModelScope.launch { settings.setDefaultPlaylist(id) }
    fun setMusicVolume(value: Float) = viewModelScope.launch { settings.setMusicVolume(value) }
    fun setAutoPlayMusicOnSession(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoPlayMusicOnSession(enabled)
    }
    fun setMusicDuringQuizzes(enabled: Boolean) = viewModelScope.launch { settings.setMusicDuringQuizzes(enabled) }
    fun setMusicDuringFlashcards(enabled: Boolean) = viewModelScope.launch { settings.setMusicDuringFlashcards(enabled) }
    fun setMusicDuringReading(enabled: Boolean) = viewModelScope.launch { settings.setMusicDuringReading(enabled) }
    fun setMusicDuringBreaks(enabled: Boolean) = viewModelScope.launch { settings.setMusicDuringBreaks(enabled) }
    fun setTimerChime(enabled: Boolean) = viewModelScope.launch { settings.setTimerChime(enabled) }
    fun setMotivationalBanners(enabled: Boolean) = viewModelScope.launch { settings.setMotivationalBanners(enabled) }
    fun setQuoteFrequency(frequency: String) = viewModelScope.launch { settings.setQuoteFrequency(frequency) }
    fun clearAudioDownloads() = viewModelScope.launch { audioEngine?.removeAll() }

    suspend fun audioStorageBytes(): Long = audioEngine?.totalBytes() ?: 0L

    fun playlistChoices(): List<Pair<String, String>> {
        val suggested = musicLibrary?.suggestedPlaylists.orEmpty().map { it.id to it.name }
        return listOf("" to "None") + suggested
    }

    // ---- Network / account ----------------------------------------------

    fun setApiBaseUrl(url: String) = viewModelScope.launch { settings.setApiBaseUrl(url) }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, authMessage = null)
            val result = authRepository.signIn(email, password)
            _state.value = _state.value.copy(
                busy = false,
                authMessage = when (result) {
                    is AuthRepository.AuthResult.Success -> "Signed in as ${result.email}."
                    is AuthRepository.AuthResult.Failure -> result.message
                    AuthRepository.AuthResult.Offline -> "Fully offline: set an API base URL in Settings first."
                }
            )
        }
    }

    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, authMessage = null)
            val result = authRepository.register(email, password, displayName)
            _state.value = _state.value.copy(
                busy = false,
                authMessage = when (result) {
                    is AuthRepository.AuthResult.Success -> "Account created for ${result.email}."
                    is AuthRepository.AuthResult.Failure -> result.message
                    AuthRepository.AuthResult.Offline -> "Fully offline: set an API base URL in Settings first."
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _state.value = _state.value.copy(authMessage = "Signed out.")
        }
    }

    // ---- Update check ----------------------------------------------------

    fun checkForUpdates(dismissedVersion: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, updateCheckMessage = null, updateInfo = null)
            when (val result = updateChecker.check(dismissedVersion ?: _state.value.settings.dismissedUpdateVersion)) {
                is UpdateChecker.Result.Available -> _state.value = _state.value.copy(
                    busy = false, updateInfo = result.info,
                    updateCheckMessage = "Update available: v${result.info.version}"
                )
                UpdateChecker.Result.UpToDate -> _state.value = _state.value.copy(
                    busy = false, updateCheckMessage = "You're on the latest version (v${BuildConfig.VERSION_NAME})."
                )
                is UpdateChecker.Result.Failure -> _state.value = _state.value.copy(
                    busy = false, updateCheckMessage = result.reason
                )
            }
        }
    }

    fun dismissUpdate(version: String) {
        viewModelScope.launch {
            settings.setDismissedUpdateVersion(version)
            _state.value = _state.value.copy(updateInfo = null)
        }
    }

    fun dismissUpdateDialog() {
        _state.value = _state.value.copy(updateInfo = null)
    }

    // ---- Data export ------------------------------------------------------

    fun exportData() {
        viewModelScope.launch {
            val cards = flashcardRepository.allCards()
            // "Little Notes for You" are private: included in this local export
            // only - never synced to the server (see SyncWorker).
            val notes = loveNoteRepository?.all().orEmpty()
            val json = buildString {
                append("{\n")
                append("  \"app\": \"MedTech Review Hub\",\n")
                append("  \"version\": \"${BuildConfig.VERSION_NAME}\",\n")
                append("  \"exportedAt\": \"${java.time.Instant.now()}\",\n")
                append("  \"flashcards\": [\n")
                cards.forEachIndexed { i, c ->
                    append("    {\"id\": \"${c.id}\", \"front\": ${c.front.json()}, \"back\": ${c.back.json()}, ")
                    append("\"deck\": \"${c.deckId}\", \"state\": \"${c.schedule.state}\", ")
                    append("\"stability\": ${c.schedule.stability}, \"dueAtMs\": ${c.schedule.dueAtMs}}")
                    if (i < cards.size - 1) append(",")
                    append("\n")
                }
                append("  ],\n")
                append("  \"loveNotes\": [\n")
                notes.forEachIndexed { i, n ->
                    append("    {\"id\": \"${n.id}\", \"text\": ${n.text.json()}, ")
                    append("\"showOnDate\": ${n.showOnDate?.json() ?: "null"}, \"favorite\": ${n.favorite}}")
                    if (i < notes.size - 1) append(",")
                    append("\n")
                }
                append("  ]\n")
                append("}\n")
            }
            _state.value = _state.value.copy(exportJson = json)
        }
    }

    private fun String.json(): String = "\"" + this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
