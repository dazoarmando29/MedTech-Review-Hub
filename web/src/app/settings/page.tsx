'use client';

// Settings — pink study companion controls: themes, personal touch, mascot,
// highlight meanings, accessibility, sync, data portability, disclaimers.
import React, { useState } from 'react';
import { Badge, Card, ConfirmButton, SectionTitle, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { resetAll, removeLoveNote, todayKey, updateSettings, upsertLoveNote, useStore } from '@/lib/store';
import { ttsSupported } from '@/lib/tts';
import type { HighlightColor, LoveNote, Settings as SettingsType, ThemeId } from '@/lib/types';

const ALL_COLORS: HighlightColor[] = ['YELLOW', 'PINK', 'PURPLE', 'BLUE', 'GREEN', 'ROSE'];

const THEMES: { id: ThemeId | 'system'; name: string; swatch: string[] }[] = [
  { id: 'strawberry', name: 'Strawberry Milk', swatch: ['#FFF0F5', '#F8BBD0', '#DB2777'] },
  { id: 'sakura', name: 'Sakura Blossom', swatch: ['#FFF5F7', '#FBD3E0', '#E56B8D'] },
  { id: 'lavender', name: 'Lavender Dreams', swatch: ['#F7F2FF', '#E9D5FF', '#8B5CF6'] },
  { id: 'night', name: 'Cozy Night', swatch: ['#241B2E', '#7C3AED', '#F472B6'] },
  { id: 'minimal', name: 'Minimal Study', swatch: ['#FAFAFA', '#E5E7EB', '#352735'] },
  { id: 'system', name: 'Follow system', swatch: ['#FFF0F5', '#241B2E', '#DB2777'] },
];

export default function SettingsPage() {
  const settings = useStore((s) => s.settings);
  const state = useStore((s) => s);
  const [saved, setSaved] = useState(false);

  const patch = (p: Partial<SettingsType>) => {
    updateSettings(p);
    setSaved(true);
    setTimeout(() => setSaved(false), 1500);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <h1 className="font-display text-2xl font-bold">Settings 🌸</h1>
        {saved && <Badge tone="success">saved</Badge>}
      </div>

      <Card>
        <SectionTitle icon="🎨">Theme</SectionTitle>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {THEMES.map((t) => (
            <button
              key={t.id}
              className={cx('rounded-[16px] border p-3 text-left transition-shadow', settings.appTheme === t.id ? 'shadow-lift' : '')}
              style={{ borderColor: settings.appTheme === t.id ? 'var(--primary)' : 'var(--line)', background: 'var(--surface-2)' }}
              aria-pressed={settings.appTheme === t.id}
              onClick={() => patch({ appTheme: t.id })}
            >
              <div className="flex gap-1">
                {t.swatch.map((c) => (
                  <span key={c} className="h-6 w-6 rounded-full border" style={{ background: c, borderColor: 'var(--line)' }} />
                ))}
              </div>
              <div className="text-sm font-semibold mt-2">{t.name}</div>
            </button>
          ))}
        </div>
      </Card>

      <Card>
        <SectionTitle icon="💌">Personal Touch</SectionTitle>
        <label className="block text-sm">
          Display name
          <input className="input mt-1" value={settings.displayName}
            onChange={(e) => patch({ displayName: e.target.value.slice(0, 40) })}
            placeholder="future MLS" />
        </label>
        <label className="block text-sm mt-3">
          Gift note (shown on your dashboard)
          <textarea className="input mt-1" rows={2} value={settings.giftNote}
            onChange={(e) => patch({ giftNote: e.target.value.slice(0, 300) })} />
        </label>
        <label className="block text-sm mt-3">
          Your personal motivation line (optional — replaces the daily line)
          <input className="input mt-1" value={settings.motivationNote}
            onChange={(e) => patch({ motivationNote: e.target.value.slice(0, 120) })}
            placeholder="e.g. RMT by 2027 — for us. 🌸" />
        </label>
        <LittleNotesManager />
      </Card>

      <Card>
        <SectionTitle icon="🐾">Study buddy</SectionTitle>
        <div className="flex flex-wrap items-center gap-6">
          <Mochi mood="happy" size={110} />
          <div className="flex-1 min-w-[220px] space-y-3">
            <label className="block text-sm">
              Mascot name
              <input className="input mt-1" value={settings.mascotName}
                onChange={(e) => patch({ mascotName: e.target.value.slice(0, 24) })} />
            </label>
            <div className="flex flex-wrap gap-3 text-sm">
              <label className="flex items-center gap-2">
                <input type="radio" checked={settings.mascot === 'cat'} onChange={() => patch({ mascot: 'cat' })} />
                Mochi (cat)
              </label>
              <label className="flex items-center gap-2">
                <input type="radio" checked={settings.mascot === 'dog'} onChange={() => patch({ mascot: 'dog' })} />
                Bantay (dog companion)
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={settings.mascotCommentary}
                  onChange={(e) => patch({ mascotCommentary: e.target.checked })} />
                Show mascot commentary
              </label>
            </div>
          </div>
        </div>
      </Card>

      <Card>
        <SectionTitle icon="🖍️">Highlight color meanings</SectionTitle>
        <div className="grid sm:grid-cols-2 gap-3">
          {ALL_COLORS.map((c) => (
            <label key={c} className="text-sm flex items-center gap-2">
              <span className={cx('h-6 w-6 rounded-full inline-block border', `hl-${c}`)} style={{ borderColor: 'var(--line-strong)' }} />
              <span className="w-16 lowercase">{c.toLowerCase()}</span>
              <input
                className="input"
                value={settings.highlightColors[c]}
                onChange={(e) => patch({ highlightColors: { ...settings.highlightColors, [c]: e.target.value } })}
              />
            </label>
          ))}
        </div>
      </Card>

      <Card>
        <SectionTitle icon="✨">Experience</SectionTitle>
        <div className="grid sm:grid-cols-2 gap-3 text-sm">
          <label>
            Text scale: {settings.textScale}%
            <input type="range" min={100} max={150} step={5} value={settings.textScale}
              className="w-full mt-2" onChange={(e) => patch({ textScale: Number(e.target.value) as SettingsType['textScale'] })} />
          </label>
          <label>
            Explanation style
            <select className="input mt-1" value={settings.explanationStyle}
              onChange={(e) => patch({ explanationStyle: e.target.value as SettingsType['explanationStyle'] })}>
              <option value="FORMAL_ENGLISH">Formal English</option>
              <option value="SIMPLE_ENGLISH">Simple English</option>
              <option value="TAGLISH">Taglish</option>
            </select>
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.seriousMode} onChange={(e) => patch({ seriousMode: e.target.checked })} />
            Serious Mode (hides jokes, memes, mascot commentary)
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.funnyCommentary} onChange={(e) => patch({ funnyCommentary: e.target.checked })} />
            Funny quiz result commentary
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.reduceMotion} onChange={(e) => patch({ reduceMotion: e.target.checked })} />
            Reduce animation
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.lowBandwidth} onChange={(e) => patch({ lowBandwidth: e.target.checked })} />
            Low-bandwidth mode (no decorations)
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.ttsEnabled} disabled={!ttsSupported()}
              onChange={(e) => patch({ ttsEnabled: e.target.checked })} />
            Text-to-speech {ttsSupported() ? '' : '(not supported by this browser)'}
          </label>
          <label>
            Motivational quote frequency
            <select className="input mt-1" value={settings.music.quoteFrequency}
              onChange={(e) => patch({ music: { ...settings.music, quoteFrequency: e.target.value as SettingsType['music']['quoteFrequency'] } })}>
              <option value="daily">Daily</option>
              <option value="session">Each study session</option>
              <option value="off">Off</option>
            </select>
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.music.ambientAnimations}
              onChange={(e) => patch({ music: { ...settings.music, ambientAnimations: e.target.checked } })} />
            Ambient animations (study room, mascots)
          </label>
        </div>
      </Card>

      <Card>
        <SectionTitle icon="☁️">Sync &amp; account (optional)</SectionTitle>
        <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
          Everything works offline. A hosted backend already exists on Cloudflare (default below) —
          sign in on the Login page to sync with the Android app. Credentials stay on this device
          and are only sent to the server you specify.
        </p>
        <label className="block text-sm mt-3">
          Backend API base URL
          <input className="input mt-1" placeholder="https://medtech-review-hub-api.dazoarmando.workers.dev"
            value={settings.apiBaseUrl} onChange={(e) => patch({ apiBaseUrl: e.target.value })} />
        </label>
      </Card>

      <Card>
        <SectionTitle icon="📦">Your data</SectionTitle>
        <div className="flex flex-wrap gap-2">
          <button
            className="btn-primary"
            onClick={() => {
              const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `medtech-study-companion-${todayKey()}.json`;
              a.click();
              URL.revokeObjectURL(url);
            }}
          >
            Export all my data (JSON)
          </button>
          <button
            className="btn-ghost"
            onClick={() => {
              const rows = state.highlights.map((h) => `${h.color}\t${h.text}\t${h.note}`).join('\n');
              const blob = new Blob([`color\ttext\tnote\n${rows}`], { type: 'text/tab-separated-values' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = 'my-highlights.tsv';
              a.click();
              URL.revokeObjectURL(url);
            }}
          >
            Export highlights
          </button>
          <ConfirmButton
            label="Reset all local data"
            confirmLabel="This deletes everything on this device. Confirm?"
            className="btn-danger"
            onConfirm={() => resetAll()}
          />
        </div>
        <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
          PDFs live in your browser’s IndexedDB and stay private to this device unless you upload
          them to a backend you control. We never train models on your documents.
        </p>
      </Card>

      <Card>
        <SectionTitle icon="⚖️">Disclaimers</SectionTitle>
        <ul className="text-xs space-y-1 list-disc pl-5" style={{ color: 'var(--muted-strong)' }}>
          <li>MedTech Study Companion is an independent study platform. It is NOT affiliated with, endorsed by, or sponsored by ASCP or any review center listed in Resources.</li>
          <li>Practice scores, readiness percentages, and mastery maps are your own study estimates — not ASCP scores and not guarantees of passing.</li>
          <li>The adaptive practice mode uses a documented self-adjusting difficulty rule. It is NOT a reproduction of ASCP’s computer-adaptive testing.</li>
          <li>Medical reference ranges vary by laboratory, method, age, sex, and population. Always verify against your local laboratory.</li>
          <li>Content is original teaching material mapped at topic level to public exam content outlines; no proprietary exam questions or review-center handouts are reproduced.</li>
          <li>The website has no app-blocking features. Distraction/app blocking exists only in the Android app and cannot guarantee absolute blocking (platform limitation).</li>
        </ul>
      </Card>
    </div>
  );
}

function LittleNotesManager() {
  const loveNotes = useStore((s) => s.loveNotes);
  const [draft, setDraft] = useState('');
  const [showDate, setShowDate] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);

  const save = () => {
    if (!draft.trim()) return;
    const note: LoveNote = {
      id: editingId ?? `ln-${Date.now()}`,
      text: draft.trim().slice(0, 400),
      showDate: showDate || null,
      favorite: editingId ? (loveNotes.find((n) => n.id === editingId)?.favorite ?? false) : false,
      createdAt: editingId ? (loveNotes.find((n) => n.id === editingId)?.createdAt ?? new Date().toISOString()) : new Date().toISOString(),
    };
    upsertLoveNote(note);
    setDraft('');
    setShowDate('');
    setEditingId(null);
  };

  return (
    <div className="mt-5">
      <SectionTitle icon="💌">Little Notes for You</SectionTitle>
      <p className="text-xs" style={{ color: 'var(--muted)' }}>
        Personal encouragement written by someone who believes in her — shown on the dashboard
        and in the Study Room. Private: stored on this device only.
      </p>
      <div className="flex flex-wrap gap-2 mt-2">
        <input className="input flex-1 min-w-[220px]" placeholder="e.g. I'm so proud of how hard you're working."
          value={draft} onChange={(e) => setDraft(e.target.value)} />
        <input className="input !w-44" type="date" value={showDate}
          onChange={(e) => setShowDate(e.target.value)} aria-label="Show on date (optional)" />
        <button className="btn-primary" disabled={!draft.trim()} onClick={save}>
          {editingId ? 'Update note' : 'Add note'}
        </button>
      </div>
      <ul className="space-y-2 mt-3">
        {loveNotes.length === 0 && (
          <li className="text-xs" style={{ color: 'var(--muted)' }}>No notes yet — the first one will make her day.</li>
        )}
        {loveNotes.map((n) => (
          <li key={n.id} className="rounded-[12px] p-3 flex items-start gap-2" style={{ background: 'var(--surface-2)' }}>
            <div className="flex-1">
              <p className="text-sm italic">“{n.text}”</p>
              <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
                {n.showDate ? `shows on ${n.showDate}` : 'any day'} {n.favorite && '· ♥ favorite'}
              </p>
            </div>
            <button className="chip" onClick={() => upsertLoveNote({ ...n, favorite: !n.favorite })}>
              {n.favorite ? '♥' : '♡'}
            </button>
            <button className="chip" onClick={() => { setDraft(n.text); setShowDate(n.showDate ?? ''); setEditingId(n.id); }}>
              edit
            </button>
            <ConfirmButton label="✕" confirmLabel="Delete?" className="chip"
              onConfirm={() => removeLoveNote(n.id)} />
          </li>
        ))}
      </ul>
    </div>
  );
}
