'use client';

// My Cozy Study Room — full-screen focus: Pomodoro + study music + motivation.
// (Website: no app blocking by design — the Android app owns that feature.)
import Link from 'next/link';
import React, { useEffect, useRef, useState } from 'react';
import { Badge, Card, SectionTitle, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { usePlayer } from '@/components/MiniPlayer';
import { formatTime, player, playlistTracks } from '@/lib/audio/engine';
import { musicCatalog, motivation } from '@/lib/content';
import { contextQuote, dailyQuote, dayKey, pickLoveNote } from '@/lib/motivation';
import { logSession, useStore } from '@/lib/store';

type Phase = 'focus' | 'shortBreak' | 'longBreak';
type Scene = 'sakura' | 'rainy' | 'night' | 'lavender' | 'minimal';

const PRESETS = [
  { id: '25-5', label: '25 / 5', focus: 25 * 60, brk: 5 * 60 },
  { id: '50-10', label: '50 / 10', focus: 50 * 60, brk: 10 * 60 },
  { id: '15-3', label: '15 / 3', focus: 15 * 60, brk: 3 * 60 },
];

const SCENES: { id: Scene; name: string; bg: string }[] = [
  { id: 'sakura', name: 'Sakura Morning', bg: 'linear-gradient(160deg, #FFF0F5 0%, #FDE7F1 55%, #F8E8FF 100%)' },
  { id: 'rainy', name: 'Rainy Pink Café', bg: 'linear-gradient(160deg, #E9D5FF 0%, #D8C3F0 60%, #C9B2F5 100%)' },
  { id: 'night', name: 'Cozy Night', bg: 'linear-gradient(160deg, #241B2E 0%, #322741 70%, #3B2A4E 100%)' },
  { id: 'lavender', name: 'Lavender Evening', bg: 'linear-gradient(160deg, #F7F2FF 0%, #E9D5FF 60%, #F8BBD0 130%)' },
  { id: 'minimal', name: 'Minimal Focus', bg: 'linear-gradient(160deg, #FAFAFA 0%, #F5F5F5 100%)' },
];

export default function StudyRoomPage() {
  const st = usePlayer();
  const settings = useStore((s) => s.settings);
  const loveNotes = useStore((s) => s.loveNotes);
  const garden = useStore((s) => s.garden);

  const [presetId, setPresetId] = useState('25-5');
  const [phase, setPhase] = useState<Phase>('focus');
  const [remaining, setRemaining] = useState(PRESETS[0].focus);
  const [running, setRunning] = useState(false);
  const [donePomodoros, setDonePomodoros] = useState(0);
  const [goal, setGoal] = useState('');
  const [scene, setScene] = useState<Scene>('sakura');
  const [distractionFree, setDistractionFree] = useState(false);
  const [nudge, setNudge] = useState<string | null>(null);
  const [awayCount, setAwayCount] = useState(0);
  const [welcomeBack, setWelcomeBack] = useState(false);
  const hiddenSinceRef = useRef<number | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const preset = PRESETS.find((p) => p.id === presetId) ?? PRESETS[0];
  const note = pickLoveNote(loveNotes);
  const quote = dailyQuote();
  const lalabsLines = motivation.mascotLines.lalabs ?? ['Lalabs is always here for you. 💗'];
  const lalabsLine = lalabsLines[Math.abs([...dayKey()].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) | 0, 7)) % lalabsLines.length];
  const playerPrefs = settings.music;

  const currentPlaylist =
    phase === 'focus'
      ? musicCatalog.suggestedPlaylists.find((p) => p.id === playerPrefs.defaultPlaylistId)
      : musicCatalog.suggestedPlaylists.find((p) => p.id === playerPrefs.nightPlaylistId);

  useEffect(() => {
    if (!running) return;
    intervalRef.current = setInterval(() => {
      setRemaining((r) => {
        if (r > 1) return r - 1;
        // phase complete
        setRunning(false);
        if (playerPrefs.chime) void player.chime(playerPrefs.volume * 0.7);
        if (phase === 'focus') {
          setDonePomodoros((d) => d + 1);
          logSession({
            id: `sess-${Date.now()}`,
            startedAt: new Date(Date.now() - preset.focus * 1000).toISOString(),
            kind: 'FOCUS',
            subjectId: null,
            minutes: Math.round(preset.focus / 60),
            itemsCompleted: 1,
          });
          const next: Phase = (donePomodoros + 1) % 4 === 0 ? 'longBreak' : 'shortBreak';
          setPhase(next);
          setNudge(contextQuote('afterGoals'));
          setRemaining(next === 'longBreak' ? preset.brk * 2 : preset.brk);
          if (!playerPrefs.musicDuring.breaks) player.pause();
        } else {
          setPhase('focus');
          setNudge(contextQuote('beforeStudy'));
          setRemaining(preset.focus);
        }
        return 0;
      });
    }, 1000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, phase, presetId, donePomodoros]);

  const startPause = () => {
    if (!running && phase === 'focus' && playerPrefs.autoplay && st.trackId === null) {
      const list = playlistTracks(currentPlaylist);
      if (list.length) void player.playTrack(list[0].id, list.map((t) => t.id));
    }
    setRunning((r) => !r);
  };

  // Gentle accountability (NOT blocking): iPadOS/ web cannot block other apps,
  // so we simply notice when she steps away during a focus session and welcome
  // her back warmly.
  useEffect(() => {
    const onVisibility = () => {
      if (document.hidden) {
        if (running && phase === 'focus') hiddenSinceRef.current = Date.now();
      } else if (hiddenSinceRef.current) {
        const awaySec = Math.round((Date.now() - hiddenSinceRef.current) / 1000);
        hiddenSinceRef.current = null;
        if (awaySec >= 20) {
          setAwayCount((c) => c + 1);
          setWelcomeBack(true);
          setTimeout(() => setWelcomeBack(false), 8000);
        }
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [running, phase]);

  const reset = () => {
    setRunning(false);
    setPhase('focus');
    setRemaining(preset.focus);
  };

  const mood = phase === 'focus' ? (running ? 'studying' : 'encouraging') : 'sleepy';
  const sceneDef = SCENES.find((s) => s.id === scene) ?? SCENES[0];

  return (
    <div
      className="rounded-[24px] border overflow-hidden"
      style={{ borderColor: 'var(--line)', background: sceneDef.bg, minHeight: '72vh' }}
    >
      {/* scene decorations */}
        {!distractionFree && <SceneDecor scene={scene} />}

        {welcomeBack && (
          <div className="relative mx-6 mt-4 sticky-note confetti-pop">
            💗 Welcome back! Mochi kept your seat warm — you were away for a moment. No pressure, just keep going.
          </div>
        )}

      <div className="relative p-6 sm:p-10 space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="font-display text-2xl font-bold">My Cozy Study Room 🌸</h1>
            <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
              Music, timer, and gentle encouragement — all in one calm place.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {SCENES.map((s) => (
              <button
                key={s.id}
                className={cx('btn text-xs', scene === s.id ? 'btn-primary' : 'btn-ghost')}
                onClick={() => setScene(s.id)}
              >
                {s.name}
              </button>
            ))}
            <button className={cx('btn text-xs', distractionFree ? 'btn-secondary' : 'btn-ghost')}
              onClick={() => setDistractionFree((v) => !v)}>
              {distractionFree ? 'Show decorations' : 'Distraction-free'}
            </button>
          </div>
        </div>

        <div className="grid lg:grid-cols-3 gap-6">
          {/* timer */}
          <Card className="text-center">
            <Badge tone={phase === 'focus' ? 'info' : 'success'}>
              {phase === 'focus' ? 'Focus time' : phase === 'shortBreak' ? 'Short break' : 'Long break'}
            </Badge>
            <div className="font-heading font-bold mt-3" style={{ fontSize: '3.2rem', color: 'var(--primary)', lineHeight: 1 }}>
              {formatTime(remaining)}
            </div>
            <div className="flex justify-center gap-2 mt-4">
              <button className="btn-primary" onClick={startPause}>
                {running ? '⏸ Pause' : '▶ Start'}
              </button>
              <button className="btn-ghost" onClick={reset}>↺ Reset</button>
            </div>
            <div className="flex justify-center gap-2 mt-3">
              {PRESETS.map((p) => (
                <button key={p.id} className={cx('chip', presetId === p.id && 'btn-secondary !rounded-full')}
                  onClick={() => { setPresetId(p.id); setRunning(false); setPhase('focus'); setRemaining(p.focus); }}>
                  {p.label}
                </button>
              ))}
            </div>
            <p className="text-xs mt-3" style={{ color: 'var(--muted)' }}>
              {donePomodoros} pomodoro{donePomodoros === 1 ? '' : 's'} done today · gentle chime {playerPrefs.chime ? 'on' : 'off'}
            </p>
            {phase !== 'focus' && (
              <button
                className="btn-secondary mt-3 w-full text-xs"
                onClick={() => {
                  const list = playlistTracks(currentPlaylist);
                  const fallback = playlistTracks(musicCatalog.suggestedPlaylists[0]);
                  const use = list.length ? list : fallback;
                  if (use.length) void player.playTrack(use[0].id, use.map((t) => t.id));
                }}
              >
                🎵 Play a calm break playlist
              </button>
            )}
          </Card>

          {/* goal + music */}
          <Card>
            <SectionTitle icon="🎯">Today’s study goal</SectionTitle>
            <input
              className="input"
              placeholder="e.g. Finish Blood Banking transfusion reactions"
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
            />
            <div className="mt-4">
              <SectionTitle icon="🎵">Music</SectionTitle>
              {st.trackId ? (
                <div className="text-sm">
                  <p style={{ color: 'var(--muted-strong)' }}>
                    Now playing: <strong>{musicCatalog.tracks.find((t) => t.id === st.trackId)?.title}</strong>
                  </p>
                  <div className="flex gap-2 mt-2">
                    <button className="btn-secondary text-xs" onClick={() => void player.toggle()}>
                      {st.playing ? '⏸ Pause' : '▶ Resume'}
                    </button>
                    <Link href="/music/" className="btn-ghost text-xs">Change music</Link>
                  </div>
                </div>
              ) : (
                <div className="text-sm">
                  <p style={{ color: 'var(--muted)' }}>No music playing. Pick a playlist to set the mood:</p>
                  <div className="flex flex-wrap gap-2 mt-2">
                    {musicCatalog.suggestedPlaylists.slice(0, 3).map((p) => {
                      const list = playlistTracks(p);
                      return (
                        <button key={p.id} className="chip"
                          onClick={() => list.length && void player.playTrack(list[0].id, list.map((t) => t.id))}>
                          ▶ {p.name}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
            <p className="text-xs mt-4" style={{ color: 'var(--muted)' }}>
              Note: the website keeps you focused with this room and timer — the Android app
              adds the optional distraction blocker.
            </p>
          </Card>

          {/* mascot + motivation */}
          <Card className="text-center">
            <Mochi mood={mood} size={120} className="loading-mochi" />
            <p className="text-sm mt-2" style={{ color: 'var(--muted-strong)' }}>
              {phase === 'focus' ? 'I’ll be right here while you focus.' : 'Break time. Stretch, hydrate, breathe. 🌸'}
            </p>
            {nudge && (
              <div className="sticky-note mt-3 text-xs">{nudge}</div>
            )}
            <div className="mt-4 rounded-[14px] p-3 text-left" style={{ background: 'var(--surface-2)' }}>
              <p className="text-xs uppercase tracking-wide" style={{ color: 'var(--muted)' }}>A little motivation for you</p>
              <p className="text-sm font-semibold mt-1">{quote}</p>
            </div>
            {note && (
              <div className="mt-3 rounded-[14px] p-3 text-left" style={{ background: 'var(--chip)' }}>
                <p className="text-xs uppercase tracking-wide" style={{ color: 'var(--muted)' }}>A little note for you</p>
                <p className="text-sm italic mt-1">“{note.text}”</p>
              </div>
            )}
            <div className="mt-3 rounded-[14px] p-3 text-left" style={{ background: 'var(--surface-2)' }}>
              <p className="text-xs uppercase tracking-wide" style={{ color: 'var(--muted)' }}>From Lalabs 💗</p>
              <p className="text-sm italic mt-1">{lalabsLine}</p>
            </div>
            <p className="text-xs mt-3" style={{ color: 'var(--muted)' }}>
              🌷 {garden.flowers.length} flowers in your Study Garden
            </p>
          </Card>
        </div>

        {/* iPad focus guide — honest tools, since iPadOS does not let apps block apps */}
        <Card>
          <SectionTitle icon="🍎">Focus on iPad (real tools that work)</SectionTitle>
          <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
            An honest note: iPadOS never lets an app block other apps (that is an Apple rule for
            every app, including this one). But the iPad has built-in tools that do exactly this
            job — and Mochi will notice when you step away and welcome you back
            {awayCount > 0 && <> (away moments this session: <strong>{awayCount}</strong>)</>}.
          </p>
          <div className="grid sm:grid-cols-3 gap-3 mt-4">
            <details className="rounded-[14px] p-3" style={{ background: 'var(--surface-2)' }}>
              <summary className="font-semibold text-sm cursor-pointer">🔒 Guided Access (closest to an app blocker)</summary>
              <ol className="text-xs mt-2 space-y-1 list-decimal pl-4" style={{ color: 'var(--muted-strong)' }}>
                <li>Settings → Accessibility → Guided Access → turn it on (set a passcode).</li>
                <li>Open this app, then triple-click the top/side button.</li>
                <li>Press Start — the iPad is now locked into this app until you enter the passcode.</li>
                <li>Perfect for study blocks: nothing else can be opened.</li>
              </ol>
            </details>
            <details className="rounded-[14px] p-3" style={{ background: 'var(--surface-2)' }}>
              <summary className="font-semibold text-sm cursor-pointer">⏱ Screen Time limits for distracting apps</summary>
              <ol className="text-xs mt-2 space-y-1 list-decimal pl-4" style={{ color: 'var(--muted-strong)' }}>
                <li>Settings → Screen Time → App Limits → Add Limit.</li>
                <li>Pick the apps that eat your time (TikTok, Instagram, games).</li>
                <li>Set a daily limit like 15 minutes during review season.</li>
                <li>Enable Downtime for your study hours so only study apps stay open.</li>
              </ol>
            </details>
            <details className="rounded-[14px] p-3" style={{ background: 'var(--surface-2)' }}>
              <summary className="font-semibold text-sm cursor-pointer">🌙 A Study Focus mode</summary>
              <ol className="text-xs mt-2 space-y-1 list-decimal pl-4" style={{ color: 'var(--muted-strong)' }}>
                <li>Settings → Focus → add a new Focus called “Study”.</li>
                <li>Silence notifications from everyone except a few people.</li>
                <li>Link it to a Home Screen page that only has this app + Music.</li>
                <li>Turn it on before you start — your brain will thank you.</li>
              </ol>
            </details>
          </div>
          <p className="text-xs mt-3" style={{ color: 'var(--muted)' }}>
            The Android version has a true distraction blocker (with your permission). On iPad,
            Guided Access is the strongest protection Apple allows — and honestly, it works.
          </p>
        </Card>
      </div>
    </div>
  );
}

function SceneDecor({ scene }: { scene: Scene }) {
  if (scene === 'minimal') return null;
  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden decor-only" aria-hidden>
      <svg viewBox="0 0 1200 600" className="w-full h-full opacity-70" preserveAspectRatio="xMidYMid slice">
        {scene === 'sakura' && (
          <g>
            <path d="M-20 80 C 200 40, 320 120, 520 60" stroke="#E78AB4" strokeWidth="10" fill="none" strokeLinecap="round" />
            {[[120, 90], [220, 60], [330, 110], [430, 70], [520, 55]].map(([x, y], i) => (
              <g key={i}>
                <circle cx={x} cy={y} r="16" fill="#F8BBD0" />
                <circle cx={x + 14} cy={y - 12} r="11" fill="#FBCFE8" />
                <circle cx={x - 12} cy={y - 10} r="10" fill="#FBCFE8" />
              </g>
            ))}
            <circle cx="1050" cy="90" r="46" fill="#FDE68A" opacity="0.7" />
          </g>
        )}
        {scene === 'rainy' && (
          <g>
            <rect x="900" y="40" width="220" height="260" rx="14" fill="#E9D5FF" opacity="0.55" stroke="#A98CF0" strokeWidth="6" />
            <path d="M1010 40 v260 M900 170 h220" stroke="#A98CF0" strokeWidth="6" />
            {Array.from({ length: 14 }).map((_, i) => (
              <path key={i} d={`M${915 + i * 15} 60 q-3 40 -6 80`} stroke="#B7A6E8" strokeWidth="3" strokeLinecap="round" />
            ))}
            <rect x="60" y="420" width="120" height="90" rx="12" fill="#F8BBD0" opacity="0.8" />
            <path d="M180 450 q34 0 34 24 q0 24 -34 24" fill="none" stroke="#E78AB4" strokeWidth="8" />
          </g>
        )}
        {scene === 'night' && (
          <g>
            {Array.from({ length: 26 }).map((_, i) => (
              <circle key={i} cx={(i * 97) % 1200} cy={(i * 53) % 220 + 20} r={i % 3 === 0 ? 3.5 : 2} fill="#F8BBD0" opacity="0.8" />
            ))}
            <path d="M1020 60 a44 44 0 1 0 34 68 a36 36 0 0 1 -34 -68z" fill="#FDE7F1" opacity="0.9" />
            {Array.from({ length: 10 }).map((_, i) => (
              <circle key={i} cx={120 + i * 100} cy={300} r="6" fill="#FBBF24" opacity="0.85" />
            ))}
            <path d="M120 300 q450 -40 960 0" stroke="#FBBF24" strokeWidth="2" fill="none" opacity="0.5" />
          </g>
        )}
        {scene === 'lavender' && (
          <g>
            <circle cx="1020" cy="100" r="52" fill="#F8BBD0" opacity="0.8" />
            <ellipse cx="180" cy="120" rx="120" ry="42" fill="#E9D5FF" opacity="0.8" />
            <ellipse cx="420" cy="90" rx="90" ry="34" fill="#F3E3FF" opacity="0.8" />
            {[[700, 500], [820, 520], [940, 490]].map(([x, y], i) => (
              <g key={i}>
                <path d={`M${x} ${y} q10 -40 24 0`} stroke="#7C5A70" strokeWidth="4" fill="none" />
                <circle cx={x + 12} cy={y - 42} r="14" fill="#C9B2F5" />
              </g>
            ))}
          </g>
        )}
      </svg>
    </div>
  );
}
