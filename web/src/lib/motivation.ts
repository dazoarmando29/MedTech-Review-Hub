// Daily motivation logic — deterministic quote rotation, context messages,
// and "Little Notes for You" scheduling. Pure functions (unit-tested).
import { motivation } from './content';
import type { AppState } from './store';
import type { LoveNote } from './types';

export type QuoteContext =
  | 'beforeStudy' | 'afterLesson' | 'afterFlashcards' | 'afterWrong'
  | 'longSession' | 'afterGoals';

/** Deterministic daily quote (same all day, new tomorrow; PH timezone aware). */
export function dailyQuote(date: Date = new Date()): string {
  const quotes = motivation.dailyQuotes.length ? motivation.dailyQuotes : ['Keep going, future MLS.'];
  const key = dayKey(date);
  const hash = [...key].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) | 0, 7);
  return quotes[Math.abs(hash) % quotes.length];
}

export function dayKey(date: Date = new Date()): string {
  try {
    return date.toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' });
  } catch {
    return date.toISOString().slice(0, 10);
  }
}

export function contextQuote(context: QuoteContext, salt = 0): string {
  const pool = motivation.contextQuotes[context] ?? ['Keep going, future MLS.'];
  const idx = (Math.floor(Date.now() / 1000) + salt) % pool.length;
  return pool[(idx + pool.length) % pool.length];
}

export function randomQuote(): string {
  const pool = motivation.dailyQuotes;
  return pool[Math.floor(Math.random() * pool.length)] ?? 'Keep going, future MLS.';
}

/** Notes that are due today or earlier (or undated), favorites first. */
export function dueLoveNotes(notes: LoveNote[], date: Date = new Date()): LoveNote[] {
  const today = dayKey(date);
  return notes
    .filter((n) => !n.showDate || n.showDate <= today)
    .sort((a, b) => Number(b.favorite) - Number(a.favorite));
}

/** One deterministic note for the dashboard (favorites rotate first). */
export function pickLoveNote(notes: LoveNote[], date: Date = new Date()): LoveNote | null {
  const due = dueLoveNotes(notes, date);
  if (!due.length) return null;
  const idx = [...dayKey(date)].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) | 0, 7);
  return due[Math.abs(idx) % due.length];
}

/** Word-wrap a quote for canvas card rendering. */
export function wrapText(text: string, maxCharsPerLine: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = '';
  for (const w of words) {
    if ((line + ' ' + w).trim().length > maxCharsPerLine) {
      if (line) lines.push(line.trim());
      line = w;
    } else {
      line = (line + ' ' + w).trim();
    }
  }
  if (line) lines.push(line);
  return lines;
}

/**
 * Renders a shareable pastel quote card (PNG) to a canvas.
 * Returns the canvas so callers can export toBlob / toDataURL.
 */
export function drawQuoteCard(canvas: HTMLCanvasElement, quote: string): void {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  const size = 1080;
  canvas.width = size;
  canvas.height = size;

  // soft gradient background
  const grad = ctx.createLinearGradient(0, 0, size, size);
  grad.addColorStop(0, '#FFF0F5');
  grad.addColorStop(0.55, '#FDE7F1');
  grad.addColorStop(1, '#F3E3FF');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);

  // decorative rings + stars
  ctx.globalAlpha = 0.5;
  for (const [x, y, r, color] of [
    [140, 160, 120, '#F8BBD0'], [920, 220, 150, '#E9D5FF'], [220, 880, 170, '#F8BBD0'], [900, 900, 130, '#E9D5FF'],
  ] as [number, number, number, string][]) {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.globalAlpha = 1;
  const star = (x: number, y: number, s: number, color: string) => {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.moveTo(x, y - s);
    ctx.quadraticCurveTo(x, y - s * 0.2, x + s, y);
    ctx.quadraticCurveTo(x, y + s * 0.2, x, y + s);
    ctx.quadraticCurveTo(x, y + s * 0.2, x - s, y);
    ctx.quadraticCurveTo(x, y - s * 0.2, x, y - s);
    ctx.fill();
  };
  star(180, 320, 26, '#F48FB1');
  star(860, 380, 34, '#B39DDB');
  star(260, 760, 20, '#B39DDB');
  star(820, 760, 24, '#F48FB1');

  // quote text
  const lines = wrapText(quote, 26);
  ctx.fillStyle = '#352735';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const fontSize = lines.length > 5 ? 52 : 62;
  ctx.font = `700 ${fontSize}px Quicksand, Nunito, sans-serif`;
  const lineHeight = fontSize * 1.45;
  const startY = size / 2 - ((lines.length - 1) * lineHeight) / 2;
  lines.forEach((line, i) => {
    ctx.fillText(line, size / 2, startY + i * lineHeight);
  });

  // footer
  ctx.font = '600 30px Quicksand, Nunito, sans-serif';
  ctx.fillStyle = '#BE7896';
  ctx.fillText('MedTech Study Companion  🌸', size / 2, size - 90);
}

export function encouragementForProgress(state: AppState): string | null {
  // gentle hint when the daily plan is fully checked off
  const plan = state.plan?.days.find((d) => d.date === todayKeyLocal());
  if (plan && plan.items.length > 0 && plan.items.every((i) => i.completed)) {
    return contextQuote('afterGoals');
  }
  return null;
}

function todayKeyLocal(): string {
  return dayKey();
}
