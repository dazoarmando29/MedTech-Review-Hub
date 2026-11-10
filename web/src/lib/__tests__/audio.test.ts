import { describe, expect, it } from 'vitest';
import { generateChime, generateTrackPcm, mulberry32, SAMPLE_RATE } from '../audio/synth';
import { encodeWav, wavDurationSec } from '../audio/wav';
import type { TrackSynthSpec } from '../types';

const recipes: TrackSynthSpec[] = [
  { recipe: 'piano-arpeggio', seed: 1, tempoBpm: 66, progression: [[60, 64, 67], [55, 59, 62], [57, 60, 64], [53, 57, 60]] },
  { recipe: 'violin-pad', seed: 2, chords: [[57, 60, 64], [53, 57, 60], [60, 64, 67], [55, 59, 62]] },
  { recipe: 'lofi-cafe', seed: 3, progression: [[60, 64, 67, 71], [57, 60, 64, 67], [53, 57, 60, 64], [55, 59, 62, 65]] },
  { recipe: 'classical-duet', seed: 4, progression: [[60, 64, 67], [67, 71, 74], [57, 60, 64], [55, 59, 62], [53, 57, 60], [60, 64, 67], [53, 57, 60], [67, 71, 74]] },
  { recipe: 'sleepy-pads', seed: 5, chords: [[57, 60, 64], [53, 57, 60], [55, 59, 62]] },
  { recipe: 'deep-drone', seed: 6 },
  { recipe: 'rain', seed: 7 },
  { recipe: 'forest', seed: 8 },
  { recipe: 'ocean', seed: 9 },
  { recipe: 'wind', seed: 10 },
  { recipe: 'white-noise', seed: 11 },
  { recipe: 'brown-noise', seed: 12 },
  { recipe: 'pink-noise', seed: 13 },
  { recipe: 'night', seed: 14 },
];

describe('mulberry32', () => {
  it('is deterministic and in [0,1)', () => {
    const a = mulberry32(42);
    const b = mulberry32(42);
    for (let i = 0; i < 100; i++) {
      const x = a();
      expect(x).toBe(b());
      expect(x).toBeGreaterThanOrEqual(0);
      expect(x).toBeLessThan(1);
    }
  });
});

describe('generateTrackPcm', () => {
  it('is deterministic per seed (same seed -> identical samples)', () => {
    for (const spec of recipes) {
      const first = generateTrackPcm(spec, 2);
      const second = generateTrackPcm({ ...spec }, 2);
      expect(first.length).toBe(second.length);
      for (let i = 0; i < 256; i++) {
        expect(first[i]).toBe(second[i]);
      }
    }
  });

  it('produces non-silent audio with sane amplitude for every recipe', () => {
    for (const spec of recipes) {
      const pcm = generateTrackPcm(spec, 2);
      let energy = 0;
      let peak = 0;
      for (let i = 0; i < pcm.length; i++) {
        energy += pcm[i] * pcm[i];
        peak = Math.max(peak, Math.abs(pcm[i]));
      }
      expect(energy).toBeGreaterThan(0);
      expect(peak).toBeLessThanOrEqual(0.85 + 1e-6);
      expect(peak).toBeGreaterThan(0.05);
    }
  });

  it('fades edges in and out (no clicks)', () => {
    const pcm = generateTrackPcm({ recipe: 'white-noise', seed: 21 }, 3);
    expect(Math.abs(pcm[0])).toBeLessThan(0.01);
    expect(Math.abs(pcm[pcm.length - 1])).toBeLessThan(0.01);
  });
});

describe('encodeWav', () => {
  it('writes a valid RIFF/WAVE header for 22050 Hz mono 16-bit', () => {
    const pcm = generateTrackPcm({ recipe: 'pink-noise', seed: 31 }, 1);
    const wav = encodeWav(pcm, SAMPLE_RATE);
    const view = new DataView(wav);
    const tag = (o: number) => String.fromCharCode(view.getUint8(o), view.getUint8(o + 1), view.getUint8(o + 2), view.getUint8(o + 3));
    expect(tag(0)).toBe('RIFF');
    expect(tag(8)).toBe('WAVE');
    expect(tag(12)).toBe('fmt ');
    expect(view.getUint16(20, true)).toBe(1); // PCM
    expect(view.getUint16(22, true)).toBe(1); // mono
    expect(view.getUint32(24, true)).toBe(SAMPLE_RATE);
    expect(view.getUint16(34, true)).toBe(16); // bits
    expect(tag(36)).toBe('data');
    expect(wavDurationSec(wav)).toBeCloseTo(1, 1);
    expect(wav.byteLength).toBe(44 + pcm.length * 2);
  });
});

describe('chime', () => {
  it('is gentle and short', () => {
    const chime = generateChime();
    expect(chime.length).toBeLessThanOrEqual(1.2 * SAMPLE_RATE + 1);
    let peak = 0;
    for (let i = 0; i < chime.length; i++) peak = Math.max(peak, Math.abs(chime[i]));
    expect(peak).toBeGreaterThan(0);
    expect(peak).toBeLessThanOrEqual(0.5 + 1e-6);
  });
});
