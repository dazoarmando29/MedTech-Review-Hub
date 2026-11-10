// Generative study-music synthesizer — implements shared/MUSIC.md exactly.
// Pure TypeScript (no Web Audio dependency) so it is unit-testable and can be
// ported 1:1 to Kotlin (android/.../StudySynth.kt). All audio is original
// composition created by the app (CC0) — no third-party recordings.

import type { MusicRecipe, TrackSynthSpec } from '../types';

export const SAMPLE_RATE = 22050;

/** Deterministic RNG (mulberry32) — identical output on every device. */
export function mulberry32(seed: number): () => number {
  let a = seed | 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function midiFreq(midi: number): number {
  return 440 * Math.pow(2, (midi - 69) / 12);
}

function onePoleLP(x: Float32Array, alpha: number): Float32Array {
  const y = new Float32Array(x.length);
  let prev = 0;
  for (let i = 0; i < x.length; i++) {
    prev = prev + alpha * (x[i] - prev);
    y[i] = prev;
  }
  return y;
}

function onePoleHP(x: Float32Array, alpha: number): Float32Array {
  const y = new Float32Array(x.length);
  let prevY = 0;
  let prevX = 0;
  for (let i = 0; i < x.length; i++) {
    prevY = alpha * (prevY + x[i] - prevX);
    prevX = x[i];
    y[i] = prevY;
  }
  return y;
}

function delayReverb(x: Float32Array, mix = 0.25, fb = 0.35, delaySec = 0.28): Float32Array {
  const d = Math.floor(delaySec * SAMPLE_RATE);
  const buf = new Float32Array(x.length);
  const out = new Float32Array(x.length);
  for (let i = 0; i < x.length; i++) {
    const back = i >= d ? buf[i - d] : 0;
    buf[i] = x[i] + fb * back;
    out[i] = x[i] + mix * buf[i];
  }
  return out;
}

function normalizeWithFade(x: Float32Array, peak = 0.85, fadeSec = 0.15): Float32Array {
  let max = 0;
  for (let i = 0; i < x.length; i++) max = Math.max(max, Math.abs(x[i]));
  const gain = max > 0 ? peak / max : 1;
  const fade = Math.floor(fadeSec * SAMPLE_RATE);
  const out = new Float32Array(x.length);
  for (let i = 0; i < x.length; i++) {
    let env = 1;
    if (i < fade) env = i / fade;
    if (i > x.length - fade) env = Math.min(env, (x.length - i) / fade);
    out[i] = x[i] * gain * env;
  }
  return out;
}

function whiteNoise(n: number, rng: () => number, amp = 1): Float32Array {
  const y = new Float32Array(n);
  for (let i = 0; i < n; i++) y[i] = (rng() * 2 - 1) * amp;
  return y;
}

function pinkNoise(n: number, rng: () => number): Float32Array {
  const w = whiteNoise(n, rng, 1);
  const lp = onePoleLP(w, 0.02);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) out[i] = lp[i] + w[i] * 0.15;
  return out;
}

function brownNoise(n: number, rng: () => number): Float32Array {
  const out = new Float32Array(n);
  let y = 0;
  for (let i = 0; i < n; i++) {
    y = y + (rng() - 0.5) * 0.02;
    out[i] = y;
  }
  return out;
}

function addAt(dst: Float32Array, src: Float32Array, atSample: number, gain = 1): void {
  const start = Math.max(0, Math.floor(atSample));
  for (let i = 0; i < src.length && start + i < dst.length; i++) {
    dst[start + i] += src[i] * gain;
  }
}

function pluck(freq: number, seconds: number, tau = 1.1): Float32Array {
  const n = Math.floor(seconds * SAMPLE_RATE);
  const y = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    let s = 0;
    for (let k = 1; k <= 4; k++) {
      s += Math.sin(2 * Math.PI * freq * k * t) / Math.pow(k, 1.5);
    }
    y[i] = s * Math.exp(-t / tau);
  }
  return y;
}

function softNote(freq: number, seconds: number, tau: number, harmonics: number[], amps: number[]): Float32Array {
  const n = Math.floor(seconds * SAMPLE_RATE);
  const y = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    let s = 0;
    for (let h = 0; h < harmonics.length; h++) {
      s += amps[h] * Math.sin(2 * Math.PI * freq * harmonics[h] * t);
    }
    y[i] = s * Math.exp(-t / tau);
  }
  return y;
}

// ---------- recipes ----------

function pianoArpeggio(n: number, rng: () => number, spec: TrackSynthSpec): Float32Array {
  const out = new Float32Array(n);
  const tempo = spec.tempoBpm ?? 66;
  const beat = 60 / tempo;
  const progression = spec.progression ?? [[60, 64, 67], [55, 59, 62], [57, 60, 64], [53, 57, 60]];
  const barSec = 4 * beat;
  const totalBars = Math.ceil(n / SAMPLE_RATE / barSec);
  for (let bar = 0; bar < totalBars; bar++) {
    const chord = progression[bar % progression.length];
    const barStart = bar * barSec * SAMPLE_RATE;
    // bass root on bar start
    addAt(out, pluck(midiFreq(chord[0] - 12), Math.min(3, barSec * 2), 2.2), barStart, 0.42);
    // eighth-note arpeggio up/down across the chord
    const pattern = [0, 1, 2, 1, 2, 1, 0, 1];
    for (let e = 0; e < 8; e++) {
      const note = chord[pattern[e] % chord.length] + (e >= 4 ? 12 : 0);
      const at = barStart + (e * beat * 0.5) * SAMPLE_RATE;
      addAt(out, pluck(midiFreq(note), 2.2, 1.1), at, 0.5 + rng() * 0.08);
    }
  }
  void rng;
  return delayReverb(out);
}

function violinPad(n: number, rng: () => number, spec: TrackSynthSpec): Float32Array {
  const out = new Float32Array(n);
  const chords = spec.chords ?? [[57, 60, 64], [53, 57, 60], [60, 64, 67], [55, 59, 62]];
  const perChord = spec.secondsPerChord ?? 8;
  const total = Math.ceil(n / SAMPLE_RATE / perChord);
  for (let c = 0; c < total; c++) {
    const chord = chords[c % chords.length];
    const start = c * perChord * SAMPLE_RATE;
    const segN = Math.floor(perChord * SAMPLE_RATE);
    const seg = new Float32Array(segN);
    for (const m of chord) {
      const f = midiFreq(m);
      for (let i = 0; i < segN; i++) {
        const t = i / SAMPLE_RATE;
        const vib = 0.003 * Math.sin(2 * Math.PI * 5 * t);
        let s = 0;
        for (let k = 1; k <= 6; k++) s += Math.sin(2 * Math.PI * f * k * t + vib) / k;
        // attack 2.5s, release 2s
        const attack = Math.min(1, t / 2.5);
        const release = Math.min(1, (perChord - t) / 2);
        seg[i] += s * attack * Math.max(0, release) * 0.32;
      }
    }
    addAt(out, onePoleLP(seg, 0.06), start, 1);
    void rng;
  }
  return delayReverb(out);
}

function lofiCafe(n: number, rng: () => number, spec: TrackSynthSpec): Float32Array {
  const out = new Float32Array(n);
  const progression = spec.progression ?? [[60, 64, 67, 71], [57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 65]];
  const tempo = spec.tempoBpm ?? 72;
  const beat = 60 / tempo;
  const barSec = 4 * beat;
  const totalBars = Math.ceil(n / SAMPLE_RATE / barSec);

  // pads: 3 detuned saws per chord note, chord every 2 bars
  for (let c = 0; c < totalBars / 2; c++) {
    const chord = progression[c % progression.length];
    const start = c * 2 * barSec * SAMPLE_RATE;
    const segN = Math.floor(2 * barSec * SAMPLE_RATE);
    const seg = new Float32Array(segN);
    for (const m of chord) {
      for (const detune of [-4, 0, 4]) {
        const f = midiFreq(m) + detune;
        for (let i = 0; i < segN; i++) {
          const t = i / SAMPLE_RATE;
          let saw = 0;
          for (let k = 1; k <= 10; k++) saw += Math.sin(2 * Math.PI * f * k * t) / k;
          const attack = Math.min(1, t / 0.5);
          const release = Math.min(1, (segN / SAMPLE_RATE - t) / 0.5);
          seg[i] += saw * attack * Math.max(0, release) * 0.06;
        }
      }
    }
    addAt(out, onePoleLP(seg, 0.05), start, 1);
  }

  // kick on beats 1 & 3, hats on off-beats
  for (let bar = 0; bar < totalBars; bar++) {
    const barStart = bar * barSec * SAMPLE_RATE;
    for (const beatIdx of [0, 2]) {
      const kickN = Math.floor(0.12 * SAMPLE_RATE);
      const kick = new Float32Array(kickN);
      for (let i = 0; i < kickN; i++) {
        const t = i / SAMPLE_RATE;
        kick[i] = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t / 0.12) * 0.5;
      }
      addAt(out, kick, barStart + beatIdx * beat * SAMPLE_RATE, 1);
    }
    for (let h = 0; h < 4; h++) {
      const at = barStart + (h * beat + beat * 0.5) * SAMPLE_RATE;
      const hatN = Math.floor(0.02 * SAMPLE_RATE);
      const hat = onePoleHP(whiteNoise(hatN, rng, 1), 0.9);
      addAt(out, hat, at, 0.05);
    }
  }

  // vinyl crackle
  const crackle = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    if (rng() < 0.004) crackle[i] = (rng() * 2 - 1) * 0.02;
  }
  for (let i = 0; i < n; i++) out[i] += crackle[i];

  return delayReverb(out, 0.15);
}

function classicalDuet(n: number, rng: () => number, spec: TrackSynthSpec): Float32Array {
  const out = new Float32Array(n);
  const progression = spec.progression ?? [[60, 64, 67], [67, 71, 74], [57, 60, 64], [55, 59, 62], [53, 57, 60], [60, 64, 67], [53, 57, 60], [67, 71, 74]];
  const tempo = spec.tempoBpm ?? 76;
  const beat = 60 / tempo;
  const barSec = 4 * beat;
  const totalBars = Math.ceil(n / SAMPLE_RATE / barSec);

  for (let bar = 0; bar < totalBars; bar++) {
    const chord = progression[bar % progression.length];
    const barStart = bar * barSec * SAMPLE_RATE;
    // voice B: sustained chord tones (half notes)
    chord.forEach((m, idx) => {
      const f = midiFreq(m - 12);
      const note = softNote(f, barSec * 1.1, 1.8, [1, 2, 3], [1, 0.4, 0.15]);
      addAt(out, note, barStart, 0.16 + rng() * 0.02 + idx * 0.01);
    });
    // voice A: eighth-note arpeggio, triangle-ish
    const pattern = [0, 1, 2, 1, 0, 1, 2, 1];
    for (let e = 0; e < 8; e++) {
      const f = midiFreq(chord[pattern[e] % chord.length]);
      const at = barStart + (e * beat * 0.5) * SAMPLE_RATE;
      const nLen = Math.floor(1.2 * SAMPLE_RATE);
      const seg = new Float32Array(nLen);
      for (let i = 0; i < nLen; i++) {
        const t = i / SAMPLE_RATE;
        seg[i] = (Math.sin(2 * Math.PI * f * t) + 0.08 * Math.sin(2 * Math.PI * f * 9 * t)) * Math.exp(-t / 0.9);
      }
      addAt(out, seg, at, 0.3);
    }
  }
  return delayReverb(out, 0.2);
}

function sleepyPads(n: number, rng: () => number, spec: TrackSynthSpec): Float32Array {
  const out = new Float32Array(n);
  const chords = spec.chords ?? [[57, 60, 64], [53, 57, 60], [55, 59, 62]];
  const perChord = spec.secondsPerChord ?? 12;
  const total = Math.ceil(n / SAMPLE_RATE / perChord);
  for (let c = 0; c < total; c++) {
    const chord = chords[c % chords.length];
    const start = c * perChord * SAMPLE_RATE;
    const segN = Math.floor(perChord * SAMPLE_RATE);
    const seg = new Float32Array(segN);
    for (const m of chord) {
      const f = midiFreq(m);
      for (let i = 0; i < segN; i++) {
        const t = i / SAMPLE_RATE;
        const s =
          Math.sin(2 * Math.PI * f * t) +
          0.28 * Math.sin(2 * Math.PI * f * 2 * t) +
          0.1 * Math.sin(2 * Math.PI * f * 3 * t);
        const attack = Math.min(1, t / 4);
        const release = Math.min(1, (perChord - t) / 3.5);
        seg[i] += s * attack * Math.max(0, release) * 0.3;
      }
    }
    addAt(out, seg, start, 1);
  }
  // sparse pluck accents
  const totalSec = n / SAMPLE_RATE;
  for (let s = 0; s < totalSec; s++) {
    if (rng() < 0.02) {
      const chord = chords[Math.floor(rng() * chords.length)];
      const note = chord[Math.floor(rng() * chord.length)] + 12;
      addAt(out, pluck(midiFreq(note), 3, 2.8), s * SAMPLE_RATE, 0.12);
    }
  }
  return delayReverb(out, 0.18);
}

function deepDrone(n: number, _rng: () => number): Float32Array {
  const out = new Float32Array(n);
  const partials: [number, number][] = [[110, 0.35], [110.35, 0.3], [165, 0.2], [220.5, 0.12]];
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    const swell = 0.75 + 0.25 * Math.sin(2 * Math.PI * 0.02 * t);
    let s = 0;
    for (const [f, amp] of partials) s += amp * Math.sin(2 * Math.PI * f * t);
    out[i] = s * swell * 0.6;
  }
  const floor = pinkNoise(n, mulberry32(99));
  for (let i = 0; i < n; i++) out[i] += floor[i] * 0.015;
  return out;
}

function rain(n: number, rng: () => number): Float32Array {
  const out = onePoleHP(whiteNoise(n, rng, 0.22), 0.86);
  let t = 0;
  const totalSec = n / SAMPLE_RATE;
  while (t < totalSec) {
    t += 0.05 + rng() * 0.25;
    const freq = 2000 + rng() * 2200;
    const amp = 0.05 + rng() * 0.17;
    const dropN = Math.floor(0.05 * SAMPLE_RATE);
    const drop = new Float32Array(dropN);
    for (let i = 0; i < dropN; i++) {
      const dt = i / SAMPLE_RATE;
      drop[i] = Math.sin(2 * Math.PI * freq * dt) * Math.exp(-dt / 0.045);
    }
    addAt(out, drop, t * SAMPLE_RATE, amp);
  }
  return out;
}

function forest(n: number, rng: () => number): Float32Array {
  const out = pinkNoise(n, rng);
  for (let i = 0; i < n; i++) out[i] *= 0.06;
  const totalSec = n / SAMPLE_RATE;
  let t = 0;
  while (t < totalSec) {
    t += 2 + rng() * 5;
    const repeats = 2 + Math.floor(rng() * 3);
    for (let r = 0; r < repeats; r++) {
      const at = (t + r * 0.18) * SAMPLE_RATE;
      const chirpN = Math.floor(0.12 * SAMPLE_RATE);
      const chirp = new Float32Array(chirpN);
      for (let i = 0; i < chirpN; i++) {
        const dt = i / SAMPLE_RATE;
        const f = 2800 + (3400 - 2800) * (dt / 0.12);
        chirp[i] = Math.sin(2 * Math.PI * f * dt) * Math.exp(-dt / 0.05);
      }
      addAt(out, chirp, at, 0.12);
    }
  }
  return out;
}

function ocean(n: number, rng: () => number): Float32Array {
  const brown = brownNoise(n, rng);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    out[i] = brown[i] * (0.45 + 0.4 * Math.sin((2 * Math.PI * t) / 9));
  }
  const lp = onePoleLP(out, 0.03);
  const totalSec = n / SAMPLE_RATE;
  for (let w = 0; w < totalSec; w += 9) {
    const crashN = Math.floor(1.2 * SAMPLE_RATE);
    const crash = whiteNoise(crashN, rng, 0.18);
    for (let i = 0; i < crashN; i++) {
      const dt = i / SAMPLE_RATE;
      const env = dt < 0.6 ? dt / 0.6 : (1.2 - dt) / 0.6;
      crash[i] *= Math.max(0, env);
    }
    addAt(lp, crash, w * SAMPLE_RATE, 1);
  }
  return lp;
}

function wind(n: number, rng: () => number): Float32Array {
  const w = whiteNoise(n, rng, 1);
  const out = new Float32Array(n);
  let prev = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    const alpha = 0.02 + 0.015 * Math.sin(2 * Math.PI * 0.05 * t);
    prev = prev + alpha * (w[i] - prev);
    out[i] = prev * (0.6 + 0.4 * Math.sin(2 * Math.PI * 0.07 * t + 1));
  }
  return out;
}

function night(n: number, rng: () => number): Float32Array {
  const out = pinkNoise(n, rng);
  for (let i = 0; i < n; i++) out[i] *= 0.04;
  const totalSec = n / SAMPLE_RATE;
  let t = 0;
  while (t < totalSec) {
    t += 0.8 + rng() * 1.7;
    for (let b = 0; b < 5; b++) {
      const burstN = Math.floor(0.02 * SAMPLE_RATE);
      const burst = new Float32Array(burstN);
      for (let i = 0; i < burstN; i++) {
        const dt = i / SAMPLE_RATE;
        burst[i] = Math.sin(2 * Math.PI * 4300 * dt) * Math.exp(-dt / 0.012);
      }
      addAt(out, burst, (t + b * 0.045) * SAMPLE_RATE, 0.1);
    }
  }
  return out;
}

const RECIPES: Record<MusicRecipe, (n: number, rng: () => number, spec: TrackSynthSpec) => Float32Array> = {
  'piano-arpeggio': pianoArpeggio,
  'violin-pad': violinPad,
  'lofi-cafe': lofiCafe,
  'classical-duet': classicalDuet,
  'sleepy-pads': sleepyPads,
  'deep-drone': deepDrone,
  rain,
  forest,
  ocean,
  wind,
  'white-noise': (n, rng) => whiteNoise(n, rng, 0.5),
  'brown-noise': (n, rng) => brownNoise(n, rng),
  'pink-noise': pinkNoise,
  night,
};

/** Generates normalized mono PCM for a track. Deterministic per seed. */
export function generateTrackPcm(spec: TrackSynthSpec, durationSec: number): Float32Array {
  const n = Math.floor(durationSec * SAMPLE_RATE);
  const rng = mulberry32(spec.seed || 1);
  const recipe = RECIPES[spec.recipe] ?? RECIPES['pink-noise'];
  const raw = recipe(n, rng, spec);
  return normalizeWithFade(raw);
}

/** Gentle two-soft-pluck chime for Pomodoro phase changes (shared/MUSIC.md). */
export function generateChime(): Float32Array {
  const n = Math.floor(1.2 * SAMPLE_RATE);
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const t = i / SAMPLE_RATE;
    let s = 0;
    if (t < 0.9) s += Math.sin(2 * Math.PI * 880 * t) * Math.exp(-t / 0.6);
    const t2 = t - 0.25;
    if (t2 >= 0 && t2 < 0.9) s += Math.sin(2 * Math.PI * 1320 * t2) * Math.exp(-t2 / 0.6);
    out[i] = s * 0.25;
  }
  return normalizeWithFade(out, 0.5);
}
