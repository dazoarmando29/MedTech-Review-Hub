import { describe, expect, it } from 'vitest';
import {
  contextQuote, dailyQuote, dayKey, dueLoveNotes, pickLoveNote, randomQuote, wrapText,
} from '../motivation';
import { motivation } from '../content';
import type { LoveNote } from '../types';

describe('daily quote rotation', () => {
  it('is deterministic per day and drawn from the bank', () => {
    const d = new Date('2026-06-15T10:00:00Z');
    expect(dailyQuote(d)).toBe(dailyQuote(d));
    expect(motivation.dailyQuotes).toContain(dailyQuote(d));
  });

  it('can change between days', () => {
    const a = dailyQuote(new Date('2026-06-15T10:00:00Z'));
    const b = dailyQuote(new Date('2026-06-16T10:00:00Z'));
    expect(typeof a).toBe('string');
    expect(typeof b).toBe('string');
    expect(a.length).toBeGreaterThan(5);
    expect(b.length).toBeGreaterThan(5);
  });

  it('uses PH timezone for the day key', () => {
    const key = dayKey(new Date('2026-06-15T20:00:00Z'));
    expect(key).toBe('2026-06-16'); // 20:00 UTC = 04:00 next day in Manila
  });
});

describe('context quotes', () => {
  it('covers every encouragement context', () => {
    for (const ctx of ['beforeStudy', 'afterLesson', 'afterFlashcards', 'afterWrong', 'longSession', 'afterGoals'] as const) {
      const q = contextQuote(ctx);
      expect(q.length).toBeGreaterThan(5);
    }
    expect(randomQuote().length).toBeGreaterThan(5);
  });
});

describe('Little Notes for You', () => {
  const notes: LoveNote[] = [
    { id: 'n1', text: 'I believe in you.', showDate: '2026-06-01', favorite: true, createdAt: '2026-05-01' },
    { id: 'n2', text: 'Keep going!', showDate: '2030-01-01', favorite: false, createdAt: '2026-05-02' },
    { id: 'n3', text: 'So proud of you.', showDate: null, favorite: false, createdAt: '2026-05-03' },
  ];

  it('only shows notes that are due (dated notes wait for their day)', () => {
    const due = dueLoveNotes(notes, new Date('2026-06-15T10:00:00Z'));
    expect(due.map((n) => n.id).sort()).toEqual(['n1', 'n3']);
  });

  it('sorts favorites first', () => {
    const due = dueLoveNotes(notes, new Date('2026-06-15T10:00:00Z'));
    expect(due[0].favorite).toBe(true);
  });

  it('picks a deterministic note for the dashboard', () => {
    const d = new Date('2026-06-15T10:00:00Z');
    expect(pickLoveNote(notes, d)).toEqual(pickLoveNote(notes, d));
    expect(pickLoveNote([], d)).toBeNull();
    expect(pickLoveNote([notes[1]], d)).toBeNull(); // not due yet
  });
});

describe('quote card text wrapping', () => {
  it('wraps long quotes into readable lines', () => {
    const lines = wrapText('Every page you study brings you closer to your dream.', 20);
    expect(lines.length).toBeGreaterThan(1);
    for (const l of lines) expect(l.length).toBeLessThanOrEqual(20);
  });

  it('keeps short quotes on one line', () => {
    expect(wrapText('Keep going.', 26)).toEqual(['Keep going.']);
  });
});
