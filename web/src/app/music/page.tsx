'use client';

// Study Music — "Find your focus, one melody at a time."
// All audio is original generative composition (shared/MUSIC.md, CC0),
// synthesized on-device: real playback, real seeking, works offline.
import React, { useEffect, useMemo, useState } from 'react';
import { Badge, Card, ConfirmButton, SectionTitle, Tabs, cx } from '@/components/ui';
import { MusicArt } from '@/components/MusicArt';
import { usePlayer } from '@/components/MiniPlayer';
import { formatTime, player, playlistTracks } from '@/lib/audio/engine';
import { musicCatalog } from '@/lib/content';
import { savePlaylist, removePlaylist, toggleDownload, toggleFavoriteTrack, updateSettings, useStore } from '@/lib/store';
import type { MusicPlaylist, MusicTrack } from '@/lib/types';

type Tab = 'browse' | 'playlists' | 'favorites' | 'offline' | 'settings';

export default function MusicPage() {
  const st = usePlayer();
  const [tab, setTab] = useState<Tab>('browse');
  const [categoryId, setCategoryId] = useState(musicCatalog.categories[0]?.id ?? '');
  const favorites = useStore((s) => s.music.favorites);
  const downloads = useStore((s) => s.music.downloads);
  const userPlaylists = useStore((s) => s.music.playlists);
  const prefs = useStore((s) => s.settings.music);
  const [storageBytes, setStorageBytes] = useState(0);
  const [newPlaylistName, setNewPlaylistName] = useState('');

  useEffect(() => {
    void player.storageUsedBytes().then(setStorageBytes);
  }, [downloads.length]);

  const category = musicCatalog.categories.find((c) => c.id === categoryId);
  const tracks = musicCatalog.tracks.filter((t) => t.categoryId === categoryId);
  const allPlaylists: MusicPlaylist[] = [...musicCatalog.suggestedPlaylists, ...userPlaylists];

  const playAll = (list: MusicTrack[]) => {
    if (list.length) void player.playTrack(list[0].id, list.map((t) => t.id));
  };

  return (
    <div className="space-y-6">
      <div className="hero-gradient p-6 sm:p-8">
        <h1 className="font-display text-2xl font-bold">Study Music 🎵</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--muted-strong)' }}>
          Find your focus, one melody at a time. Every track is an original generative
          composition created for this app (CC0) — synthesized right here on your device,
          so it always works offline.
        </p>
      </div>

      <Tabs
        tabs={[
          { id: 'browse', label: 'Browse' },
          { id: 'playlists', label: 'Playlists' },
          { id: 'favorites', label: `Favorites (${favorites.length})` },
          { id: 'offline', label: 'Offline' },
          { id: 'settings', label: 'Player settings' },
        ]}
        active={tab}
        onChange={(id) => setTab(id as Tab)}
      />

      {tab === 'browse' && (
        <>
          <div className="flex gap-2 flex-wrap">
            {musicCatalog.categories.map((c) => (
              <button
                key={c.id}
                className={cx('btn text-xs', categoryId === c.id ? 'btn-primary' : 'btn-ghost')}
                onClick={() => setCategoryId(c.id)}
              >
                {c.name}
              </button>
            ))}
          </div>

          <div className="grid lg:grid-cols-3 gap-4">
            <Card className="flex flex-col items-center text-center">
              <MusicArt id={category?.art ?? 'focus-orb'} size={160} />
              <h2 className="font-heading font-bold mt-3">{category?.name}</h2>
              <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>{category?.description}</p>
              <button className="btn-primary mt-4 w-full" onClick={() => playAll(tracks)}>
                ▶ Play all
              </button>
            </Card>

            <div className="lg:col-span-2 space-y-2">
              {tracks.map((t) => (
                <TrackRow
                  key={t.id}
                  track={t}
                  art={category?.art ?? 'focus-orb'}
                  active={st.trackId === t.id}
                  playing={st.playing && st.trackId === t.id}
                  favorite={favorites.includes(t.id)}
                  downloaded={downloads.includes(t.id)}
                  queueIds={tracks.map((x) => x.id)}
                />
              ))}
            </div>
          </div>

          {/* full player */}
          {st.trackId && (
            <Card>
              <SectionTitle icon="🎧">Now playing</SectionTitle>
              <FullPlayer />
            </Card>
          )}
        </>
      )}

      {tab === 'playlists' && (
        <div className="space-y-4">
          <Card>
            <SectionTitle icon="➕">Create a playlist</SectionTitle>
            <div className="flex gap-2">
              <input
                className="input"
                placeholder="e.g. Midnight ASCPi Review"
                value={newPlaylistName}
                onChange={(e) => setNewPlaylistName(e.target.value)}
              />
              <button
                className="btn-primary"
                disabled={!newPlaylistName.trim()}
                onClick={() => {
                  savePlaylist({ id: `pl-${Date.now()}`, name: newPlaylistName.trim(), trackIds: [] });
                  setNewPlaylistName('');
                }}
              >
                Create
              </button>
            </div>
          </Card>

          {allPlaylists.map((p) => {
            const list = playlistTracks(p);
            const isUser = userPlaylists.some((u) => u.id === p.id);
            return (
              <Card key={p.id}>
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex gap-1">
                    {list.slice(0, 3).map((t) => (
                      <MusicArt key={t.id} id={musicCatalog.categories.find((c) => c.id === t.categoryId)?.art ?? 'focus-orb'} size={44} />
                    ))}
                  </div>
                  <div className="flex-1 min-w-[180px]">
                    <h3 className="font-heading font-bold">{p.name}</h3>
                    <p className="text-xs" style={{ color: 'var(--muted)' }}>{list.length} tracks</p>
                  </div>
                  <button className="btn-primary !py-1.5 text-xs" onClick={() => playAll(list)}>▶ Play</button>
                  <button
                    className="btn-secondary !py-1.5 text-xs"
                    onClick={() => updateSettings({ music: { ...prefs, defaultPlaylistId: p.id } })}
                  >
                    {prefs.defaultPlaylistId === p.id ? '✓ Default' : 'Set default'}
                  </button>
                  {isUser && (
                    <ConfirmButton label="Delete" confirmLabel="Confirm?" className="btn-ghost text-xs"
                      onConfirm={() => removePlaylist(p.id)} />
                  )}
                </div>
                <ul className="mt-3 space-y-1">
                  {list.map((t) => (
                    <li key={t.id} className="flex items-center gap-2 text-sm">
                      <button className="btn-ghost !p-1 !min-h-[30px] !w-[30px]" aria-label={`Play ${t.title}`}
                        onClick={() => void player.playTrack(t.id, list.map((x) => x.id))}>
                        ▶
                      </button>
                      <span className={cx(st.trackId === t.id && 'font-semibold')} style={st.trackId === t.id ? { color: 'var(--primary)' } : undefined}>
                        {t.title}
                      </span>
                      <span className="text-xs ml-auto" style={{ color: 'var(--muted)' }}>{formatTime(t.durationSec)}</span>
                      {isUser && (
                        <button
                          className="chip"
                          onClick={() =>
                            savePlaylist({ ...p, trackIds: p.trackIds.filter((id) => id !== t.id) })
                          }
                        >
                          remove
                        </button>
                      )}
                    </li>
                  ))}
                </ul>
                {isUser && (
                  <div className="flex flex-wrap gap-1 mt-2">
                    <span className="text-xs self-center" style={{ color: 'var(--muted)' }}>add:</span>
                    {musicCatalog.tracks.filter((t) => !p.trackIds.includes(t.id)).slice(0, 8).map((t) => (
                      <button key={t.id} className="chip"
                        onClick={() => savePlaylist({ ...p, trackIds: [...p.trackIds, t.id] })}>
                        + {t.title}
                      </button>
                    ))}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}

      {tab === 'favorites' && (
        <div className="space-y-2">
          {favorites.length === 0 ? (
            <Card className="text-center py-10">
              <p className="text-sm" style={{ color: 'var(--muted)' }}>
                No favorites yet — tap the ♡ on any track to keep it here.
              </p>
            </Card>
          ) : (
            favorites.map((id) => {
              const t = musicCatalog.tracks.find((x) => x.id === id);
              if (!t) return null;
              return (
                <TrackRow
                  key={id}
                  track={t}
                  art={musicCatalog.categories.find((c) => c.id === t.categoryId)?.art ?? 'focus-orb'}
                  active={st.trackId === id}
                  playing={st.playing && st.trackId === id}
                  favorite
                  downloaded={downloads.includes(id)}
                  queueIds={favorites}
                />
              );
            })
          )}
        </div>
      )}

      {tab === 'offline' && (
        <Card>
          <SectionTitle icon="📥">Offline listening</SectionTitle>
          <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
            Every track is synthesized on your device, so music always works offline.
            “Download” pre-composes a track and keeps it in local cache for instant starts;
            you can remove downloads anytime. Estimated cache usage:{' '}
            <strong>{(storageBytes / 1024 / 1024).toFixed(1)} MB</strong>
            {downloads.length > 0 && <> ({downloads.length} tracks kept)</>}.
          </p>
          <div className="space-y-2 mt-4">
            {musicCatalog.tracks.map((t) => {
              const on = downloads.includes(t.id);
              return (
                <div key={t.id} className="flex items-center gap-2 text-sm rounded-[12px] p-2" style={{ background: 'var(--surface-2)' }}>
                  <span className="flex-1">{t.title}</span>
                  <Badge>{(t.durationSec / 60).toFixed(1)} min ≈ {(t.durationSec * 22050 * 2 / 1024 / 1024).toFixed(1)} MB</Badge>
                  <button
                    className={cx('text-xs', on ? 'btn-secondary' : 'btn-ghost')}
                    onClick={async () => {
                      if (on) {
                        await player.removeDownload(t.id);
                        toggleDownload(t.id);
                      } else {
                        await player.download(t.id);
                        toggleDownload(t.id);
                      }
                      setStorageBytes(await player.storageUsedBytes());
                    }}
                  >
                    {on ? 'Remove download' : 'Download for offline'}
                  </button>
                </div>
              );
            })}
          </div>
        </Card>
      )}

      {tab === 'settings' && (
        <Card>
          <SectionTitle icon="⚙️">Player preferences</SectionTitle>
          <div className="grid sm:grid-cols-2 gap-4 text-sm">
            <label>
              Default playlist
              <select className="input mt-1" value={prefs.defaultPlaylistId ?? ''}
                onChange={(e) => updateSettings({ music: { ...prefs, defaultPlaylistId: e.target.value || null } })}>
                <option value="">None</option>
                {allPlaylists.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </label>
            <label>
              Morning study playlist
              <select className="input mt-1" value={prefs.morningPlaylistId ?? ''}
                onChange={(e) => updateSettings({ music: { ...prefs, morningPlaylistId: e.target.value || null } })}>
                <option value="">None</option>
                {allPlaylists.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </label>
            <label>
              Late-night review playlist
              <select className="input mt-1" value={prefs.nightPlaylistId ?? ''}
                onChange={(e) => updateSettings({ music: { ...prefs, nightPlaylistId: e.target.value || null } })}>
                <option value="">None</option>
                {allPlaylists.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </label>
            <label>
              Preferred volume: {Math.round(prefs.volume * 100)}%
              <input type="range" min={0} max={100} value={Math.round(prefs.volume * 100)} className="w-full mt-2"
                onChange={(e) => {
                  const v = Number(e.target.value) / 100;
                  updateSettings({ music: { ...prefs, volume: v } });
                  player.setVolume(v);
                }} />
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.autoplay}
                onChange={(e) => updateSettings({ music: { ...prefs, autoplay: e.target.checked } })} />
              Auto-play my default playlist when a study session starts
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.chime}
                onChange={(e) => updateSettings({ music: { ...prefs, chime: e.target.checked } })} />
              Gentle chime when a Pomodoro phase ends
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.musicDuring.flashcards}
                onChange={(e) => updateSettings({ music: { ...prefs, musicDuring: { ...prefs.musicDuring, flashcards: e.target.checked } } })} />
              Music during flashcards
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.musicDuring.quizzes}
                onChange={(e) => updateSettings({ music: { ...prefs, musicDuring: { ...prefs.musicDuring, quizzes: e.target.checked } } })} />
              Music during quizzes
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.musicDuring.reading}
                onChange={(e) => updateSettings({ music: { ...prefs, musicDuring: { ...prefs.musicDuring, reading: e.target.checked } } })} />
              Music while reading lessons
            </label>
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={prefs.musicDuring.breaks}
                onChange={(e) => updateSettings({ music: { ...prefs, musicDuring: { ...prefs.musicDuring, breaks: e.target.checked } } })} />
              Music during breaks
            </label>
          </div>
          <p className="text-xs mt-3" style={{ color: 'var(--muted)' }}>
            Volume and playlist choices sync with your account when signed in; device-specific
            caches never sync. Playback always starts from your own tap (browser autoplay rules
            are respected).
          </p>
        </Card>
      )}
    </div>
  );
}

function TrackRow({ track, art, active, playing, favorite, downloaded, queueIds }: {
  track: MusicTrack; art: string; active: boolean; playing: boolean;
  favorite: boolean; downloaded: boolean; queueIds: string[];
}) {
  return (
    <div
      className="card-soft p-3 flex items-center gap-3"
      style={active ? { outline: '2px solid var(--primary)' } : undefined}
    >
      <button
        className="btn-primary !p-2 !min-h-[42px] !w-[42px]"
        aria-label={playing ? `Pause ${track.title}` : `Play ${track.title}`}
        onClick={() => (playing ? player.pause() : void player.playTrack(track.id, queueIds))}
      >
        {playing ? '⏸' : '▶'}
      </button>
      <MusicArt id={art} size={46} />
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold truncate">{track.title}</div>
        <div className="text-xs truncate" style={{ color: 'var(--muted)' }}>
          {track.artist ?? 'MedTech Companion Studio'} · {formatTime(track.durationSec)} · CC0 original
        </div>
      </div>
      <button
        className="btn-ghost !p-2 !min-h-[38px] text-base"
        aria-label={favorite ? 'Remove from favorites' : 'Add to favorites'}
        onClick={() => toggleFavoriteTrack(track.id)}
      >
        {favorite ? '♥' : '♡'}
      </button>
      <button
        className="btn-ghost !p-2 !min-h-[38px] text-xs"
        aria-label={downloaded ? 'Remove download' : 'Download for offline'}
        onClick={async () => {
          if (downloaded) await player.removeDownload(track.id);
          else await player.download(track.id);
          toggleDownload(track.id);
        }}
      >
        {downloaded ? '✓ saved' : '⤓'}
      </button>
    </div>
  );
}

function FullPlayer() {
  const st = usePlayer();
  const track = musicCatalog.tracks.find((t) => t.id === st.trackId);
  if (!track) return null;
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <button className="btn-ghost !p-2" aria-label="Previous" onClick={() => player.previous()}>⏮</button>
        <button className="btn-primary !px-6" aria-label={st.playing ? 'Pause' : 'Play'} onClick={() => void player.toggle()}>
          {st.playing ? '⏸ Pause' : '▶ Play'}
        </button>
        <button className="btn-ghost !p-2" aria-label="Next" onClick={() => player.next()}>⏭</button>
        <button
          className={cx('btn-ghost !py-1.5 text-xs', st.shuffle && 'btn-secondary')}
          onClick={() => player.toggleShuffle()}
        >
          🔀 Shuffle
        </button>
        <button
          className={cx('btn-ghost !py-1.5 text-xs', st.repeat !== 'off' && 'btn-secondary')}
          onClick={() => player.setRepeat(st.repeat === 'off' ? 'track' : st.repeat === 'track' ? 'playlist' : 'off')}
        >
          {st.repeat === 'track' ? '🔂 Repeat track' : st.repeat === 'playlist' ? '🔁 Repeat playlist' : '➡️ Repeat off'}
        </button>
        <button className="btn-ghost !p-2" aria-label={st.muted ? 'Unmute' : 'Mute'} onClick={() => player.toggleMute()}>
          {st.muted ? '🔇' : '🔊'}
        </button>
      </div>

      <div className="flex items-center gap-3">
        <span className="text-xs w-12 text-right">{formatTime(st.positionSec)}</span>
        <input
          type="range"
          min={0}
          max={Math.max(1, Math.floor(st.durationSec))}
          value={Math.floor(st.positionSec)}
          className="flex-1"
          aria-label="Seek position"
          onChange={(e) => player.seek(Number(e.target.value))}
        />
        <span className="text-xs w-12">{formatTime(st.durationSec)}</span>
      </div>

      <div className="flex items-center gap-3">
        <span className="text-xs">Volume</span>
        <input
          type="range"
          min={0}
          max={100}
          value={Math.round(st.volume * 100)}
          className="w-40"
          aria-label="Volume"
          onChange={(e) => player.setVolume(Number(e.target.value) / 100)}
        />
        <span className="text-xs" style={{ color: 'var(--muted)' }}>
          {track.artist ?? 'MedTech Companion Studio'} — original generative composition (CC0)
        </span>
      </div>
    </div>
  );
}
