package com.medtech.reviewhub.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * StudySynth - deterministic generative audio per shared/MUSIC.md.
 *
 * Pure Kotlin (no Android dependencies) so it unit-tests on the JVM. Every
 * track is synthesized on-device from a seeded mulberry32 RNG: identical PCM
 * output on every device. Output format: 22050 Hz mono 16-bit PCM WAV.
 *
 * Small documented numeric deviations from shared/MUSIC.md (the spec leaves a
 * few amplitudes/envelope shapes open; choices below are deterministic and
 * musical):
 *  - piano bass sine amplitude 0.45 (spec gives tau 2.2 s but no amplitude).
 *  - arpeggio "up then down" cycling uses a triangle index pattern
 *    (0,1,2,1,0,1,2,1...) over the chord tones.
 *  - note render length = 3 * tau (spec does not state a cutoff).
 *  - violin-pad attack/release envelopes are linear ramps (spec gives the
 *    durations but not the shape).
 *  - lofi-cafe pad attack/release 0.5 s each (not specified).
 *  - classical-duet: one chord per bar; voice B alternates the first two chord
 *    tones as half notes (spec says "sustained half notes" without pitches).
 *  - forest rustle amplitude 0.03 with a 0.13 Hz LFO (spec says "band-passed
 *    noise, LFO'd amp" without numbers).
 *  - "light delay reverb" (lofi-cafe) uses the same delay reverb as the other
 *    recipes (spec does not parameterize it separately).
 *  - chime: the second pluck (1320 Hz) starts 0.25 s after the first (spec
 *    says "880 Hz then 1320 Hz" without a gap length).
 * Everything else follows the formulas exactly.
 */
object StudySynth {

    const val SAMPLE_RATE = 22050
    const val FADE_SEC = 0.15
    const val TARGET_PEAK = 0.85f

    // ---- RNG (mulberry32, exactly as shared/MUSIC.md) ---------------------

    class Rng(seed: Long) {
        private var a: Int = seed.toInt()

        /** Uniform float in [0, 1). */
        fun next(): Float {
            a += 0x6D2B79F5
            var t = imul(a xor (a ushr 15), 1 or a)
            t = (t + imul(t xor (t ushr 7), 61 or t)) xor t
            return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat()
        }

        /** Uniform double in [lo, hi). */
        fun range(lo: Double, hi: Double): Double = lo + next() * (hi - lo)

        companion object {
            /** JS Math.imul: 32-bit wrapping integer multiply. */
            private fun imul(x: Int, y: Int): Int = x * y
        }
    }

    // ---- Filters (one-pole, exactly as shared/MUSIC.md) -------------------

    class LowPass(private val alpha: Float) {
        private var y = 0f
        fun process(x: Float): Float {
            y += alpha * (x - y)
            return y
        }
    }

    class HighPass(private val alpha: Float) {
        private var y = 0f
        private var prev = 0f
        fun process(x: Float): Float {
            y = alpha * (y + x - prev)
            prev = x
            return y
        }
    }

    /** Delay reverb: buf[n] = x[n] + fb*buf[n-d]; out = x + mix*buf (spec formula). */
    class DelayReverb(sampleRate: Int = SAMPLE_RATE) {
        private val d = (0.28 * sampleRate).toInt()
        private val buf = FloatArray(d)
        private var i = 0

        fun process(x: Float): Float {
            val delayed = buf[i]
            val v = x + FB * delayed
            buf[i] = v
            i = (i + 1) % d
            return x + MIX * v
        }

        companion object {
            const val FB = 0.35f
            const val MIX = 0.25f
        }
    }

    // ---- Synth spec (mapped from shared/music/catalog.json) ---------------

    data class SynthSpec(
        val recipe: String,
        val seed: Long,
        val tempoBpm: Int = 66,
        val rootMidi: Int = 60,
        val progression: List<List<Int>> = emptyList(),
        val chords: List<List<Int>> = emptyList(),
        val secondsPerChord: Int = 8
    )

    fun midiFreq(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    // ---- Main entry -------------------------------------------------------

    /** Renders [durationSec] of audio for [spec]. Deterministic per (spec, duration). */
    fun render(spec: SynthSpec, durationSec: Double): FloatArray {
        val n = (durationSec * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val buf = FloatArray(n)
        val rng = Rng(spec.seed)
        when (spec.recipe) {
            "piano-arpeggio" -> pianoArpeggio(buf, rng, spec)
            "violin-pad" -> violinPad(buf, rng, spec)
            "lofi-cafe" -> lofiCafe(buf, rng, spec)
            "classical-duet" -> classicalDuet(buf, rng, spec)
            "sleepy-pads" -> sleepyPads(buf, rng, spec)
            "deep-drone" -> deepDrone(buf, rng)
            "rain" -> rain(buf, rng)
            "forest" -> forest(buf, rng)
            "ocean" -> ocean(buf, rng)
            "wind" -> wind(buf, rng)
            "white-noise" -> whiteNoise(buf, rng)
            "brown-noise" -> brownNoise(buf, rng)
            "pink-noise" -> pinkNoise(buf, rng)
            "night" -> night(buf, rng)
            else -> whiteNoise(buf, rng)
        }
        return finish(buf)
    }

    /** Gentle two-pluck chime: 880 Hz then 1320 Hz, tau 0.6 s, amp 0.25. */
    fun chimeSamples(): FloatArray {
        val totalSec = 1.35
        val n = (totalSec * SAMPLE_RATE).toInt()
        val buf = FloatArray(n)
        addPluck(buf, 0, 880.0, tau = 0.6, amp = 0.25f)
        // Deviation: second pluck starts 0.25 s after the first ("then").
        addPluck(buf, (0.25 * SAMPLE_RATE).toInt(), 1320.0, tau = 0.6, amp = 0.25f)
        return finish(buf, normalize = false)
    }

    fun chimeWav(): ByteArray = wavBytes(chimeSamples())

    // ---- Recipes ----------------------------------------------------------

    /**
     * piano-arpeggio: bar = 4 beats (beat = 60/tempo), each chord lasts 2 bars,
     * eighth-note plucks cycle chord tones up then down. Pluck =
     * sum(k=1..4) sin(2*pi*f*k*t)/k^1.5 * exp(-t/1.1). Bass sine f(root)/2,
     * tau 2.2 s on each bar start. Through delay reverb.
     */
    private fun pianoArpeggio(buf: FloatArray, rng: Rng, spec: SynthSpec) {
        val progression = spec.progression.ifEmpty { listOf(listOf(60, 64, 67)) }
        val beat = 60.0 / spec.tempoBpm
        val bar = 4 * beat
        val chordBars = 2.0
        val eighth = beat / 2.0
        var chordIdx = 0
        var t = 0.0
        while (t < buf.size.toDouble() / SAMPLE_RATE) {
            val chord = progression[chordIdx % progression.size]
            val chordStart = t
            // 16 eighth notes per 2-bar chord window.
            var k = 0
            while (k < 16) {
                val noteT = chordStart + k * eighth
                val tone = chord[triangleIndex(k, chord.size)]
                addPluck(buf, (noteT * SAMPLE_RATE).toInt(), midiFreq(tone), tau = 1.1, amp = 0.5f)
                k++
            }
            // Bass on every bar start of the chord window.
            var barStart = chordStart
            repeat(chordBars.toInt()) {
                addSine(buf, (barStart * SAMPLE_RATE).toInt(), midiFreq(spec.rootMidi) / 2.0, tau = 2.2, amp = 0.45f)
                barStart += bar
            }
            t += chordBars * bar
            chordIdx++
        }
        reverb(buf)
    }

    /**
     * violin-pad: chords of 3 midi, secondsPerChord (8 s). Voice =
     * sum(k=1..6) sin(2*pi*f*k*t + 0.003*sin(2*pi*5*t)) / k; attack 2.5 s,
     * release 2 s per chord; low-pass alpha 0.06; delay reverb.
     */
    private fun violinPad(buf: FloatArray, rng: Rng, spec: SynthSpec) {
        val chords = spec.chords.ifEmpty { listOf(listOf(57, 60, 64)) }
        val secPerChord = spec.secondsPerChord.coerceAtLeast(1)
        val total = buf.size.toDouble() / SAMPLE_RATE
        var chordIdx = 0
        var t = 0.0
        while (t < total) {
            val chord = chords[chordIdx % chords.size]
            val len = minOf(secPerChord.toDouble(), total - t)
            for (tone in chord) {
                val f = midiFreq(tone)
                addEvent(buf, (t * SAMPLE_RATE).toInt(), len) { tt ->
                    var s = 0.0
                    for (k in 1..6) {
                        s += sin(2 * PI * f * k * tt + 0.003 * sin(2 * PI * 5 * tt)) / k
                    }
                    s * attackRelease(tt, len, attack = 2.5, release = 2.0) / 3.0
                }
            }
            t += secPerChord
            chordIdx++
        }
        lowPass(buf, 0.06f)
        reverb(buf)
    }

    /**
     * lofi-cafe: pads = 3 detuned saws (+/-4 Hz; saw = sum(k=1..10)
     * sin(2*pi*f*k*t)/k), low-pass alpha 0.05, chord every 2 bars. Kick:
     * sin(2*pi*55*t)*exp(-t/0.12) on beats 1 & 3, amp 0.5. Hats: high-passed
     * noise burst 20 ms on off-beats, amp 0.05. Crackle: rng ticks
     * (p = 0.004/sample, amp 0.02). Light delay reverb.
     */
    private fun lofiCafe(buf: FloatArray, rng: Rng, spec: SynthSpec) {
        val progression = spec.progression.ifEmpty { listOf(listOf(60, 64, 67, 71)) }
        val beat = 60.0 / spec.tempoBpm
        val bar = 4 * beat
        val total = buf.size.toDouble() / SAMPLE_RATE

        // Pads: chord every 2 bars.
        var chordIdx = 0
        var t = 0.0
        while (t < total) {
            val chord = progression[chordIdx % progression.size]
            val len = minOf(2 * bar, total - t)
            for (tone in chord) {
                val f = midiFreq(tone)
                for (detune in doubleArrayOf(-4.0, 0.0, 4.0)) {
                    val fd = f + detune
                    addEvent(buf, (t * SAMPLE_RATE).toInt(), len) { tt ->
                        var s = 0.0
                        for (k in 1..10) s += sin(2 * PI * fd * k * tt) / k
                        s * attackRelease(tt, len, attack = 0.5, release = 0.5) / 12.0
                    }
                }
            }
            t += 2 * bar
            chordIdx++
        }
        lowPass(buf, 0.05f)

        // Kick on beats 1 & 3 of every bar.
        t = 0.0
        while (t < total) {
            for (beatNo in intArrayOf(0, 2)) {
                val kt = t + beatNo * beat
                if (kt < total) addPluck(buf, (kt * SAMPLE_RATE).toInt(), 55.0, tau = 0.12, amp = 0.5f)
            }
            t += bar
        }

        // Hats: 20 ms high-passed noise on eighth off-beats.
        t = beat / 2.0
        while (t < total) {
            val start = (t * SAMPLE_RATE).toInt()
            val hp = HighPass(0.86f)
            val len = (0.02 * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= buf.size) break
                buf[idx] += hp.process(rng.next() * 2f - 1f) * 0.05f
            }
            t += beat
        }

        // Crackle ticks.
        for (i in buf.indices) {
            if (rng.next() < 0.004f) {
                buf[i] += (rng.next() * 2f - 1f) * 0.02f
            }
        }
        reverb(buf)
    }

    /**
     * classical-duet: voice A = triangle-ish (sin + 0.08*sin(9*theta)) eighth
     * arpeggio, tau 0.9 s. Voice B = sustained half notes
     * (sum(k=1..3) sin(k*theta) * [1,0.4,0.15][k]), tau 1.8 s. Reverb mix 0.2.
     */
    private fun classicalDuet(buf: FloatArray, rng: Rng, spec: SynthSpec) {
        val progression = spec.progression.ifEmpty { listOf(listOf(60, 64, 67)) }
        val beat = 60.0 / spec.tempoBpm
        val bar = 4 * beat
        val eighth = beat / 2.0
        val total = buf.size.toDouble() / SAMPLE_RATE

        var chordIdx = 0
        var t = 0.0
        while (t < total) {
            val chord = progression[chordIdx % progression.size]
            // Voice A: eighth-note arpeggio over the bar.
            for (k in 0 until 8) {
                val noteT = t + k * eighth
                if (noteT >= total) break
                val tone = chord[triangleIndex(k, chord.size)]
                val f = midiFreq(tone)
                addEvent(buf, (noteT * SAMPLE_RATE).toInt(), 3 * 0.9) { tt ->
                    val theta = 2 * PI * f * tt
                    (sin(theta) + 0.08 * sin(9 * theta)) * exp(-tt / 0.9) * 0.45
                }
            }
            // Voice B: half notes on beats 1 & 3.
            for (half in intArrayOf(0, 2)) {
                val noteT = t + half * beat
                if (noteT >= total) break
                val tone = chord[if (half == 0) 0 else 1]
                val f = midiFreq(tone)
                addEvent(buf, (noteT * SAMPLE_RATE).toInt(), 3 * 1.8) { tt ->
                    var s = 0.0
                    val amps = doubleArrayOf(1.0, 0.4, 0.15)
                    for (k in 1..3) s += sin(k * 2 * PI * f * tt) * amps[k - 1]
                    s * exp(-tt / 1.8) * 0.35
                }
            }
            t += bar
            chordIdx++
        }
        reverb(buf, mix = 0.2f)
    }

    /**
     * sleepy-pads: chords of 3 midi, 12 s per chord. Sine pad
     * sum(k=1..3) sin(2*pi*f*k*t) * [1,0.28,0.1][k], attack 4 s, release 3.5 s.
     * Sparse pluck accents (rng, p = 0.02/s, tau 2.8 s).
     */
    private fun sleepyPads(buf: FloatArray, rng: Rng, spec: SynthSpec) {
        val chords = spec.chords.ifEmpty { listOf(listOf(57, 60, 64)) }
        val secPerChord = spec.secondsPerChord.coerceAtLeast(1)
        val total = buf.size.toDouble() / SAMPLE_RATE
        var chordIdx = 0
        var t = 0.0
        while (t < total) {
            val chord = chords[chordIdx % chords.size]
            val len = minOf(secPerChord.toDouble(), total - t)
            for (tone in chord) {
                val f = midiFreq(tone)
                addEvent(buf, (t * SAMPLE_RATE).toInt(), len) { tt ->
                    var s = 0.0
                    val amps = doubleArrayOf(1.0, 0.28, 0.1)
                    for (k in 1..3) s += sin(2 * PI * f * k * tt) * amps[k - 1]
                    s * attackRelease(tt, len, attack = 4.0, release = 3.5) / 3.0
                }
            }
            t += secPerChord
            chordIdx++
        }
        // Sparse pluck accents: p = 0.02 per second.
        var pt = 0.0
        while (pt < total) {
            if (rng.next() < 0.02f) {
                val chord = chords[(rng.next() * chords.size).toInt().coerceIn(0, chords.size - 1)]
                val tone = chord[(rng.next() * chord.size).toInt().coerceIn(0, chord.size - 1)]
                addPluck(buf, (pt * SAMPLE_RATE).toInt(), midiFreq(tone), tau = 2.8, amp = 0.25f)
            }
            pt += 1.0
        }
    }

    /**
     * deep-drone: sines 110 / 110.35 / 165 / 220.5 Hz at amps
     * 0.35 / 0.30 / 0.20 / 0.12; swell 0.75 + 0.25*sin(2*pi*0.02*t);
     * pink-noise floor amp 0.015.
     */
    private fun deepDrone(buf: FloatArray, rng: Rng) {
        val freqs = doubleArrayOf(110.0, 110.35, 165.0, 220.5)
        val amps = doubleArrayOf(0.35, 0.30, 0.20, 0.12)
        for (i in buf.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val swell = 0.75 + 0.25 * sin(2 * PI * 0.02 * t)
            var s = 0.0
            for (f in freqs.indices) s += sin(2 * PI * freqs[f] * t) * amps[f]
            buf[i] = (s * swell).toFloat()
        }
        // Pink-noise floor (same construction as the pink-noise recipe).
        val lp = LowPass(0.02f)
        for (i in buf.indices) {
            val white = rng.next() * 2f - 1f
            buf[i] += (lp.process(white) + white * 0.15f) * 0.015f
        }
    }

    /**
     * rain: white noise high-pass alpha 0.86, amp 0.22 + droplets: every
     * U(0.05, 0.3) s a burst sin(2*pi*U(2000,4200)*t)*exp(-t/0.045)
     * amp U(0.05, 0.22).
     */
    private fun rain(buf: FloatArray, rng: Rng) {
        val hp = HighPass(0.86f)
        for (i in buf.indices) {
            buf[i] = hp.process(rng.next() * 2f - 1f) * 0.22f
        }
        val total = buf.size.toDouble() / SAMPLE_RATE
        var t = rng.range(0.05, 0.3)
        while (t < total) {
            val freq = rng.range(2000.0, 4200.0)
            val amp = rng.range(0.05, 0.22).toFloat()
            addPluck(buf, (t * SAMPLE_RATE).toInt(), freq, tau = 0.045, amp = amp, lengthSec = 0.12)
            t += rng.range(0.05, 0.3)
        }
    }

    /**
     * forest: pink noise amp 0.06; bird chirps every U(2, 7) s: 2-4 repeats of
     * a 120 ms sine sweeping 2800 -> 3400 Hz, amp 0.12; soft rustle
     * (band-passed noise, LFO'd amp).
     */
    private fun forest(buf: FloatArray, rng: Rng) {
        val lp = LowPass(0.02f)
        for (i in buf.indices) {
            val white = rng.next() * 2f - 1f
            buf[i] = (lp.process(white) + white * 0.15f) * 0.06f
        }
        // Soft rustle: band-passed noise with LFO'd amplitude (documented
        // deviation: amp 0.03, LFO 0.13 Hz - the spec gives no numbers).
        val hp = HighPass(0.1f)
        val lp2 = LowPass(0.2f)
        for (i in buf.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val lfo = 0.5 + 0.5 * sin(2 * PI * 0.13 * t)
            buf[i] += (lp2.process(hp.process(rng.next() * 2f - 1f)) * lfo * 0.03).toFloat()
        }
        val total = buf.size.toDouble() / SAMPLE_RATE
        var t = rng.range(2.0, 7.0)
        while (t < total) {
            val repeats = 2 + (rng.next() * 3).toInt() // 2..4 repeats
            for (r in 0 until repeats) {
                val start = t + r * 0.15
                if (start >= total) break
                val startSample = (start * SAMPLE_RATE).toInt()
                addEvent(buf, startSample, 0.12) { tt ->
                    val sweep = 2800.0 + (3400.0 - 2800.0) * (tt / 0.12)
                    sin(2 * PI * sweep * tt) * 0.12
                }
            }
            t += rng.range(2.0, 7.0)
        }
    }

    /**
     * ocean: brown noise * (0.45 + 0.4*sin(2*pi*t/9)), low-pass alpha 0.03; a
     * wave accent every 9 s (noise burst, 1.2 s attack/decay, amp 0.18).
     */
    private fun ocean(buf: FloatArray, rng: Rng) {
        var y = 0f
        val lp = LowPass(0.03f)
        for (i in buf.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            y += (rng.next() - 0.5f) * 0.02f
            buf[i] = lp.process(y * (0.45f + 0.4f * sin(2 * PI * t / 9.0).toFloat()))
        }
        val total = buf.size.toDouble() / SAMPLE_RATE
        var t = 0.0
        while (t < total) {
            val start = (t * SAMPLE_RATE).toInt()
            val len = (1.2 * SAMPLE_RATE).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= buf.size) break
                val env = (i.toDouble() / len) // attack/decay triangle
                val shape = if (env < 0.5) env * 2 else (1.0 - env) * 2
                buf[idx] += (rng.next() * 2f - 1f) * 0.18f * shape.toFloat()
            }
            t += 9.0
        }
    }

    /**
     * wind: white noise through a low-pass with alpha
     * 0.02 + 0.015*sin(2*pi*0.05*t); amplitude 0.6 + 0.4*sin(2*pi*0.07*t + 1).
     */
    private fun wind(buf: FloatArray, rng: Rng) {
        var y = 0f
        for (i in buf.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val alpha = (0.02 + 0.015 * sin(2 * PI * 0.05 * t)).toFloat()
            val x = rng.next() * 2f - 1f
            y += alpha * (x - y)
            val amp = (0.6 + 0.4 * sin(2 * PI * 0.07 * t + 1)).toFloat()
            buf[i] = y * amp
        }
    }

    /** white-noise: 2*rng - 1, amp 0.5. */
    private fun whiteNoise(buf: FloatArray, rng: Rng) {
        for (i in buf.indices) buf[i] = (rng.next() * 2f - 1f) * 0.5f
    }

    /** brown-noise: y[n] = y[n-1] + (rng - 0.5)*0.02, normalized at the end. */
    private fun brownNoise(buf: FloatArray, rng: Rng) {
        var y = 0f
        for (i in buf.indices) {
            y += (rng.next() - 0.5f) * 0.02f
            buf[i] = y
        }
    }

    /** pink-noise: white through low-pass alpha 0.02 + white*0.15. */
    private fun pinkNoise(buf: FloatArray, rng: Rng) {
        val lp = LowPass(0.02f)
        for (i in buf.indices) {
            val white = rng.next() * 2f - 1f
            buf[i] = lp.process(white) + white * 0.15f
        }
    }

    /**
     * night: pink amp 0.04 + crickets every U(0.8, 2.5) s: 5 bursts of
     * sin(2*pi*4300*t)*exp(-t/0.012) spaced 45 ms, amp 0.1.
     */
    private fun night(buf: FloatArray, rng: Rng) {
        val lp = LowPass(0.02f)
        for (i in buf.indices) {
            val white = rng.next() * 2f - 1f
            buf[i] = (lp.process(white) + white * 0.15f) * 0.04f
        }
        val total = buf.size.toDouble() / SAMPLE_RATE
        var t = rng.range(0.8, 2.5)
        while (t < total) {
            for (b in 0 until 5) {
                val bt = t + b * 0.045
                if (bt >= total) break
                addPluck(buf, (bt * SAMPLE_RATE).toInt(), 4300.0, tau = 0.012, amp = 0.1f, lengthSec = 0.05)
            }
            t += rng.range(0.8, 2.5)
        }
    }

    // ---- Note helpers -----------------------------------------------------

    /** Decaying sine pluck: sin(2*pi*f*t) * exp(-t/tau) * amp. */
    private fun addPluck(
        buf: FloatArray,
        startSample: Int,
        freq: Double,
        tau: Double,
        amp: Float,
        lengthSec: Double = 3 * tau
    ) {
        addEvent(buf, startSample, lengthSec) { t -> sin(2 * PI * freq * t) * exp(-t / tau) * amp }
    }

    private fun addSine(buf: FloatArray, startSample: Int, freq: Double, tau: Double, amp: Float) {
        addPluck(buf, startSample, freq, tau, amp)
    }

    private inline fun addEvent(buf: FloatArray, startSample: Int, lengthSec: Double, fn: (Double) -> Double) {
        val len = (lengthSec * SAMPLE_RATE).toInt()
        val start = startSample.coerceAtLeast(0)
        val end = minOf(buf.size, start + len)
        for (i in start until end) {
            buf[i] += fn((i - start).toDouble() / SAMPLE_RATE).toFloat()
        }
    }

    /** attack/release envelope (linear ramps; deviation documented above). */
    private fun attackRelease(t: Double, len: Double, attack: Double, release: Double): Double {
        val a = if (t < attack) t / attack else 1.0
        val r = if (t > len - release) ((len - t) / release).coerceIn(0.0, 1.0) else 1.0
        return a.coerceIn(0.0, 1.0) * r
    }

    /** Triangle index pattern for "up then down" chord-tone cycling. */
    private fun triangleIndex(step: Int, size: Int): Int {
        if (size <= 1) return 0
        val period = 2 * size - 2
        val p = Math.floorMod(step, period)
        return if (p < size) p else period - p
    }

    // ---- Post-processing / WAV -------------------------------------------

    private fun lowPass(buf: FloatArray, alpha: Float) {
        val f = LowPass(alpha)
        for (i in buf.indices) buf[i] = f.process(buf[i])
    }

    private fun reverb(buf: FloatArray, mix: Float = DelayReverb.MIX) {
        // Delay reverb with custom mix: the shared/MUSIC.md formula inline.
        val d = (0.28 * SAMPLE_RATE).toInt()
        val delayBuf = FloatArray(d)
        var idx = 0
        for (i in buf.indices) {
            val x = buf[i]
            val v = x + DelayReverb.FB * delayBuf[idx]
            delayBuf[idx] = v
            idx = (idx + 1) % d
            buf[i] = x + mix * v
        }
    }

    /** Normalize to peak 0.85 + 0.15 s fade in/out (unless [normalize] is false). */
    private fun finish(buf: FloatArray, normalize: Boolean = true): FloatArray {
        if (normalize) {
            var peak = 0f
            for (v in buf) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
            if (peak > 0f) {
                val gain = TARGET_PEAK / peak
                for (i in buf.indices) buf[i] *= gain
            }
        }
        val fade = (FADE_SEC * SAMPLE_RATE).toInt().coerceAtMost(buf.size / 2)
        for (i in 0 until fade) {
            val g = i.toFloat() / fade
            buf[i] *= g
            buf[buf.size - 1 - i] *= g
        }
        return buf
    }

    /** Encode mono 16-bit PCM WAV (RIFF/WAVE) at [sampleRate]. */
    fun wavBytes(samples: FloatArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArray(44 + dataBytes)
        fun str(off: Int, s: String) {
            for (i in s.indices) out[off + i] = s[i].code.toByte()
        }
        fun int32(off: Int, v: Int) {
            out[off] = (v and 0xFF).toByte()
            out[off + 1] = ((v ushr 8) and 0xFF).toByte()
            out[off + 2] = ((v ushr 16) and 0xFF).toByte()
            out[off + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        fun int16(off: Int, v: Int) {
            out[off] = (v and 0xFF).toByte()
            out[off + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        str(0, "RIFF")
        int32(4, 36 + dataBytes)
        str(8, "WAVE")
        str(12, "fmt ")
        int32(16, 16) // fmt chunk size
        int16(20, 1) // PCM
        int16(22, 1) // mono
        int32(24, sampleRate)
        int32(28, sampleRate * 2) // byte rate
        int16(32, 2) // block align
        int16(34, 16) // bits per sample
        str(36, "data")
        int32(40, dataBytes)
        var off = 44
        for (s in samples) {
            val v = (s * 32767f).toInt().coerceIn(-32768, 32767)
            int16(off, v)
            off += 2
        }
        return out
    }
}
