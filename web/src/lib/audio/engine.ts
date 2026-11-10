// Study Music player engine (client-side singleton).
// Real playback of generated audio (shared/MUSIC.md) with a single audio
// element — never more than one track at a time. Media Session API is wired
// where the browser supports it; playback only starts from user gestures.

import { musicCatalog } from '../content';
import type { MusicPlaylist, MusicTrack } from '../types';
import { generateChime, generateTrackPcm, SAMPLE_RATE } from './synth';
import { encodeWav } from './wav';

export type RepeatMode = 'off' | 'track' | 'playlist';

export interface PlayerState {
  trackId: string | null;
  playing: boolean;
  positionSec: number;
  durationSec: number;
  volume: number;
  muted: boolean;
  repeat: RepeatMode;
  shuffle: boolean;
  queue: string[];
  index: number;
  generating: boolean;
}

const listeners = new Set<() => void>();
let state: PlayerState = {
  trackId: null, playing: false, positionSec: 0, durationSec: 0,
  volume: 0.6, muted: false, repeat: 'off', shuffle: false,
  queue: [], index: -1, generating: false,
};

let audio: HTMLAudioElement | null = null;
const blobCache = new Map<string, string>();
const CACHE_NAME = 'mtrh-audio-v1';

function emit() {
  listeners.forEach((l) => l());
}

function getAudio(): HTMLAudioElement {
  if (!audio) {
    audio = new Audio();
    audio.preload = 'auto';
    audio.addEventListener('timeupdate', () => {
      state = { ...state, positionSec: audio!.currentTime };
      emit();
    });
    audio.addEventListener('ended', () => handleEnded());
    audio.addEventListener('loadedmetadata', () => {
      if (Number.isFinite(audio!.duration)) {
        state = { ...state, durationSec: audio!.duration };
        emit();
      }
    });
  }
  return audio;
}

async function generateBlobUrl(track: MusicTrack): Promise<string> {
  const cached = blobCache.get(track.id);
  if (cached) return cached;

  // persistent cache ("downloaded") first
  try {
    const cache = await caches.open(CACHE_NAME);
    const hit = await cache.match(track.id);
    if (hit) {
      const blob = await hit.blob();
      const url = URL.createObjectURL(blob);
      blobCache.set(track.id, url);
      return url;
    }
  } catch {
    /* Cache Storage unavailable — in-memory only */
  }

  state = { ...state, generating: true };
  emit();
  // yield to keep the UI responsive while synthesizing
  await new Promise((r) => setTimeout(r, 0));
  const pcm = generateTrackPcm(track.synth, track.durationSec);
  const wav = encodeWav(pcm, SAMPLE_RATE);
  const blob = new Blob([wav], { type: 'audio/wav' });
  const url = URL.createObjectURL(blob);
  blobCache.set(track.id, url);
  state = { ...state, generating: false };
  emit();
  return url;
}

function fadeTo(target: number, ms = 200): void {
  const a = getAudio();
  const start = a.volume;
  const t0 = performance.now();
  const step = () => {
    const k = Math.min(1, (performance.now() - t0) / ms);
    a.volume = start + (target - start) * k;
    if (k < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function wireMediaSession(track: MusicTrack): void {
  if (!('mediaSession' in navigator)) return;
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist ?? 'MedTech Companion Studio',
      album: 'Study Music — original generative composition (CC0)',
    });
    navigator.mediaSession.setActionHandler('play', () => void player.play());
    navigator.mediaSession.setActionHandler('pause', () => player.pause());
    navigator.mediaSession.setActionHandler('previoustrack', () => player.previous());
    navigator.mediaSession.setActionHandler('nexttrack', () => player.next());
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (details.seekTime != null) player.seek(details.seekTime);
    });
  } catch {
    /* some browsers restrict handlers */
  }
}

async function loadAndPlay(trackId: string, queue: string[]): Promise<void> {
  const track = musicCatalog.tracks.find((t) => t.id === trackId);
  if (!track) return;
  const url = await generateBlobUrl(track);
  const a = getAudio();
  if (a.src !== url) {
    a.src = url;
  }
  a.loop = state.repeat === 'track';
  a.muted = state.muted;
  a.volume = 0;
  await a.play();
  fadeTo(state.muted ? 0 : state.volume);
  state = {
    ...state,
    trackId,
    queue,
    index: queue.indexOf(trackId),
    playing: true,
    durationSec: track.durationSec,
  };
  wireMediaSession(track);
  emit();
}

function handleEnded(): void {
  if (state.repeat === 'track') return;
  player.next(true);
}

export const player = {
  getState: (): PlayerState => state,
  subscribe(cb: () => void): () => void {
    listeners.add(cb);
    return () => listeners.delete(cb);
  },

  async playTrack(trackId: string, queue?: string[]): Promise<void> {
    const q = queue && queue.length ? queue : [trackId];
    await loadAndPlay(trackId, q);
  },

  async play(): Promise<void> {
    if (!state.trackId) return;
    const a = getAudio();
    await a.play();
    fadeTo(state.muted ? 0 : state.volume);
    state = { ...state, playing: true };
    emit();
  },

  pause(): void {
    if (!audio) return;
    fadeTo(0);
    setTimeout(() => audio?.pause(), 200);
    state = { ...state, playing: false };
    emit();
  },

  async toggle(): Promise<void> {
    if (state.playing) player.pause();
    else await player.play();
  },

  next(auto = false): void {
    if (!state.queue.length) return;
    let idx = state.index + 1;
    if (state.shuffle && state.queue.length > 1) {
      let candidate = idx;
      while (candidate === state.index) candidate = Math.floor(Math.random() * state.queue.length);
      idx = candidate;
    }
    if (idx >= state.queue.length) {
      if (state.repeat === 'playlist') idx = 0;
      else if (auto) {
        player.pause();
        return;
      } else idx = 0;
    }
    void loadAndPlay(state.queue[idx], state.queue);
  },

  previous(): void {
    if (!state.queue.length) return;
    if (state.positionSec > 3) {
      player.seek(0);
      return;
    }
    const idx = state.index > 0 ? state.index - 1 : state.queue.length - 1;
    void loadAndPlay(state.queue[idx], state.queue);
  },

  seek(seconds: number): void {
    const a = getAudio();
    a.currentTime = Math.max(0, Math.min(seconds, state.durationSec || a.duration || 0));
    state = { ...state, positionSec: a.currentTime };
    emit();
  },

  setVolume(v: number): void {
    const vol = Math.max(0, Math.min(1, v));
    state = { ...state, volume: vol, muted: vol === 0 };
    if (audio) fadeTo(vol);
    emit();
  },

  toggleMute(): void {
    const muted = !state.muted;
    state = { ...state, muted };
    if (audio) {
      audio.muted = muted;
      fadeTo(muted ? 0 : state.volume);
    }
    emit();
  },

  setRepeat(mode: RepeatMode): void {
    state = { ...state, repeat: mode };
    if (audio) audio.loop = mode === 'track';
    emit();
  },

  toggleShuffle(): void {
    state = { ...state, shuffle: !state.shuffle };
    emit();
  },

  /** Pre-generates a track's WAV and stores it in Cache Storage ("download"). */
  async download(trackId: string): Promise<number> {
    const track = musicCatalog.tracks.find((t) => t.id === trackId);
    if (!track) return 0;
    const pcm = generateTrackPcm(track.synth, track.durationSec);
    const wav = encodeWav(pcm, SAMPLE_RATE);
    const blob = new Blob([wav], { type: 'audio/wav' });
    try {
      const cache = await caches.open(CACHE_NAME);
      await cache.put(trackId, new Response(blob, { headers: { 'Content-Type': 'audio/wav' } }));
    } catch {
      /* in-memory only */
    }
    blobCache.set(trackId, URL.createObjectURL(blob));
    return blob.size;
  },

  async removeDownload(trackId: string): Promise<void> {
    try {
      const cache = await caches.open(CACHE_NAME);
      await cache.delete(trackId);
    } catch {
      /* ignore */
    }
    const url = blobCache.get(trackId);
    if (url) URL.revokeObjectURL(url);
    blobCache.delete(trackId);
  },

  async storageUsedBytes(): Promise<number> {
    try {
      const cache = await caches.open(CACHE_NAME);
      const keys = await cache.keys();
      let total = 0;
      for (const req of keys) {
        const res = await cache.match(req);
        if (res) total += (await res.blob()).size;
      }
      return total;
    } catch {
      return 0;
    }
  },

  /** Gentle synthesized chime for Pomodoro phase changes. */
  async chime(volume = 0.5): Promise<void> {
    const wav = encodeWav(generateChime(), SAMPLE_RATE);
    const el = new Audio(URL.createObjectURL(new Blob([wav], { type: 'audio/wav' })));
    el.volume = Math.max(0, Math.min(1, volume));
    try {
      await el.play();
    } catch {
      /* autoplay policy — ignore */
    }
  },

  stop(): void {
    if (audio) {
      audio.pause();
      audio.src = '';
    }
    state = { ...state, playing: false, trackId: null, positionSec: 0, index: -1 };
    emit();
  },
};

export function playlistTracks(playlist: MusicPlaylist | null | undefined): MusicTrack[] {
  if (!playlist) return [];
  return playlist.trackIds
    .map((id) => musicCatalog.tracks.find((t) => t.id === id))
    .filter((t): t is MusicTrack => Boolean(t));
}

export function formatTime(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
