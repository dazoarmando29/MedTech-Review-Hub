'use client';

// Searchable formula sheet — validated laboratory formulas with worked examples.
import React, { useMemo, useState } from 'react';
import { Card, EmptyState, SectionTitle } from '@/components/ui';
import { formulas } from '@/lib/content';

export default function FormulasPage() {
  const [q, setQ] = useState('');

  const filtered = useMemo(() => {
    if (!q.trim()) return formulas;
    const s = q.toLowerCase();
    return formulas.filter(
      (f) =>
        f.title.toLowerCase().includes(s) ||
        f.formula.toLowerCase().includes(s) ||
        f.example.toLowerCase().includes(s),
    );
  }, [q]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Formula Sheet</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          {formulas.length} validated laboratory formulas and calculations with worked examples.
          Always confirm against your local laboratory&apos;s standard operating procedure.
        </p>
      </div>

      <input
        className="input"
        placeholder="Search formulas… (e.g. “anion gap”, “creatinine”)"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        aria-label="Search formulas"
      />

      {filtered.length === 0 ? (
        <EmptyState icon="🧮" title="No formulas match" hint="Try a different keyword." />
      ) : (
        <div className="grid lg:grid-cols-2 gap-4">
          {filtered.map((f) => (
            <Card key={f.id}>
              <SectionTitle icon="📐">{f.title}</SectionTitle>
              <p className="font-mono text-sm rounded-md bg-slate-50 dark:bg-slate-800 p-3">{f.formula}</p>
              {f.variables.length > 0 && (
                <ul className="mt-2 text-xs text-[var(--ink-muted)] space-y-0.5">
                  {f.variables.map((v) => (
                    <li key={v.name}>
                      • <strong>{v.name}</strong>{v.unit ? ` (${v.unit})` : ''}
                    </li>
                  ))}
                </ul>
              )}
              <p className="text-sm mt-2">
                <strong>Example:</strong> {f.example}
              </p>
              <p className="text-xs text-[var(--ink-muted)] mt-1">⚠ {f.caveat}</p>
              <p className="text-xs text-[var(--ink-muted)] mt-1">📚 {f.reference}</p>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
