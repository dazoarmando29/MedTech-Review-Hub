'use client';

// App shell: pink theme engine (DESIGN.md v2), rounded nav, Mochi bubble,
// hydration-safe content mount, offline notice.
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import React, { useEffect, useState, useSyncExternalStore } from 'react';
import { useStore, updateSettings } from '@/lib/store';
import type { ThemeId } from '@/lib/types';
import { Mochi, type MochiMood } from './Mochi';
import { MiniPlayer, usePlayer } from './MiniPlayer';
import {
  IconBook, IconCards, IconGarden, IconHabits, IconHome, IconPdf, IconPlanner,
  IconQuiz, IconResources, IconSearch, IconSettings, IconSparkle,
} from './icons';

const emptySubscribe = () => () => {};
const useMounted = () => useSyncExternalStore(emptySubscribe, () => true, () => false);

const NAV: { href: string; label: string; icon: React.ComponentType<{ size?: number; className?: string }> }[] = [
  { href: '/', label: 'Home', icon: IconHome },
  { href: '/library/', label: 'Library', icon: IconBook },
  { href: '/flashcards/', label: 'Flashcards', icon: IconCards },
  { href: '/quiz/', label: 'Quizzes', icon: IconQuiz },
  { href: '/music/', label: 'Music', icon: IconSparkle },
  { href: '/pdf/', label: 'PDF', icon: IconPdf },
  { href: '/planner/', label: 'Planner', icon: IconPlanner },
  { href: '/habits/', label: 'Diary', icon: IconHabits },
  { href: '/garden/', label: 'Garden', icon: IconGarden },
  { href: '/resources/', label: 'Resources', icon: IconResources },
];

const THEME_CYCLE: ThemeId[] = ['strawberry', 'sakura', 'lavender', 'night', 'minimal'];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const mounted = useMounted();
  const playerState = usePlayer();
  const appTheme = useStore((s) => s.settings.appTheme);
  const textScale = useStore((s) => s.settings.textScale);
  const lowBandwidth = useStore((s) => s.settings.lowBandwidth);
  const reduceMotion = useStore((s) => s.settings.reduceMotion);
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const easyMode = useStore((s) => s.settings.easyMode);
  const mascotName = useStore((s) => s.settings.mascotName);
  const mascotKind = useStore((s) => s.settings.mascot);
  const commentary = useStore((s) => s.settings.mascotCommentary);
  const [online, setOnline] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const root = document.documentElement;
    let resolved: ThemeId = appTheme === 'system'
      ? window.matchMedia('(prefers-color-scheme: dark)').matches ? 'night' : 'strawberry'
      : appTheme;
    root.setAttribute('data-theme', resolved);
    root.style.setProperty('--text-scale', String(textScale / 100));
    document.body.classList.toggle('no-decor', resolved === 'minimal' || lowBandwidth || seriousMode);
    document.body.classList.toggle('no-motion', reduceMotion || lowBandwidth);
    document.body.classList.toggle('easy-mode', easyMode);
  }, [appTheme, textScale, lowBandwidth, reduceMotion, seriousMode, easyMode]);

  useEffect(() => {
    setOnline(navigator.onLine);
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', off);
    };
  }, []);

  useEffect(() => setMenuOpen(false), [pathname]);

  return (
    <div className="min-h-screen flex flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-3 focus:bg-white focus:text-pink-700 rounded-md">
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b" style={{ background: 'color-mix(in srgb, var(--bg) 88%, transparent)', borderColor: 'var(--line)', backdropFilter: 'blur(10px)' }}>
        <div className="max-w-[1480px] mx-auto px-4 py-3 flex items-center gap-4">
          <Link href="/" className="flex items-center gap-2.5 font-display font-bold text-lg shrink-0 whitespace-nowrap">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={`${process.env.NEXT_PUBLIC_BASE_PATH || ''}/icon.svg`} alt="" width={42} height={42} className="rounded-xl shrink-0" />
            <span className="leading-tight">
              MedTech <span style={{ color: 'var(--primary)' }}>Study Companion</span>
            </span>
          </Link>

          <nav aria-label="Main" className="hidden lg:flex items-center gap-1.5 ml-auto">
            {NAV.map((item) => {
              const active = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href as string}
                  className="px-3 py-2 rounded-full text-sm font-semibold flex items-center gap-1.5 transition-colors whitespace-nowrap"
                  style={{
                    background: active ? 'var(--primary)' : 'transparent',
                    color: active ? 'var(--primary-ink)' : 'var(--muted-strong)',
                    minHeight: 40,
                  }}
                  aria-current={active ? 'page' : undefined}
                  aria-label={item.label}
                  title={item.label}
                >
                  <Icon size={17} />
                  <span className="hidden 2xl:inline">{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="ml-auto lg:ml-3 flex items-center gap-2 shrink-0">
            <button
              className="btn-ghost !p-2 !min-h-[40px] !w-[40px] text-base"
              aria-label={`Theme: ${appTheme}. Click to switch.`}
              title={`Theme: ${appTheme}`}
              onClick={() => {
                const idx = THEME_CYCLE.indexOf(appTheme as ThemeId);
                const next = THEME_CYCLE[(idx + 1) % THEME_CYCLE.length];
                updateSettings({ appTheme: next });
              }}
            >
              🌸
            </button>
            <Link href="/settings/" className="btn-ghost !p-2 !min-h-[40px] !w-[40px]" aria-label="Settings">
              <IconSettings size={18} />
            </Link>
            <button
              className="btn-ghost !p-2 !min-h-[40px] lg:hidden"
              aria-label="Open menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((v) => !v)}
            >
              ☰
            </button>
          </div>
        </div>

        {menuOpen && (
          <nav aria-label="Mobile" className="lg:hidden px-4 pb-3 grid grid-cols-2 gap-2">
            {[...NAV,
              { href: '/study-room/', label: 'Cozy Study Room', icon: IconSparkle },
              { href: '/games/', label: 'Games', icon: IconSparkle },
              { href: '/mistakes/', label: 'Mistake Notebook', icon: IconBook },
              { href: '/quick-review/', label: 'Quick Review', icon: IconCards },
              { href: '/search/', label: 'Search', icon: IconSearch },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <Link key={item.href} href={item.href as string} className="btn-ghost justify-start">
                  <Icon size={16} /> {item.label}
                </Link>
              );
            })}
          </nav>
        )}
      </header>

      {!online && (
        <div className="text-sm text-center py-2" style={{ background: 'var(--chip)', color: 'var(--muted-strong)' }}>
          You are offline — your downloaded lessons, flashcards, and quizzes are still here. Progress syncs when you reconnect.
        </div>
      )}

      <main
        id="main"
        className="flex-1 max-w-6xl w-full mx-auto px-4"
        style={{ paddingTop: 24, paddingBottom: playerState.trackId ? 110 : 24 }}
      >
        {mounted ? children : <LoadingMochi />}
      </main>

      <MiniPlayer />

      {!seriousMode && <MochiDock name={mascotName} kind={mascotKind} commentary={commentary} />}

      <footer className="border-t py-6 text-center text-xs space-y-1" style={{ borderColor: 'var(--line)', color: 'var(--muted)' }}>
        <p>
          MedTech Study Companion — an independent study platform. Not affiliated with or endorsed by ASCP.
        </p>
        <p>
          <Link href="/settings/" className="underline">Settings &amp; disclaimers</Link>
          {' · '}
          <Link href="/resources/" className="underline">ASCPi resources</Link>
          {' · '}
          <a href="https://github.com/dazoarmando29/MedTech-Review-Hub" className="underline" target="_blank" rel="noreferrer">
            Source &amp; releases
          </a>
        </p>
      </footer>
    </div>
  );
}

function LoadingMochi() {
  return (
    <div className="flex flex-col items-center justify-center py-24 gap-4" role="status" aria-live="polite">
      <Mochi mood="studying" size={110} className="" />
      <p className="text-sm" style={{ color: 'var(--muted)' }}>Warming up your study nook…</p>
    </div>
  );
}

const LINES: Record<string, { cat: string[]; dog: string[] }> = {
  home: {
    cat: ['Anong susunod mong aaralin, future MLS?', 'Your future self is cheering for you!'],
    dog: ['Woof! Your study streak needs attention.', 'One more quiz before scrolling?'],
  },
  library: {
    cat: ['Deep Study mode: para sa mga gustong maging RMT talaga.', 'High-yield facts first. Chika later.'],
    dog: ['Good students read the traps section. Good dogs get treats.'],
  },
  flashcards: {
    cat: ['You have flashcards waiting. Explain yourself.', 'Rated “Easy”? Sige, kita natin bukas.'],
    dog: ['Woof! Again means again. No shortcuts!'],
  },
  quiz: {
    cat: ['Read the vignette twice. Like a cat stalking a laser.', 'Wrong answers are free teachers.'],
    dog: ['Focus muna. We can celebrate later.'],
  },
  garden: {
    cat: ['Your flowers are growing with every study session!', 'Isang bloom pa, tapos celebratory milk na.'],
    dog: ['Water the flowers by studying. Woof!'],
  },
  default: {
    cat: ['Kaya mo ’to, future MLS.', 'One chapter at a time. 🌸'],
    dog: ['Good grades, good treats.', 'Laban lang, future MLS!'],
  },
};

function MochiDock({ name, kind, commentary }: { name: string; kind: 'cat' | 'dog'; commentary: boolean }) {
  const pathname = usePathname();
  const [idx, setIdx] = useState(0);
  const key = pathname.startsWith('/library') ? 'library'
    : pathname.startsWith('/flashcards') ? 'flashcards'
    : pathname.startsWith('/quiz') ? 'quiz'
    : pathname.startsWith('/garden') ? 'garden'
    : pathname === '/' ? 'home' : 'default';
  const lines = LINES[key][kind === 'cat' ? 'cat' : 'dog'];

  useEffect(() => {
    setIdx(Math.floor(Math.random() * lines.length));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, kind]);

  if (!commentary) return null;

  const mood: MochiMood = key === 'quiz' ? 'encouraging' : key === 'garden' ? 'excited' : 'happy';

  return (
    <div className="fixed bottom-4 right-4 z-40 hidden sm:block">
      <div className="flex items-start gap-1">
        <Mochi mood={mood} size={72} className="" />
        <div className="card-soft p-3 text-xs leading-snug max-w-[210px]">
          <strong className="font-heading block" style={{ color: 'var(--primary)' }}>{name}</strong>
          <p>{lines[idx % lines.length]}</p>
          <button className="underline mt-1" style={{ color: 'var(--muted)' }} onClick={() => setIdx((i) => (i + 1) % lines.length)}>
            another one
          </button>
        </div>
      </div>
    </div>
  );
}
