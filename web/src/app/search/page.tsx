'use client';

// Global search across lessons, flashcards, questions, glossary, formulas, highlights.
import Link from 'next/link';
import React, { useMemo, useState } from 'react';
import { EmptyState } from '@/components/ui';
import { globalSearch, type SearchHit } from '@/lib/search';
import { useStore } from '@/lib/store';

const KIND_LABEL: Record<SearchHit['kind'], string> = {
  subject: 'Subject',
  lesson: 'Lesson',
  flashcard: 'Flashcard',
  question: 'Question',
  glossary: 'Glossary',
  formula: 'Formula',
  highlight: 'Your highlight',
  note: 'Your note',
};

export default function SearchPage() {
  const [q, setQ] = useState('');
  const [kindFilter, setKindFilter] = useState<'all' | SearchHit['kind']>('all');
  const state = useStore((s) => s);

  const hits = useMemo(() => globalSearch(q, state), [q, state]);
  const filtered = kindFilter === 'all' ? hits : hits.filter((h) => h.kind === kindFilter);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="font-heading text-2xl font-bold">Search everything</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          Lessons, flashcards, questions, your highlights, glossary terms, and formulas.
        </p>
      </div>

      <input
        className="input text-lg"
        placeholder="Try “coagulase”, “Westgard”, “anion gap”…"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        autoFocus
        aria-label="Search query"
      />

      <div className="flex flex-wrap gap-2">
        {(['all', 'lesson', 'flashcard', 'question', 'glossary', 'formula', 'highlight'] as const).map((k) => (
          <button
            key={k}
            className={kindFilter === k ? 'btn-primary !py-1.5 text-xs' : 'btn-ghost !py-1.5 text-xs'}
            onClick={() => setKindFilter(k)}
          >
            {k === 'all' ? 'All' : KIND_LABEL[k]}
          </button>
        ))}
      </div>

      {q.length < 2 ? (
        <EmptyState icon="🔍" title="Type at least 2 characters" hint="Search is local and instant — it also covers your own notes and highlights." />
      ) : filtered.length === 0 ? (
        <EmptyState icon="🫧" title={`No results for “${q}”`} hint="Try a shorter keyword, or check the glossary." />
      ) : (
        <ul className="space-y-2">
          {filtered.map((h) => (
            <li key={`${h.kind}-${h.id}`}>
              <Link href={h.href as string}>
                <div className="card p-4 hover:shadow-lift transition-shadow">
                  <div className="flex items-center gap-2">
                    <span className="chip">{KIND_LABEL[h.kind]}</span>
                    <span className="font-semibold text-sm">{h.title}</span>
                  </div>
                  <p className="text-xs text-[var(--ink-muted)] mt-1">{h.snippet}</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
