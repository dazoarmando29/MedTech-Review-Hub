'use client';

// App shell: theme handling, responsive navigation, mascot bubble, offline notice.
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import React, { useEffect, useState } from 'react';
import { useStore, updateSettings } from '@/lib/store';
import { cx } from './ui';

const NAV = [
  { href: '/', label: 'Home', icon: '🏠' },
  { href: '/library/', label: 'Library', icon: '📚' },
  { href: '/flashcards/', label: 'Flashcards', icon: '🃏' },
  { href: '/quiz/', label: 'Quizzes', icon: '📝' },
  { href: '/pdf/', label: 'PDF Reader', icon: '📄' },
  { href: '/habits/', label: 'Habits', icon: '📅' },
  { href: '/planner/', label: 'Planner', icon: '🗺️' },
  { href: '/games/', label: 'Games', icon: '🎮' },
  { href: '/search/', label: 'Search', icon: '🔍' },
  { href: '/settings/', label: 'Settings', icon: '⚙️' },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useStore((s) => s.settings.theme);
  const textScale = useStore((s) => s.settings.textScale);
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const [online, setOnline] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const root = document.documentElement;
    const dark =
      theme === 'dark' ||
      (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    root.classList.toggle('dark', dark);
    root.style.setProperty('--text-scale', String(textScale / 100));
  }, [theme, textScale]);

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
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:p-3 focus:bg-white focus:text-blue-700">
        Skip to content
      </a>

      <header className="sticky top-0 z-40 backdrop-blur bg-white/85 dark:bg-slate-900/85 border-b border-slate-200 dark:border-slate-800">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-3">
          <Link href="/" className="flex items-center gap-2 font-heading font-bold text-lg">
            <span aria-hidden className="text-2xl">🧬</span>
            <span>
              MedTech <span style={{ color: 'var(--brand-2)' }}>Review Hub</span>
            </span>
          </Link>

          <nav aria-label="Main" className="hidden lg:flex items-center gap-1 ml-auto">
            {NAV.map((item) => {
              const active = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href as string}
                  className={cx(
                    'px-3 py-2 rounded-md text-sm font-medium min-h-[40px] flex items-center gap-1.5',
                    active
                      ? 'bg-blue-600 text-white'
                      : 'text-[var(--ink-muted)] hover:bg-slate-100 dark:hover:bg-slate-800',
                  )}
                  aria-current={active ? 'page' : undefined}
                >
                  <span aria-hidden>{item.icon}</span>
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="ml-auto lg:ml-2 flex items-center gap-2">
            <button
              className="btn-ghost !p-2 !min-h-[40px] !w-[40px]"
              aria-label="Toggle dark mode"
              onClick={() =>
                updateSettings({
                  theme:
                    document.documentElement.classList.contains('dark') ? 'light' : 'dark',
                })
              }
            >
              🌗
            </button>
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
            {NAV.map((item) => (
              <Link key={item.href} href={item.href as string} className="btn-ghost justify-start">
                <span aria-hidden>{item.icon}</span> {item.label}
              </Link>
            ))}
          </nav>
        )}
      </header>

      {!online && (
        <div className="bg-amber-100 dark:bg-amber-900/50 text-amber-900 dark:text-amber-100 text-sm text-center py-2">
          You are offline. Your downloaded lessons, flashcards, and quizzes are still available — progress will sync when you reconnect.
        </div>
      )}

      <main id="main" className="flex-1 max-w-6xl w-full mx-auto px-4 py-6">
        {children}
      </main>

      {!seriousMode && <MascotBubble />}

      <footer className="border-t border-slate-200 dark:border-slate-800 py-6 text-center text-xs text-[var(--ink-muted)] space-y-1">
        <p>
          MedTech Review Hub — an independent study platform. Not affiliated with, endorsed by, or sponsored by ASCP.
        </p>
        <p>
          <Link href="/settings/" className="underline">Disclaimers &amp; data export</Link>
          {' · '}
          <a href="https://github.com/dazoarmando29/MedTech-Review-Hub" className="underline" target="_blank" rel="noreferrer">
            Source &amp; releases
          </a>
        </p>
      </footer>
    </div>
  );
}

const LINES: Record<string, { cat: string[]; dog: string[] }> = {
  home: {
    cat: ['Meow. Anong susunod mong aaralin?', 'Your antibodies are working harder than you.'],
    dog: ['Woof! Your study streak needs attention.', 'One more quiz before scrolling?'],
  },
  library: {
    cat: ['Deep Study mode: para sa mga gustong maging RMT talaga.', 'High-yield facts first. Chika later.'],
    dog: ['Good students read the traps section. Good dogs get treats.'],
  },
  flashcards: {
    cat: ['You have flashcards waiting. Explain yourself.', 'Rated "Easy"? Sige, kita natin bukas.'],
    dog: ['Woof! Again means again. No shortcuts!'],
  },
  quiz: {
    cat: ['Read the vignette twice. Like a cat stalking a laser.', 'Wrong answers are free teachers.'],
    dog: ['Focus muna. We can celebrate later.'],
  },
  default: {
    cat: ['Kaya mo \u2019to, future RMT.', 'Blood Banking muna bago TikTok.'],
    dog: ['Good grades, good treats.', 'Laban lang, future RMT!'],
  },
};

function MascotBubble() {
  const mascot = useStore((s) => s.settings.mascot);
  const commentary = useStore((s) => s.settings.mascotCommentary);
  const pathname = usePathname();
  const [idx, setIdx] = useState(0);

  const key = pathname.startsWith('/library')
    ? 'library'
    : pathname.startsWith('/flashcards')
      ? 'flashcards'
      : pathname.startsWith('/quiz')
        ? 'quiz'
        : pathname === '/'
          ? 'home'
          : 'default';
  const lines = LINES[key][mascot === 'cat' ? 'cat' : 'dog'];

  useEffect(() => {
    setIdx(Math.floor(Math.random() * lines.length));
  }, [key, mascot, lines.length]);

  if (!commentary) return null;

  return (
    <div className="fixed bottom-4 right-4 z-40 max-w-[260px] hidden sm:block">
      <div className="card p-3 flex items-start gap-2 shadow-lift">
        <div className="text-3xl" aria-hidden>
          {mascot === 'cat' ? '🐱' : '🐶'}
        </div>
        <div className="text-xs leading-snug">
          <p>{lines[idx % lines.length]}</p>
          <button
            className="mt-1 underline text-[var(--ink-muted)]"
            onClick={() => setIdx((i) => (i + 1) % lines.length)}
          >
            another one
          </button>
        </div>
      </div>
    </div>
  );
}
