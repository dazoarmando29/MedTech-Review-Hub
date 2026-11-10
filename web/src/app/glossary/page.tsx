'use client';

// Personal glossary — save terms, acronyms, definitions; searchable; exportable.
import React, { useMemo, useState } from 'react';
import { Badge, Card, ConfirmButton, EmptyState, SectionTitle, cx } from '@/components/ui';
import { subjects } from '@/lib/content';
import { addGlossaryTerm, removeGlossaryTerm, useStore } from '@/lib/store';

export default function GlossaryPage() {
  const glossary = useStore((s) => s.glossary);
  const [q, setQ] = useState('');
  const [subjectFilter, setSubjectFilter] = useState('all');
  const [draft, setDraft] = useState({ term: '', acronym: '', definition: '', subjectId: subjects[0].id, example: '' });

  const filtered = useMemo(
    () =>
      glossary.filter((g) => {
        const matchesQ = !q || g.term.toLowerCase().includes(q.toLowerCase()) || g.definition.toLowerCase().includes(q.toLowerCase());
        const matchesS = subjectFilter === 'all' || g.subjectId === subjectFilter;
        return matchesQ && matchesS;
      }),
    [glossary, q, subjectFilter],
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Personal Glossary</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          {glossary.length} terms · seed terms come from the review library; your additions are marked “yours”.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        <input className="input !w-64" placeholder="Filter terms…" value={q} onChange={(e) => setQ(e.target.value)} />
        <select className="input !w-56" value={subjectFilter} onChange={(e) => setSubjectFilter(e.target.value)}>
          <option value="all">All subjects</option>
          {subjects.map((s) => <option key={s.id} value={s.id}>{s.shortName}</option>)}
        </select>
        <button
          className="btn-ghost text-xs"
          onClick={() => {
            const blob = new Blob([JSON.stringify(glossary, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'my-glossary.json';
            a.click();
            URL.revokeObjectURL(url);
          }}
        >
          Export JSON
        </button>
      </div>

      <Card>
        <SectionTitle icon="➕">Add a term</SectionTitle>
        <div className="grid sm:grid-cols-2 gap-2">
          <input className="input" placeholder="Term (e.g. Isohemagglutinin)" value={draft.term} onChange={(e) => setDraft({ ...draft, term: e.target.value })} />
          <input className="input" placeholder="Acronym (optional)" value={draft.acronym} onChange={(e) => setDraft({ ...draft, acronym: e.target.value })} />
          <textarea className="input sm:col-span-2" rows={2} placeholder="Definition" value={draft.definition} onChange={(e) => setDraft({ ...draft, definition: e.target.value })} />
          <input className="input" placeholder="Example (optional)" value={draft.example} onChange={(e) => setDraft({ ...draft, example: e.target.value })} />
          <select className="input" value={draft.subjectId} onChange={(e) => setDraft({ ...draft, subjectId: e.target.value })}>
            {subjects.map((s) => <option key={s.id} value={s.id}>{s.shortName}</option>)}
          </select>
        </div>
        <button
          className="btn-primary mt-3"
          disabled={!draft.term.trim() || !draft.definition.trim()}
          onClick={() => {
            addGlossaryTerm({
              id: `glo-user-${Date.now()}`,
              term: draft.term.trim(),
              acronym: draft.acronym.trim() || undefined,
              definition: draft.definition.trim(),
              subjectId: draft.subjectId,
              example: draft.example.trim() || undefined,
              source: 'personal note',
            });
            setDraft({ term: '', acronym: '', definition: '', subjectId: subjects[0].id, example: '' });
          }}
        >
          Save term
        </button>
      </Card>

      {filtered.length === 0 ? (
        <EmptyState icon="📕" title="No matching terms" />
      ) : (
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {filtered.map((g) => (
            <Card key={g.id} className="text-sm">
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-heading font-bold">
                  {g.term} {g.acronym && <span className="text-[var(--ink-muted)] font-normal">({g.acronym})</span>}
                </h3>
                {g.custom ? <Badge tone="warn">yours</Badge> : <Badge>library</Badge>}
              </div>
              <p className="mt-1 text-[var(--ink-muted)]">{g.definition}</p>
              {g.example && <p className="mt-1 text-xs italic">e.g. {g.example}</p>}
              <div className="flex items-center justify-between mt-2">
                <span className="chip">{g.subjectId}</span>
                {g.custom && (
                  <ConfirmButton label="Delete" confirmLabel="Confirm?" className="text-xs underline text-red-600" onConfirm={() => removeGlossaryTerm(g.id)} />
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
