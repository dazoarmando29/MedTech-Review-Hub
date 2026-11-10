'use client';

// Quick Review — all high-yield facts and mnemonics in one last-minute hub.
import Link from 'next/link';
import React, { useMemo, useState } from 'react';
import { Badge, Card, SectionTitle, Tabs, cx } from '@/components/ui';
import { getSubject, lessons, subjects } from '@/lib/content';
import { useStore } from '@/lib/store';

type Mode = 'facts' | 'mnemonics' | 'traps';

export default function QuickReviewPage() {
  const [subjectFilter, setSubjectFilter] = useState('all');
  const [mode, setMode] = useState<Mode>('facts');
  const [mastered, setMastered] = useState<Record<string, boolean>>({});
  const saved = useStore((s) => s.lessonProgress);

  const rows = useMemo(() => {
    const out: { lessonId: string; subjectId: string; title: string; text: string }[] = [];
    for (const l of lessons) {
      if (subjectFilter !== 'all' && l.subjectId !== subjectFilter) continue;
      if (mode === 'facts') {
        for (const fact of l.quickReview.highYield) {
          out.push({ lessonId: l.id, subjectId: l.subjectId, title: l.title, text: fact });
        }
      } else if (mode === 'mnemonics') {
        out.push({ lessonId: l.id, subjectId: l.subjectId, title: l.title, text: l.quickReview.mnemonic });
        for (const h of l.memoryHooks) {
          out.push({ lessonId: l.id, subjectId: l.subjectId, title: l.title, text: h.text });
        }
      } else {
        for (const trap of l.deepStudy.examTraps) {
          out.push({ lessonId: l.id, subjectId: l.subjectId, title: l.title, text: trap });
        }
      }
    }
    return out;
  }, [subjectFilter, mode]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold">Quick Review ⚡</h1>
        <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
          The one-page revision hub: high-yield facts, mnemonics, and exam traps pulled from every
          lesson. Tap to mark items you’ve mastered.
        </p>
      </div>

      <div className="flex flex-wrap gap-2 items-center">
        <Tabs
          tabs={[
            { id: 'facts', label: 'High-yield facts' },
            { id: 'mnemonics', label: 'Memory hooks' },
            { id: 'traps', label: 'Exam traps' },
          ]}
          active={mode}
          onChange={(id) => setMode(id as Mode)}
        />
        <select className="input !w-auto" value={subjectFilter} onChange={(e) => setSubjectFilter(e.target.value)} aria-label="Filter by subject">
          <option value="all">All subjects</option>
          {subjects.map((s) => <option key={s.id} value={s.id}>{s.shortName}</option>)}
        </select>
      </div>

      <div className="space-y-2">
        {rows.map((r, i) => {
          const key = `${r.lessonId}|${r.text.slice(0, 40)}`;
          const done = mastered[key] ?? false;
          return (
            <div
              key={`${key}-${i}`}
              className={cx('card-soft p-4 flex items-start gap-3 cursor-pointer', done && 'opacity-60')}
              onClick={() => setMastered((m) => ({ ...m, [key]: !m[key] }))}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => e.key === 'Enter' && setMastered((m) => ({ ...m, [key]: !m[key] }))}
            >
              <span className="mt-0.5" aria-hidden>{done ? '✅' : mode === 'traps' ? '🪤' : '💡'}</span>
              <div className="flex-1">
                <p className={cx('text-sm', done && 'line-through')}>{r.text}</p>
                <div className="flex flex-wrap gap-2 mt-1.5 items-center">
                  <Badge>{getSubject(r.subjectId)?.shortName}</Badge>
                  <Link
                    className="text-xs link-cute"
                    href={`/library/${r.subjectId}/${encodeURIComponent(r.lessonId.split('/')[1])}/`}
                  >
                    {r.title}
                  </Link>
                  {saved[r.lessonId]?.completedAt && <Badge tone="success">lesson done</Badge>}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
