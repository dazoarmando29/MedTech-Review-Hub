package com.medtech.reviewhub.data.music

import android.content.Context
import com.medtech.reviewhub.domain.StudySynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device audio generation cache. Audio is synthesized per shared/MUSIC.md
 * (original generative composition, CC0) into cacheDir/audio/<trackId>.wav the
 * first time a track is played and reused afterwards. "Download for offline"
 * is simply pre-generating this cache entry (the audio is always available
 * offline once generated); "Remove download" deletes the file.
 */
class AudioEngine(private val context: Context) {

    private val audioDir: File
        get() = File(context.cacheDir, "audio").apply { mkdirs() }

    fun wavFile(trackId: String): File = File(audioDir, "$trackId.wav")

    fun isDownloaded(trackId: String): Boolean = wavFile(trackId).let { it.exists() && it.length() > 44 }

    fun remove(trackId: String): Boolean = wavFile(trackId).delete()

    fun removeAll(): Int {
        val files = audioDir.listFiles().orEmpty()
        files.forEach { it.delete() }
        return files.size
    }

    /** Sum of generated WAV sizes (bytes) - shown as the storage estimate. */
    fun totalBytes(): Long = audioDir.listFiles().orEmpty().sumOf { it.length() }

    /**
     * Generates the track WAV if missing and returns the file. Deterministic
     * per track: same synth spec + seed -> identical PCM on every device.
     */
    suspend fun ensure(track: MusicTrackJson): File = withContext(Dispatchers.Default) {
        val file = wavFile(track.id)
        if (file.exists() && file.length() > 44) return@withContext file
        val samples = StudySynth.render(track.toSynthSpec(), track.durationSec.toDouble())
        val bytes = StudySynth.wavBytes(samples)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "${track.id}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.writeBytes(bytes)
                tmp.delete()
            }
        }
        file
    }

    /** Plays the gentle two-pluck chime through an AudioTrack (phase-end chime). */
    suspend fun ensureChime(): File = withContext(Dispatchers.Default) {
        val file = File(audioDir, "chime.wav")
        if (file.exists() && file.length() > 44) return@withContext file
        val bytes = StudySynth.chimeWav()
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        file
    }
}
