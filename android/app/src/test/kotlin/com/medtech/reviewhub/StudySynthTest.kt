package com.medtech.reviewhub

import com.medtech.reviewhub.domain.StudySynth
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StudySynth tests (shared/MUSIC.md): determinism (seeded mulberry32),
 * WAV header correctness (22050 Hz / 16-bit / mono / RIFF), non-silence for
 * every recipe, peak normalization + edge fades, and the gentle chime.
 */
class StudySynthTest {

    private val recipes = listOf(
        "piano-arpeggio", "violin-pad", "lofi-cafe", "classical-duet", "sleepy-pads",
        "deep-drone", "rain", "forest", "ocean", "wind",
        "white-noise", "brown-noise", "pink-noise", "night"
    )

    private fun spec(recipe: String, seed: Long = 42L) = when (recipe) {
        "piano-arpeggio" -> StudySynth.SynthSpec(
            recipe, seed, tempoBpm = 66, rootMidi = 60,
            progression = listOf(listOf(60, 64, 67), listOf(55, 59, 62))
        )
        "violin-pad" -> StudySynth.SynthSpec(
            recipe, seed, chords = listOf(listOf(57, 60, 64), listOf(53, 57, 60)), secondsPerChord = 2
        )
        "lofi-cafe" -> StudySynth.SynthSpec(
            recipe, seed, tempoBpm = 72,
            progression = listOf(listOf(60, 64, 67, 71), listOf(57, 60, 64, 67))
        )
        "classical-duet" -> StudySynth.SynthSpec(
            recipe, seed, tempoBpm = 76,
            progression = listOf(listOf(60, 64, 67), listOf(67, 71, 74))
        )
        "sleepy-pads" -> StudySynth.SynthSpec(
            recipe, seed, chords = listOf(listOf(57, 60, 64)), secondsPerChord = 3
        )
        else -> StudySynth.SynthSpec(recipe, seed)
    }

    // ---- Determinism -----------------------------------------------------

    @Test
    fun `mulberry32 rng is deterministic and in range`() {
        val a = StudySynth.Rng(12345)
        val b = StudySynth.Rng(12345)
        repeat(64) {
            val x = a.next()
            assertEquals(x, b.next())
            assertTrue("rng output must be in [0,1)", x >= 0f && x < 1f)
        }
    }

    @Test
    fun `same seed produces identical first 256 samples for every recipe`() {
        for (recipe in recipes) {
            val s = spec(recipe)
            val first = StudySynth.render(s, 0.5)
            val second = StudySynth.render(s, 0.5)
            for (i in 0 until 256) {
                assertEquals("recipe=$recipe sample=$i", first[i], second[i], 0f)
            }
        }
    }

    @Test
    fun `different seeds produce different audio`() {
        // Piano is fully deterministic per MUSIC.md (no RNG in the formula);
        // seeded randomness drives the noise-based recipes.
        val a = StudySynth.render(spec("rain", seed = 1), 0.5)
        val b = StudySynth.render(spec("rain", seed = 2), 0.5)
        var diff = false
        for (i in 0 until 256) if (a[i] != b[i]) { diff = true; break }
        assertTrue("seeds 1 and 2 should differ", diff)
    }

    @Test
    fun `full render is byte-identical for identical input`() {
        val s = spec("rain", seed = 99)
        val bytesA = StudySynth.wavBytes(StudySynth.render(s, 0.5))
        val bytesB = StudySynth.wavBytes(StudySynth.render(s, 0.5))
        assertArrayEquals(bytesA, bytesB)
    }

    // ---- WAV header ------------------------------------------------------

    @Test
    fun `wav bytes carry RIFF and WAVE magic`() {
        val wav = StudySynth.wavBytes(StudySynth.render(spec("white-noise"), 0.2))
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }

    @Test
    fun `wav header declares 22050 Hz 16-bit mono`() {
        val samples = FloatArray(100)
        val wav = StudySynth.wavBytes(samples)
        fun le16(off: Int): Int = (wav[off].toInt() and 0xFF) or ((wav[off + 1].toInt() and 0xFF) shl 8)
        fun le32(off: Int): Int =
            (wav[off].toInt() and 0xFF) or ((wav[off + 1].toInt() and 0xFF) shl 8) or
                ((wav[off + 2].toInt() and 0xFF) shl 16) or ((wav[off + 3].toInt() and 0xFF) shl 24)
        assertEquals(1, le16(20)) // PCM
        assertEquals(1, le16(22)) // mono
        assertEquals(22050, le32(24))
        assertEquals(16, le16(34))
        assertEquals(22050 * 2, le32(28)) // byte rate
        assertEquals(2, le16(32)) // block align
    }

    @Test
    fun `wav data chunk matches sample count`() {
        val samples = FloatArray(500) { 0.1f }
        val wav = StudySynth.wavBytes(samples)
        assertEquals(44 + 500 * 2, wav.size)
        val dataSize = (wav[40].toInt() and 0xFF) or ((wav[41].toInt() and 0xFF) shl 8) or
            ((wav[42].toInt() and 0xFF) shl 16) or ((wav[43].toInt() and 0xFF) shl 24)
        assertEquals(500 * 2, dataSize)
    }

    // ---- Audio content ---------------------------------------------------

    @Test
    fun `every recipe produces non-silent audio`() {
        for (recipe in recipes) {
            val samples = StudySynth.render(spec(recipe), 1.0)
            val energy = samples.sumOf { (it * it).toDouble() }
            assertTrue("recipe=$recipe should not be silent (energy=$energy)", energy > 0.0)
        }
    }

    @Test
    fun `output peaks at 0_85 and fades at the edges`() {
        // White noise has energy everywhere, so the normalized peak must sit at
        // the 0.85 target outside the 0.15 s fade windows.
        val noise = StudySynth.render(spec("white-noise"), 1.0)
        var peak = 0f
        for (v in noise) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        assertTrue("peak $peak should be <= 0.85 (+float epsilon)", peak <= 0.8501f)
        assertTrue("peak $peak should reach the normalization target", peak >= 0.80f)

        val samples = StudySynth.render(spec("piano-arpeggio"), 1.0)
        assertEquals(0f, samples[0], 0.0001f) // fade-in starts at silence
        assertEquals(0f, samples[samples.size - 1], 0.0001f) // fade-out ends at silence
    }

    @Test
    fun `midi frequency formula matches 440 Hz at A4`() {
        assertEquals(440.0, StudySynth.midiFreq(69), 0.0001)
        assertEquals(261.6255, StudySynth.midiFreq(60), 0.001)
    }

    // ---- Chime -----------------------------------------------------------

    @Test
    fun `chime wav is valid and non-silent`() {
        val wav = StudySynth.chimeWav()
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('W'.code.toByte(), wav[8])
        val samples = StudySynth.chimeSamples()
        val energy = samples.sumOf { (it * it).toDouble() }
        assertTrue("chime should not be silent", energy > 0.0)
        // Each pluck is amp 0.25 per shared/MUSIC.md; the two overlapping
        // plucks may sum to ~0.4, still gentle.
        assertTrue("chime stays gentle", samples.maxOf { kotlin.math.abs(it) } <= 0.5f)
    }

    @Test
    fun `chime is deterministic`() {
        assertTrue(StudySynth.chimeSamples().contentEquals(StudySynth.chimeSamples()))
    }
}
