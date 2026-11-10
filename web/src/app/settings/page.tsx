'use client';

// Settings: appearance, accessibility, mascot, highlight colors, sync, data
// portability, and disclaimers.
import React, { useState } from 'react';
import { Badge, Card, ConfirmButton, SectionTitle } from '@/components/ui';
import { resetAll, todayKey, updateSettings, useStore } from '@/lib/store';
import { ttsSupported } from '@/lib/tts';
import type { HighlightColor, Settings as SettingsType } from '@/lib/types';

const ALL_COLORS: HighlightColor[] = ['YELLOW', 'RED', 'BLUE', 'GREEN', 'PURPLE'];

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
        <h1 className="font-heading text-2xl font-bold">Settings</h1>
        {saved && <Badge tone="success">saved</Badge>}
      </div>

      <Card>
        <SectionTitle icon="🎨">Appearance &amp; accessibility</SectionTitle>
        <div className="grid sm:grid-cols-2 gap-4 text-sm">
          <label>
            Theme
            <select className="input mt-1" value={settings.theme} onChange={(e) => patch({ theme: e.target.value as SettingsType['theme'] })}>
              <option value="system">System</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
          <label>
            Text scale: {settings.textScale}%
            <input type="range" min={100} max={150} step={5} value={settings.textScale}
              className="w-full mt-2" onChange={(e) => patch({ textScale: Number(e.target.value) as SettingsType['textScale'] })} />
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.reduceMotion} onChange={(e) => patch({ reduceMotion: e.target.checked })} />
            Reduce animation
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.ttsEnabled} disabled={!ttsSupported()} onChange={(e) => patch({ ttsEnabled: e.target.checked })} />
            Text-to-speech {ttsSupported() ? '' : '(not supported by this browser)'}
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.seriousMode} onChange={(e) => patch({ seriousMode: e.target.checked })} />
            Serious Mode (hides jokes, memes, mascot commentary)
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
        </div>
      </Card>

      <Card>
        <SectionTitle icon="🐾">Mascot</SectionTitle>
        <div className="flex flex-wrap gap-4 items-center text-sm">
          <label className="flex items-center gap-2">
            <input type="radio" checked={settings.mascot === 'cat'} onChange={() => patch({ mascot: 'cat' })} />
            🐱 Mimi (cat)
          </label>
          <label className="flex items-center gap-2">
            <input type="radio" checked={settings.mascot === 'dog'} onChange={() => patch({ mascot: 'dog' })} />
            🐶 Bantay (dog)
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={settings.mascotCommentary} onChange={(e) => patch({ mascotCommentary: e.target.checked })} />
            Show mascot commentary
          </label>
        </div>
      </Card>

      <Card>
        <SectionTitle icon="🖍️">Highlight color meanings</SectionTitle>
        <div className="grid sm:grid-cols-2 gap-3">
          {ALL_COLORS.map((c) => (
            <label key={c} className="text-sm flex items-center gap-2">
              <span className={`h-5 w-5 rounded inline-block hl-${c}`} />
              <span className="w-16">{c.toLowerCase()}</span>
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
        <SectionTitle icon="☁️">Sync &amp; account (optional)</SectionTitle>
        <p className="text-sm text-[var(--ink-muted)]">
          The app is fully functional offline. A hosted backend already exists on Cloudflare
          (default below) — sign in on the Login page to sync this website with the Android app.
          You can also self-host (see backend/README.md or cloudflare-worker/README.md) and
          point the URL there. Credentials are stored only on this device and sent only to the
          server you specify.
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
            className="btn-secondary"
            onClick={() => {
              const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `medtech-review-hub-export-${todayKey()}.json`;
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
        <p className="text-xs text-[var(--ink-muted)] mt-2">
          PDF files are stored in your browser&apos;s IndexedDB and are private to this device unless you
          upload them to a backend you control. We never train models on your documents.
        </p>
      </Card>

      <Card>
        <SectionTitle icon="⚖️">Disclaimers</SectionTitle>
        <ul className="text-xs text-[var(--ink-muted)] list-disc pl-5 space-y-1">
          <li>MedTech Review Hub is an independent study platform. It is NOT affiliated with, endorsed by, or sponsored by ASCP or any other examination body.</li>
          <li>Practice scores, readiness percentages, and mastery maps are your own study estimates — not ASCP scores and not guarantees of passing.</li>
          <li>The adaptive practice mode uses a documented self-adjusting difficulty rule. It is NOT a reproduction of ASCP&apos;s computer-adaptive testing.</li>
          <li>Medical reference ranges vary by laboratory, method, age, sex, and population. Always verify against your local laboratory.</li>
          <li>Content is original teaching material mapped at topic level to public exam content outlines; no proprietary exam questions are reproduced.</li>
          <li>The website has no app-blocking features. Distraction/app blocking exists only in the Android app and cannot guarantee absolute blocking (platform limitation).</li>
        </ul>
      </Card>
    </div>
  );
}
