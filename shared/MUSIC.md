# Study Music — Generative Audio Spec (shared by web + Android)

All study-music audio is **original generative composition synthesized on-device**
from these recipes — no third-party recordings are bundled or redistributed, so
licensing is simple: the audio is created by the app itself (released CC0 with the
project). Attribution string used in the player: `MedTech Companion Studio`.
License string: `Original generative composition — created for this app (CC0)`.

The architecture is ready for licensed track packs later (metadata schema already
carries per-track `license` / `artist` fields).

Implementations MUST stay in sync (like shared/FSRS.md):
- Web: `web/src/lib/audio/synth.ts` (pure functions → Float32Array → WAV blob)
- Android: `android/.../domain/StudySynth.kt` (same math → PCM → WAV cache file,
  played with Media3/ExoPlayer for real audio-focus/notification behavior)

## Format

Sample rate **22050 Hz**, mono, 16-bit PCM WAV. Track length per catalog entry
(90–120 s). Synthesis is deterministic per track: seeded RNG, identical output
on every device.

```
RNG: mulberry32(seed)
  a = seed | 0
  next(): a = (a + 0x6D2B79F5) | 0;
          t = imul(a ^ (a >>> 15), 1 | a);
          t = (t + imul(t ^ (t >>> 7), 61 | t)) ^ t;
          return ((t ^ (t >>> 14)) >>> 0) / 4294967296        // [0,1)

One-pole low-pass:   y[n] = y[n-1] + alpha * (x[n] - y[n-1])
One-pole high-pass:  y[n] = alpha * (y[n-1] + x[n] - x[n-1])
Delay reverb:        d = floor(0.28 * SR), fb = 0.35, mix = 0.25
                     buf[n] = x[n] + fb * buf[n-d];  out = x + mix * buf
Note frequency:      f(midi) = 440 * 2^((midi - 69) / 12)
Envelope (decay):    amp(t) = exp(-t / tau)
Normalize final buffer to peak 0.85; fade in/out 0.15 s at edges (no clicks).
```

## Recipes

`piano-arpeggio(seed, tempoBpm, rootMidi, progression[4 chords of 3 midi notes])`
Loop length 90 s. A bar = 4 beats (beat = 60/tempo). Each chord lasts 2 bars;
eighth-note plucks cycle chord tones up then down. Pluck:
`sum(k=1..4) sin(2π·f·k·t) / k^1.5 · exp(-t/1.1)`; bass sine `f(root)/2` with
tau 2.2 s on each bar start. Through delay reverb.

`violin-pad(seed, chords[4 × 3 midi], secondsPerChord = 8)` Loop 90 s.
Voice = `sum(k=1..6) sin(2π·f·k·t + 0.003·sin(2π·5·t)) / k`; attack 2.5 s,
release 2 s per chord; low-pass alpha 0.06; delay reverb.

`lofi-cafe(seed, progression[4 chords of 4 midi], tempoBpm = 72)` Loop 90 s.
Pads: 3 detuned saws (±4 Hz; saw = `sum(k=1..10) sin(2π·f·k·t)/k`), low-pass
alpha 0.05, chord every 2 bars. Kick: `sin(2π·55·t)·exp(-t/0.12)` on beats 1 & 3,
amp 0.5. Hats: high-passed noise burst 20 ms on off-beats, amp 0.05. Crackle:
rng ticks (p = 0.004/sample, amp 0.02). Light delay reverb.

`classical-duet(seed, progression[8 chords of 3 midi], tempoBpm = 76)` Loop 90 s.
Voice A: triangle-ish (`sin + 0.08·sin(9θ)`) eighth-note arpeggio, tau 0.9 s.
Voice B: sustained half notes (`sum(k=1..3) sin(kθ)·[1,0.4,0.15][k]`), tau 1.8 s.
Delay reverb mix 0.2.

`sleepy-pads(seed, chords[3 × 3 midi], secondsPerChord = 12)` Loop 120 s.
Sine pad `sum(k=1..3) sin(2π·f·k·t)·[1,0.28,0.1][k]`, attack 4 s, release 3.5 s.
Sparse pluck accents (rng, p = 0.02/s, tau 2.8 s).

`deep-drone(seed)` Loop 120 s. Sines 110 / 110.35 / 165 / 220.5 Hz at amps
0.35 / 0.30 / 0.20 / 0.12; swell `0.75 + 0.25·sin(2π·0.02·t)`; pink-noise floor
amp 0.015.

`rain(seed)` Loop 90 s. White noise high-pass alpha 0.86, amp 0.22 + droplets:
every U(0.05, 0.3) s a burst `sin(2π·U(2000,4200)·t)·exp(-t/0.045)` amp U(0.05, 0.22).

`forest(seed)` Loop 90 s. Pink noise amp 0.06; bird chirps every U(2, 7) s:
2–4 repeats of a 120 ms sine sweeping 2800→3400 Hz, amp 0.12; soft rustle
(band-passed noise, LFO'd amp).

`ocean(seed)` Loop 90 s. Brown noise × `(0.45 + 0.4·sin(2π·t/9))`, low-pass alpha
0.03; a wave accent every 9 s (noise burst, 1.2 s attack/decay, amp 0.18).

`wind(seed)` Loop 90 s. White noise through a low-pass with alpha
`0.02 + 0.015·sin(2π·0.05·t)`; amplitude `0.6 + 0.4·sin(2π·0.07·t + 1)`.

`white-noise(seed)` Loop 90 s: `2·rng - 1`, amp 0.5.
`brown-noise(seed)` Loop 90 s: `y[n] = y[n-1] + (rng - 0.5)·0.02`, normalized.
`pink-noise(seed)` Loop 90 s: white through low-pass alpha 0.02 + white·0.15.
`night(seed)` Loop 90 s: pink amp 0.04 + crickets every U(0.8, 2.5) s: 5 bursts of
`sin(2π·4300·t)·exp(-t/0.012)` spaced 45 ms, amp 0.1.

## Player behavior (both platforms)

- Real playback controls: play/pause, next/previous, seek, volume, mute,
  repeat track / repeat playlist, shuffle, progress + duration, title, artist.
- Fade volume 200 ms on play/pause/track-change (never a sudden spike).
- Exactly one track plays at a time (single audio engine instance).
- Web: Media Session API metadata + action handlers where supported; playback
  only starts from a user gesture (browser autoplay policy is respected).
- Android: generated WAV cached per track; Media3/MediaSessionService for
  notification + lock-screen controls; proper audio focus, headphone-unplug and
  call handling; never silent-install or hidden playback.
- Offline: audio is generated on-device, so every track works offline. Track
  cache generation is presented as "Download for offline" with storage
  estimates and removable downloads (Cache Storage on web / cache dir on Android).
- Pomodoro: on focus start play the chosen playlist; on break optionally switch
  to a calmer category or pause; gentle (never loud) synthesized chime at phase
  end — chime = two sine plucks (880 Hz then 1320 Hz, tau 0.6 s, amp 0.25).
