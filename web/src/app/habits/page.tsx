'use client';

// Habit tracker + study diary + streaks + weekly summaries.
import React, { useMemo, useState } from 'react';
import { Badge, Card, ConfirmButton, EmptyState, SectionTitle, cx } from '@/components/ui';
import {
  addDiary, addHabit, logHabit, minutesOn, removeDiary, removeHabit, streakDays,
  todayKey, useStore,
} from '@/lib/store';
import type { DiaryEntry, Habit } from '@/lib/types';

const MOODS: { id: DiaryEntry['mood']; label: string; emoji: string }[] = [
  { id: 'great', label: 'Great', emoji: '😄' },
  { id: 'good', label: 'Good', emoji: '🙂' },
  { id: 'okay', label: 'Okay', emoji: '😐' },
  { id: 'tired', label: 'Tired', emoji: '😪' },
  { id: 'struggling', label: 'Struggling', emoji: '😣' },
];

const STICKERS = ['💗', '⭐', '🧪', '🐱', '🎓', '🌸', '📚', '💪'];

export default function HabitsPage() {
  const habits = useStore((s) => s.habits);
  const habitLogs = useStore((s) => s.habitLogs);
  const diary = useStore((s) => s.diary);
  const streak = useStore((s) => streakDays(s));
  const [newHabit, setNewHabit] = useState({ name: '', targetPerDay: 20, kind: 'COUNT' as Habit['kind'] });
  const [entry, setEntry] = useState<{ mood: DiaryEntry['mood']; text: string; tags: string; stickers: string[] }>({ mood: 'good', text: '', tags: '', stickers: [] });

  const today = todayKey();
  const week = useMemo(() => {
    const out: { date: string; minutes: number }[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      const key = todayKey(d);
      out.push({ date: key, minutes: minutesOn(key) });
    }
    return out;
  }, [habitLogs, diary]);

  const weekMinutes = week.reduce((s, d) => s + d.minutes, 0);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Habits &amp; Study Diary</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          Small consistent steps beat cramming. Missing a day is a rest day, not a failure —
          your history is never deleted or shamed.
        </p>
      </div>

      <section className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="card p-4">
          <div className="text-xs text-[var(--ink-muted)] uppercase">Streak</div>
          <div className="font-heading text-2xl font-bold streak-flame">{streak} days 🔥</div>
        </div>
        <div className="card p-4">
          <div className="text-xs text-[var(--ink-muted)] uppercase">This week</div>
          <div className="font-heading text-2xl font-bold">{weekMinutes} min</div>
        </div>
        <div className="card p-4">
          <div className="text-xs text-[var(--ink-muted)] uppercase">Diary entries</div>
          <div className="font-heading text-2xl font-bold">{diary.length}</div>
        </div>
        <div className="card p-4">
          <div className="text-xs text-[var(--ink-muted)] uppercase">Habits active</div>
          <div className="font-heading text-2xl font-bold">{habits.filter((h) => !h.archived).length}</div>
        </div>
      </section>

      <Card>
        <SectionTitle icon="📅" action={<Badge tone="info">today</Badge>}>Daily checklist</SectionTitle>
        {habits.length === 0 ? (
          <p className="text-sm text-[var(--ink-muted)]">
            No habits yet. Try: “20 flashcards”, “30 questions”, “1 chapter”, “Review my wrong answers”.
          </p>
        ) : (
          <ul className="space-y-2">
            {habits.map((h) => {
              const key = `${h.id}|${today}`;
              const amount = habitLogs[key] ?? 0;
              const done = amount >= h.targetPerDay;
              return (
                <li key={h.id} className="flex items-center gap-3 rounded-md border border-slate-200 dark:border-slate-700 p-3">
                  <button
                    className={cx('h-7 w-7 rounded-full border-2 flex items-center justify-center', done ? 'bg-emerald-500 border-emerald-500 text-white' : 'border-slate-300')}
                    aria-label={`Log one unit of ${h.name}`}
                    onClick={() => logHabit(h.id, today, Math.min(h.targetPerDay, amount + (h.kind === 'MINUTES' ? 15 : 1)))}
                  >
                    {done ? '✓' : ''}
                  </button>
                  <div className="flex-1">
                    <div className="text-sm font-semibold">{h.name}</div>
                    <div className="text-xs text-[var(--ink-muted)]">{amount}/{h.targetPerDay} {h.kind === 'MINUTES' ? 'min' : 'times'} today</div>
                  </div>
                  <ConfirmButton label="Remove" confirmLabel="Confirm?" onConfirm={() => removeHabit(h.id)} className="btn-ghost text-xs" />
                </li>
              );
            })}
          </ul>
        )}

        <div className="flex flex-wrap gap-2 mt-4">
          <input className="input !w-52" placeholder="New habit name" value={newHabit.name}
            onChange={(e) => setNewHabit({ ...newHabit, name: e.target.value })} />
          <input className="input !w-28" type="number" min={1} value={newHabit.targetPerDay}
            onChange={(e) => setNewHabit({ ...newHabit, targetPerDay: Number(e.target.value) })} />
          <select className="input !w-32" value={newHabit.kind}
            onChange={(e) => setNewHabit({ ...newHabit, kind: e.target.value as Habit['kind'] })}>
            <option value="COUNT">times</option>
            <option value="MINUTES">minutes</option>
          </select>
          <button
            className="btn-primary"
            disabled={!newHabit.name.trim()}
            onClick={() => {
              addHabit({
                id: `hb-${Date.now()}`,
                name: newHabit.name.trim(),
                kind: newHabit.kind,
                targetPerDay: newHabit.targetPerDay,
                createdAt: new Date().toISOString(),
                archived: false,
              });
              setNewHabit({ name: '', targetPerDay: 20, kind: 'COUNT' });
            }}
          >
            Add habit
          </button>
        </div>
      </Card>

      <Card>
        <SectionTitle icon="📊">Weekly calendar</SectionTitle>
        <div className="grid grid-cols-7 gap-2">
          {week.map((d) => {
            const intensity = Math.min(4, Math.floor(d.minutes / 30));
            return (
              <div key={d.date} className="text-center">
                <div
                  className={cx(
                    'rounded-md h-16 flex items-end justify-center pb-1 text-[10px] font-semibold',
                    intensity === 0 && 'bg-slate-100 dark:bg-slate-800 text-[var(--ink-muted)]',
                    intensity === 1 && 'bg-emerald-100 dark:bg-emerald-900/50',
                    intensity === 2 && 'bg-emerald-300 dark:bg-emerald-800',
                    intensity === 3 && 'bg-emerald-500 text-white',
                    intensity >= 4 && 'bg-emerald-700 text-white',
                  )}
                  title={`${d.date}: ${d.minutes} minutes`}
                >
                  {d.minutes}m
                </div>
                <div className="text-[10px] text-[var(--ink-muted)] mt-1">{d.date.slice(5)}</div>
              </div>
            );
          })}
        </div>
      </Card>

      <div className="grid lg:grid-cols-2 gap-4">
        <Card>
          <SectionTitle icon="✍️">Study diary (private)</SectionTitle>
          <div className="flex gap-1 flex-wrap mb-3">
            {MOODS.map((m) => (
              <button
                key={m.id}
                className={cx('btn text-xs', entry.mood === m.id ? 'btn-primary' : 'btn-ghost')}
                onClick={() => setEntry({ ...entry, mood: m.id })}
                aria-pressed={entry.mood === m.id}
              >
                {m.emoji} {m.label}
              </button>
            ))}
          </div>
          <div className="flex gap-1 flex-wrap mb-3">
            <span className="text-xs self-center mr-1" style={{ color: 'var(--muted)' }}>stickers:</span>
            {STICKERS.map((s) => {
              const on = entry.stickers.includes(s);
              return (
                <button
                  key={s}
                  className={cx('text-xl rounded-full w-9 h-9', on ? '' : 'opacity-45')}
                  style={on ? { background: 'var(--chip)' } : undefined}
                  aria-pressed={on}
                  aria-label={`Sticker ${s}`}
                  onClick={() =>
                    setEntry({
                      ...entry,
                      stickers: on
                        ? entry.stickers.filter((x) => x !== s)
                        : entry.stickers.length < 3 ? [...entry.stickers, s] : entry.stickers,
                    })
                  }
                >
                  {s}
                </button>
              );
            })}
          </div>
          <textarea
            className="input"
            rows={4}
            placeholder="Today I reviewed Gram-positive cocci. I still confuse the differentiation tests, so I need to repeat them tomorrow."
            value={entry.text}
            onChange={(e) => setEntry({ ...entry, text: e.target.value })}
          />
          <div className="flex gap-2 mt-2">
            <input className="input" placeholder="tags (comma-separated)" value={entry.tags}
              onChange={(e) => setEntry({ ...entry, tags: e.target.value })} />
            <button
              className="btn-primary"
              disabled={!entry.text.trim()}
              onClick={() => {
                addDiary({
                  id: `dy-${Date.now()}`,
                  date: today,
                  mood: entry.mood,
                  text: entry.text.trim(),
                  tags: entry.tags.split(',').map((t) => t.trim()).filter(Boolean),
                  stickers: entry.stickers,
                  createdAt: new Date().toISOString(),
                });
                setEntry({ mood: 'good', text: '', tags: '', stickers: [] });
              }}
            >
              Save entry
            </button>
          </div>
          <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
            Diary entries are private by default and stored on this device (synced only if you sign in).
          </p>
        </Card>

        <Card>
          <SectionTitle icon="📖">Recent entries</SectionTitle>
          {diary.length === 0 ? (
            <EmptyState icon="🫧" title="No entries yet" hint="Your reflections help the planner spot recurring difficulties." />
          ) : (
            <ul className="space-y-3">
              {diary.slice(0, 6).map((d) => (
                <li key={d.id} className="rounded-md border border-slate-200 dark:border-slate-700 p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-[var(--ink-muted)]">{d.date} · {MOODS.find((m) => m.id === d.mood)?.emoji} {d.mood}</span>
                    <ConfirmButton label="Delete" confirmLabel="Confirm?" className="text-xs underline" onConfirm={() => removeDiary(d.id)} />
                  </div>
                  <p className="text-sm mt-1">{d.text}</p>
                  {d.stickers && d.stickers.length > 0 && (
                    <div className="text-lg mt-1" aria-label="stickers">{d.stickers.join(' ')}</div>
                  )}
                  {d.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {d.tags.map((t) => <span key={t} className="chip">{t}</span>)}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
