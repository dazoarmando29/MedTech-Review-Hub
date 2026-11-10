package com.medtech.reviewhub.ui.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.medtech.reviewhub.data.music.AudioEngine
import com.medtech.reviewhub.data.music.MusicLibrary
import com.medtech.reviewhub.data.music.MusicRepository
import com.medtech.reviewhub.data.music.MusicTrackJson
import com.medtech.reviewhub.data.music.PlaybackService
import com.medtech.reviewhub.data.music.UserPlaylist
import com.medtech.reviewhub.data.music.trackMediaItem
import com.medtech.reviewhub.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Study Music state: catalog browsing, playback (via a [MediaController]
 * connected to [PlaybackService]), favorites, personal playlists and the
 * on-device WAV "downloads" (pre-generated audio cache).
 */
class MusicViewModel(
    private val appContext: Context,
    val library: MusicLibrary,
    private val audioEngine: AudioEngine,
    private val musicRepository: MusicRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    data class PlayerUi(
        val connected: Boolean = false,
        val trackId: String? = null,
        val title: String = "",
        val artist: String = "",
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val shuffle: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val volume: Float = 1f,
        val muted: Boolean = false
    )

    data class MusicState(
        val loading: Boolean = true,
        val selectedCategory: String = "",
        val favorites: Set<String> = emptySet(),
        val playlists: List<UserPlaylist> = emptyList(),
        val generated: Set<String> = emptySet(),
        val generating: Set<String> = emptySet(),
        val storageBytes: Long = 0,
        val message: String? = null
    )

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    init {
        connect()
        viewModelScope.launch {
            musicRepository.favorites.collect { f -> _state.update { it.copy(favorites = f) } }
        }
        viewModelScope.launch {
            musicRepository.playlists.collect { p -> _state.update { it.copy(playlists = p) } }
        }
        viewModelScope.launch {
            val generated = withContext(Dispatchers.IO) {
                library.tracks.filter { audioEngine.isDownloaded(it.id) }.map { it.id }.toSet()
            }
            _state.update { it.copy(loading = false, generated = generated, storageBytes = storageBytes()) }
        }
    }

    // ---- Playback connection ---------------------------------------------

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val c = future.get()
                controller = c
                c.addListener(playerListener)
                syncFromPlayer()
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(appContext))
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
        }
    }

    private fun syncFromPlayer() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _player.update {
            it.copy(
                connected = true,
                trackId = item?.mediaId,
                title = item?.mediaMetadata?.title?.toString() ?: "",
                artist = item?.mediaMetadata?.artist?.toString() ?: "",
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition,
                durationMs = c.duration.coerceAtLeast(0),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                volume = c.volume
            )
        }
    }

    private suspend fun ensureController(): MediaController {
        controller?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val future = controllerFuture
            if (future == null) {
                connect()
                controllerFuture?.addListener({
                    runCatching { controller?.let { c -> cont.resume(c) } }
                }, androidx.core.content.ContextCompat.getMainExecutor(appContext))
            } else {
                future.addListener({
                    runCatching { future.get().let { c -> controller = c; cont.resume(c) } }
                }, androidx.core.content.ContextCompat.getMainExecutor(appContext))
            }
        }
    }

    /** Polls the real ExoPlayer position for the seek bar. */
    fun refreshPosition() {
        val c = controller ?: return
        _player.update { it.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0), isPlaying = c.isPlaying) }
    }

    // ---- Play actions ----------------------------------------------------

    fun playTracks(tracks: List<MusicTrackJson>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val items = ArrayList<MediaItem>(tracks.size)
            for (t in tracks) {
                _state.update { it.copy(generating = it.generating + t.id) }
                val file = runCatching { audioEngine.ensure(t) }.getOrNull()
                _state.update { it.copy(generating = it.generating - t.id) }
                if (file == null) {
                    _state.update { it.copy(message = "Could not generate ${t.title}.") }
                    continue
                }
                items.add(trackMediaItem(t, Uri.fromFile(file)))
            }
            if (items.isEmpty()) return@launch
            _state.update {
                it.copy(
                    generated = it.generated + tracks.map { t -> t.id },
                    storageBytes = storageBytes()
                )
            }
            val c = ensureController()
            c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            applyVolume(c)
            c.play()
            markUserInteracted()
        }
    }

    fun playTrack(track: MusicTrackJson, context: List<MusicTrackJson>) {
        val index = context.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTracks(context, index)
    }

    fun playCategory(categoryId: String) = playTracks(library.tracksIn(categoryId))

    fun playSuggested(playlistId: String) {
        val pl = library.suggestedPlaylists.firstOrNull { it.id == playlistId } ?: return
        playTracks(pl.trackIds.mapNotNull { library.track(it) })
    }

    fun playUserPlaylist(playlist: UserPlaylist) {
        playTracks(playlist.trackIds.mapNotNull { library.track(it) })
    }

    /** "Morning study" / "late-night review" convenience picks. */
    fun playQuickPick(kind: String) {
        when (kind) {
            "morning" -> playSuggested("pink-study")
            "late-night" -> playSuggested("midnight-review")
            else -> playCategory("lofi-cafe")
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            // 200 ms fade-out before pausing, then restore the user volume.
            viewModelScope.launch {
                val target = _player.value.volume
                val start = c.volume
                repeat(10) { i ->
                    c.volume = start * (1f - (i + 1) / 10f)
                    kotlinx.coroutines.delay(20)
                }
                c.pause()
                c.volume = target
                applyVolume(c)
                syncFromPlayer()
            }
        } else {
            applyVolume(c)
            c.play()
            markUserInteracted()
        }
    }

    fun next() {
        controller?.let {
            it.seekToNextMediaItem()
            applyVolume(it)
        }
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
        applyVolume(c)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        refreshPosition()
    }

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _player.update { it.copy(volume = clamped, muted = clamped == 0f) }
        controller?.let {
            it.volume = clamped
        }
    }

    fun toggleMute() {
        val muted = _player.value.muted
        setVolume(if (muted) 1f else 0f)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        syncFromPlayer()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncFromPlayer()
    }

    private fun applyVolume(c: MediaController) {
        c.volume = if (_player.value.muted) 0f else _player.value.volume
    }

    private fun markUserInteracted() {
        viewModelScope.launch { settings.setMusicUserInteracted(true) }
    }

    // ---- Favorites / playlists / downloads --------------------------------

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch { musicRepository.toggleFavorite(trackId) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { musicRepository.createPlaylist(name) }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { musicRepository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { musicRepository.deletePlaylist(id) }
    }

    fun addToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { musicRepository.addTrack(playlistId, trackId) }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch { musicRepository.removeTrack(playlistId, trackId) }
    }

    /** "Download for offline" = pre-generate the WAV cache entry. */
    fun generateDownload(track: MusicTrackJson) {
        viewModelScope.launch {
            _state.update { it.copy(generating = it.generating + track.id) }
            runCatching { audioEngine.ensure(track) }
            _state.update {
                it.copy(
                    generating = it.generating - track.id,
                    generated = it.generated + track.id,
                    storageBytes = storageBytes()
                )
            }
        }
    }

    fun removeDownload(trackId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { audioEngine.remove(trackId) }
            _state.update {
                it.copy(generated = it.generated - trackId, storageBytes = storageBytes())
            }
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { audioEngine.removeAll() }
            _state.update { it.copy(generated = emptySet(), storageBytes = 0) }
        }
    }

    private suspend fun storageBytes(): Long = withContext(Dispatchers.IO) { audioEngine.totalBytes() }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.cancel(true)
        runCatching { controller?.release() }
        controller = null
        super.onCleared()
    }
}
