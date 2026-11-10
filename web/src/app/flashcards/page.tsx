'use client';

// Anki-style smart flashcards with a real FSRS-4.5 scheduler (shared/FSRS.md).
import React, { useMemo, useState } from 'react';
import { Badge, Card, ConfirmButton, EmptyState, SectionTitle, Tabs, cx } from '@/components/ui';
import { decks as seedDecks, subjects } from '@/lib/content';
import { formatInterval, isDue, newSchedule, previewIntervals } from '@/lib/fsrs';
import { contextQuote } from '@/lib/motivation';
import {
  addCardFromSeed, deleteCard, dueCardList, newCardList, reviewCard, toggleFavorite,
  updateSettings, upsertCard, useStore,
} from '@/lib/store';
import type { Rating, UserCard } from '@/lib/types';

type Queue = 'due' | 'new' | 'browse';

export default function FlashcardsPage() {
  const [queue, setQueue] = useState<Queue>('due');
  const [deckFilter, setDeckFilter] = useState<string>('all');
  const [flipped, setFlipped] = useState(false);
  const [sessionCount, setSessionCount] = useState(0);
  const [creating, setCreating] = useState(false);

  const settings = useStore((s) => s.settings);
  const allCards = useStore((s) => s.cards);
  const due = useStore((s) => dueCardList(s));
  const fresh = useStore((s) => newCardList(s));
  const [kudos, setKudos] = useState<string | null>(null);

  const filteredDue = useMemo(
    () => (deckFilter === 'all' ? due : due.filter((c) => c.subjectId === deckFilter)),
    [due, deckFilter],
  );
  const filteredNew = useMemo(() => {
    const limited = fresh.slice(0, settings.dailyNewCards);
    return deckFilter === 'all' ? limited : limited.filter((c) => c.subjectId === deckFilter);
  }, [fresh, deckFilter, settings.dailyNewCards]);

  const browseCards = useMemo(() => {
    const all = Object.values(allCards);
    return deckFilter === 'all' ? all : all.filter((c) => c.subjectId === deckFilter);
  }, [allCards, deckFilter]);

  const activeCard: UserCard | undefined =
    queue === 'due' ? filteredDue[0] : queue === 'new' ? filteredNew[0] : undefined;

  const previews = activeCard ? previewIntervals(activeCard.schedule) : null;

  const onRate = (rating: Rating) => {
    if (!activeCard) return;
    reviewCard(activeCard.id, rating);
    setFlipped(false);
    setSessionCount((n) => {
      const next = n + 1;
      if (next === 5 || next === 10 || next === 20) setKudos(contextQuote('afterFlashcards', next));
      return next;
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1 min-w-[220px]">
          <h1 className="font-heading text-2xl font-bold">Smart Flashcards</h1>
          <p className="text-sm text-[var(--ink-muted)]">
            Spaced repetition powered by FSRS-4.5 — ratings ({'Again / Hard / Good / Easy'}) really
            reschedule every card from predicted recall. Next review dates are shown on each button.
          </p>
        </div>
        <div className="flex gap-2">
          <button className="btn-ghost text-sm" onClick={() => setCreating(true)}>+ New card</button>
          <ExportImport />
        </div>
      </div>

      <div className="flex flex-wrap gap-2 items-center">
        <Tabs
          tabs={[
            { id: 'due', label: `Due (${filteredDue.length})` },
            { id: 'new', label: `New (${filteredNew.length})` },
            { id: 'browse', label: `Browse (${browseCards.length})` },
          ]}
          active={queue}
          onChange={(id) => {
            setQueue(id as Queue);
            setFlipped(false);
          }}
        />
        <select className="input !w-auto" value={deckFilter} onChange={(e) => setDeckFilter(e.target.value)} aria-label="Filter by subject">
          <option value="all">All subjects</option>
          {subjects.map((s) => (
            <option key={s.id} value={s.id}>{s.shortName}</option>
          ))}
        </select>
        {sessionCount > 0 && <Badge tone="success">{sessionCount} reviewed this session</Badge>}
      </div>

      {kudos && (
        <div className="sticky-note confetti-pop flex items-center justify-between gap-3">
          <p className="text-sm font-semibold">🌸 {kudos}</p>
          <button className="chip" onClick={() => setKudos(null)}>dismiss</button>
        </div>
      )}

      {(queue === 'due' || queue === 'new') && activeCard && (
        <div className="max-w-2xl mx-auto">
          <div
            className="card p-8 min-h-[260px] flex flex-col cursor-pointer select-none"
            onClick={() => setFlipped((f) => !f)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && setFlipped((f) => !f)}
            aria-label="Flip card"
          >
            <div className="flex items-center justify-between text-xs text-[var(--ink-muted)]">
              <span>{activeCard.deckId}</span>
              <span>
                {activeCard.schedule.state.toLowerCase()} · stability {activeCard.schedule.stability.toFixed(1)}d ·
                difficulty {activeCard.schedule.difficulty.toFixed(1)}/10
              </span>
            </div>
            <div className="flex-1 flex items-center justify-center text-center py-6">
              {!flipped ? (
                <div>
                  <p className="text-xs uppercase tracking-wide mb-2" style={{ color: 'var(--muted)' }}>Question</p>
                  <p className="text-lg font-semibold">{renderCloze(activeCard.front, false)}</p>
                </div>
              ) : (
                <div>
                  <p className="text-xs uppercase tracking-wide mb-2" style={{ color: 'var(--muted)' }}>Answer</p>
                  <p className="text-lg">
                    {activeCard.type === 'CLOZE'
                      ? renderCloze(activeCard.front, true)
                      : activeCard.front}
                  </p>
                  <p className="mt-3 font-medium" style={{ color: 'var(--primary)' }}>{activeCard.back}</p>
                  {activeCard.note && (
                    <div className="sticky-note mt-4 text-xs inline-block text-left max-w-sm">
                      <strong>Cute memory trick 🐾</strong>
                      <p className="mt-1">{activeCard.note}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
            <div className="flex items-center justify-between">
              <button
                className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs"
                onClick={(e) => {
                  e.stopPropagation();
                  toggleFavorite(activeCard.id);
                }}
              >
                {activeCard.favorite ? '★ favorite' : '☆ favorite'}
              </button>
              <span className="text-xs text-[var(--ink-muted)]">{flipped ? 'tap to hide' : 'tap to reveal'}</span>
            </div>
          </div>

          {flipped && previews && (
            <div className="grid grid-cols-4 gap-2 mt-4">
              {(['AGAIN', 'HARD', 'GOOD', 'EASY'] as Rating[]).map((r) => (
                <button
                  key={r}
                  className={cx(
                    'btn flex-col !py-3',
                    r === 'AGAIN' && 'bg-red-600 text-white hover:opacity-90',
                    r === 'HARD' && 'bg-amber-500 text-white hover:opacity-90',
                    r === 'GOOD' && 'bg-emerald-600 text-white hover:opacity-90',
                    r === 'EASY' && 'bg-blue-600 text-white hover:opacity-90',
                  )}
                  onClick={() => onRate(r)}
                >
                  <span className="font-bold">{r.charAt(0) + r.slice(1).toLowerCase()}</span>
                  <span className="text-xs opacity-90">{previews[r]}</span>
                </button>
              ))}
            </div>
          )}

          {flipped && (
            <p className="text-center text-xs text-[var(--ink-muted)] mt-2">
              Estimated next review shown per rating · target retention {Math.round(settings.targetRetention * 100)}%
            </p>
          )}
        </div>
      )}

      {(queue === 'due' && filteredDue.length === 0) && (
        <EmptyState
          icon="🎉"
          title="Queue clear! No cards due right now"
          hint="Come back later — FSRS schedules reviews exactly when you are likely to forget. You can also study New cards or create your own."
          action={<button className="btn-primary" onClick={() => setQueue('new')}>Study new cards</button>}
        />
      )}
      {(queue === 'new' && filteredNew.length === 0) && (
        <EmptyState icon="🌙" title="New card limit reached" hint={`You are limited to ${settings.dailyNewCards} new cards per day (change in Settings).`} />
      )}

      {queue === 'browse' && (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {browseCards.map((c) => (
            <Card key={c.id} className="text-sm">
              <div className="flex items-start justify-between gap-2">
                <p className="font-semibold line-clamp-3">{c.front}</p>
                {c.favorite && <span aria-label="favorite">★</span>}
              </div>
              <p className="text-[var(--ink-muted)] mt-2 line-clamp-2">{c.back}</p>
              <div className="flex flex-wrap gap-1 mt-2">
                {c.tags.slice(0, 3).map((t) => <span key={t} className="chip">{t}</span>)}
              </div>
              <div className="text-xs text-[var(--ink-muted)] mt-2">
                due {new Date(c.schedule.dueAt).toLocaleDateString()} · {c.schedule.state.toLowerCase()}
              </div>
              <div className="flex gap-2 mt-2">
                <ConfirmButton
                  label="Delete"
                  confirmLabel="Confirm delete?"
                  className="btn-ghost !py-1 !px-2 !min-h-[32px] text-xs text-red-600"
                  onConfirm={() => deleteCard(c.id)}
                />
              </div>
            </Card>
          ))}
        </div>
      )}

      <Card>
        <SectionTitle icon="📦">Seed decks</SectionTitle>
        <p className="text-sm text-[var(--ink-muted)]">
          Import a full premade deck into your study queue (scheduling starts when you review):
        </p>
        <div className="flex flex-wrap gap-2 mt-3">
          {seedDecks.map((d) => (
            <button
              key={d.id}
              className="btn-ghost text-xs"
              onClick={() => {
                let n = 0;
                for (const seed of d.cards) {
                  if (!allCards[seed.id]) {
                    addCardFromSeed(seed, d.id, d.subjectId);
                    n++;
                  }
                }
                alert(n ? `Imported ${n} cards from ${d.title}` : 'All cards from this deck are already in your collection.');
              }}
            >
              {d.title} ({d.cards.length})
            </button>
          ))}
        </div>
      </Card>

      {creating && <CreateCardDialog onClose={() => setCreating(false)} />}

      <Card>
        <SectionTitle icon="⚙️">Scheduler settings</SectionTitle>
        <div className="grid sm:grid-cols-3 gap-4 text-sm">
          <label>
            New cards / day: <strong>{settings.dailyNewCards}</strong>
            <input type="range" min={5} max={50} step={5} value={settings.dailyNewCards}
              className="w-full" onChange={(e) => updateSettings({ dailyNewCards: Number(e.target.value) })} />
          </label>
          <label>
            Review limit / day: <strong>{settings.dailyReviewLimit}</strong>
            <input type="range" min={50} max={500} step={50} value={settings.dailyReviewLimit}
              className="w-full" onChange={(e) => updateSettings({ dailyReviewLimit: Number(e.target.value) })} />
          </label>
          <label>
            Target retention: <strong>{Math.round(settings.targetRetention * 100)}%</strong>
            <input type="range" min={0.8} max={0.95} step={0.01} value={settings.targetRetention}
              className="w-full" onChange={(e) => updateSettings({ targetRetention: Number(e.target.value) })} />
          </label>
        </div>
        <p className="text-xs text-[var(--ink-muted)] mt-2">
          Algorithm: FSRS-4.5 (documented in shared/FSRS.md, identical implementation on Android).
        </p>
      </Card>
    </div>
  );
}

function renderCloze(text: string, revealed: boolean): React.ReactNode {
  const parts = text.split(/(\[\[\d+\]\])/g);
  return parts.map((p, i) => {
    const m = p.match(/^\[\[(\d+)\]\]$/);
    if (!m) return <React.Fragment key={i}>{p}</React.Fragment>;
    return revealed ? (
      <span key={i} className="hl-GREEN rounded px-1 font-semibold">[{m[1]}]</span>
    ) : (
      <span key={i} className="bg-slate-300 dark:bg-slate-600 text-transparent rounded px-2">cloze</span>
    );
  });
}

function CreateCardDialog({ onClose }: { onClose: () => void }) {
  const [front, setFront] = useState('');
  const [back, setBack] = useState('');
  const [tags, setTags] = useState('');
  const [type, setType] = useState<'BASIC' | 'CLOZE'>('BASIC');
  const [subjectId, setSubjectId] = useState(subjects[0].id);

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <Card className="w-full max-w-lg">
        <SectionTitle icon="✏️">Create flashcard</SectionTitle>
        <div className="space-y-3">
          <label className="block text-sm">
            Type
            <select className="input mt-1" value={type} onChange={(e) => setType(e.target.value as 'BASIC' | 'CLOZE')}>
              <option value="BASIC">Basic (front / back)</option>
              <option value="CLOZE">Cloze (use [[1]] markers)</option>
            </select>
          </label>
          <label className="block text-sm">
            {type === 'CLOZE' ? 'Text with [[1]] blanks' : 'Front (question)'}
            <textarea className="input mt-1" rows={3} value={front} onChange={(e) => setFront(e.target.value)} />
          </label>
          <label className="block text-sm">
            {type === 'CLOZE' ? 'Answers (1: …, 2: …)' : 'Back (answer)'}
            <textarea className="input mt-1" rows={2} value={back} onChange={(e) => setBack(e.target.value)} />
          </label>
          <label className="block text-sm">
            Subject
            <select className="input mt-1" value={subjectId} onChange={(e) => setSubjectId(e.target.value)}>
              {subjects.map((s) => <option key={s.id} value={s.id}>{s.shortName}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            Tags (comma-separated)
            <input className="input mt-1" value={tags} onChange={(e) => setTags(e.target.value)} />
          </label>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button
            className="btn-primary"
            disabled={!front.trim() || !back.trim()}
            onClick={() => {
              upsertCard({
                id: `fc-user-${Date.now()}`,
                deckId: 'user-created',
                front: front.trim(),
                back: back.trim(),
                type,
                tags: tags.split(',').map((t) => t.trim()).filter(Boolean),
                subjectId,
                topicId: null,
                difficulty: 2,
                note: '',
                favorite: false,
                imageRef: null,
                source: { kind: 'MANUAL' },
                schedule: newSchedule(),
                history: [],
              });
              onClose();
            }}
          >
            Add card
          </button>
        </div>
      </Card>
    </div>
  );
}

function ExportImport() {
  const cards = useStore((s) => s.cards);
  return (
    <button
      className="btn-ghost text-sm"
      onClick={() => {
        const header = 'id,front,back,type,tags,subjectId,dueAt,state,stability,difficulty\n';
        const rows = Object.values(cards)
          .map((c) =>
            [c.id, c.front, c.back, c.type, c.tags.join(' '), c.subjectId, c.schedule.dueAt, c.schedule.state, c.schedule.stability.toFixed(2), c.schedule.difficulty.toFixed(2)]
              .map((v) => `"${String(v).replace(/"/g, '""')}"`)
              .join(','),
          )
          .join('\n');
        const blob = new Blob([header + rows], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'medtech-flashcards.csv';
        a.click();
        URL.revokeObjectURL(url);
      }}
    >
      Export CSV
    </button>
  );
}
