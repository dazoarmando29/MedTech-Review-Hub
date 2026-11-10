'use client';

// Docked mini-player — music keeps playing across every page while studying.
import Link from 'next/link';
import React, { useSyncExternalStore } from 'react';
import { player, formatTime } from '@/lib/audio/engine';
import { musicCatalog } from '@/lib/content';
import { MusicArt } from './MusicArt';

export function usePlayer() {
  return useSyncExternalStore(player.subscribe, player.getState, player.getState);
}

export function MiniPlayer() {
  const st = usePlayer();
  const track = musicCatalog.tracks.find((t) => t.id === st.trackId);
  if (!track) return null;

  const progress = st.durationSec > 0 ? Math.min(1, st.positionSec / st.durationSec) : 0;

  return (
    <div
      className="fixed bottom-0 left-0 right-0 z-40 border-t"
      style={{ background: 'var(--surface)', borderColor: 'var(--line)', boxShadow: '0 -6px 24px rgb(219 39 119 / 0.10)' }}
      role="region"
      aria-label="Mini music player"
    >
      <div className="h-1" style={{ background: 'var(--chip)' }}>
        <div className="h-full" style={{ width: `${progress * 100}%`, background: 'var(--primary)' }} />
      </div>
      <div className="max-w-6xl mx-auto px-4 py-2 flex items-center gap-3">
        <MusicArt id={(musicCatalog.categories.find((c) => c.id === track.categoryId)?.art) ?? 'focus-orb'} size={44} />
        <div className="min-w-0 flex-1">
          <div className="text-sm font-semibold truncate">
            {st.generating ? 'Composing your track…' : track.title}
          </div>
          <div className="text-xs truncate" style={{ color: 'var(--muted)' }}>
            {track.artist ?? 'MedTech Companion Studio'} · {formatTime(st.positionSec)} / {formatTime(st.durationSec)}
          </div>
        </div>

        <button className="btn-ghost !p-2 !min-h-[38px] !w-[38px]" aria-label="Previous track" onClick={() => player.previous()}>
          ⏮
        </button>
        <button
          className="btn-primary !p-2 !min-h-[42px] !w-[42px] text-base"
          aria-label={st.playing ? 'Pause' : 'Play'}
          onClick={() => void player.toggle()}
        >
          {st.playing ? '⏸' : '▶'}
        </button>
        <button className="btn-ghost !p-2 !min-h-[38px] !w-[38px]" aria-label="Next track" onClick={() => player.next()}>
          ⏭
        </button>

        <input
          type="range"
          min={0}
          max={100}
          value={Math.round(progress * 100)}
          className="hidden md:block w-28"
          aria-label="Seek"
          onChange={(e) => player.seek((Number(e.target.value) / 100) * st.durationSec)}
        />

        <button className="btn-ghost !p-2 !min-h-[38px] !w-[38px]" aria-label={st.muted ? 'Unmute' : 'Mute'} onClick={() => player.toggleMute()}>
          {st.muted ? '🔇' : '🔊'}
        </button>
        <Link href="/music/" className="btn-secondary !py-1.5 !px-3 !min-h-[38px] text-xs">
          Expand
        </Link>
      </div>
    </div>
  );
}
