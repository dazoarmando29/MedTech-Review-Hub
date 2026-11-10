package com.medtech.reviewhub.data.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.medtech.reviewhub.domain.StudySynth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Plays the gentle synthesized two-pluck chime (shared/MUSIC.md) at Pomodoro
 * phase ends through an [AudioTrack]. Never loud: amp 0.25 as specified.
 */
class ChimePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun play() {
        scope.launch {
            runCatching {
                val samples = StudySynth.chimeSamples()
                val pcm = ShortArray(samples.size) { i ->
                    (samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                val minBuf = AudioTrack.getMinBufferSize(
                    StudySynth.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(StudySynth.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Release once playback has finished (short chime).
                Thread.sleep(1500)
                runCatching { track.stop() }
                track.release()
            }
        }
    }
}
