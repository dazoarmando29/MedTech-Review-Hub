package com.medtech.reviewhub.data.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Study-music playback service (Media3 [MediaSessionService]).
 *
 * - One ExoPlayer instance: exactly one track plays at a time.
 * - MediaSession + foreground notification with lock-screen controls
 *   (play/pause/next/prev) - managed by media3's DefaultMediaNotificationProvider.
 * - Audio focus via ExoPlayer's AudioFocus handling (pause on loss, duck on
 *   transient loss); OEM behavior can vary (documented in README).
 * - ACTION_AUDIO_BECOMING_NOISY: pause when headphones are unplugged.
 * - 200 ms volume fade-in on play/track-change (never a sudden spike).
 *   Pause from the app UI fades out over 200 ms before pausing (the controller
 *   ramps the player volume); pause from the notification/lock screen is an
 *   immediate pause (a pause is never a spike). Documented simplification.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    /** Last non-faded user volume, so fades always return to it. */
    private var userVolume = 1f
    private var fading = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player?.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val exo = ExoPlayer.Builder(this)
            // handleAudioFocus = true: pauses on focus loss, ducks on transient loss.
            .setAudioAttributes(attrs, true)
            .build()
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) fadeIn()
                else exo.volume = userVolume // ready for the next fade-in
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                ) {
                    if (exo.isPlaying) fadeIn()
                }
            }

            override fun onVolumeChanged(volume: Float) {
                if (!fading && volume > 0f) userVolume = volume
            }
        })
        mediaSession = MediaSession.Builder(this, exo).build()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Clean stop when the app is swiped away - no hidden background playback.
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        mediaSession?.let { session ->
            player?.release()
            session.release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    /** 200 ms fade-in from silence to the user volume. */
    private fun fadeIn() {
        val p = player ?: return
        fading = true
        val target = userVolume
        val steps = 10
        p.volume = 0f
        val handler = android.os.Handler(mainLooper)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                p.volume = target * (step.toFloat() / steps).coerceIn(0f, 1f)
                if (step < steps) handler.postDelayed(this, 20L) else fading = false
            }
        }
        handler.postDelayed(runnable, 20L)
    }
}

/** Builds the MediaItem for a generated track WAV (metadata feeds the notification). */
fun trackMediaItem(track: MusicTrackJson, uri: android.net.Uri): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle("Study Music")
                .build()
        )
        .build()
