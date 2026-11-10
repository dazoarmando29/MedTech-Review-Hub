'use client';

// Library index — 7 ASCPi subject categories.
import Link from 'next/link';
import React from 'react';
import { Badge, Card, SectionTitle } from '@/components/ui';
import { lessonsForSubject, subjects } from '@/lib/content';
import { useStore } from '@/lib/store';

export default function LibraryPage() {
  const progress = useStore((s) => s.lessonProgress);
  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">ASCPi Review Library</h1>
        <p className="text-sm text-[var(--ink-muted)] max-w-2xl">
          Structured lessons mapped to the ASCP BOC MLS examination content guideline (topic level).
          Each lesson offers Quick Review for last-minute revision and Deep Study for full explanations.
          This is an independent reviewer — not affiliated with ASCP.
        </p>
      </div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {subjects.map((s) => {
          const ls = lessonsForSubject(s.id);
          const done = ls.filter((l) => progress[l.id]?.completedAt).length;
          return (
            <Link key={s.id} href={`/library/${s.id}/`} className="block">
              <Card className="h-full hover:shadow-lift transition-shadow">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="font-heading font-bold">{s.name}</h2>
                  <Badge tone="info">{ls.length} lessons</Badge>
                </div>
                <p className="text-sm text-[var(--ink-muted)] mt-2 line-clamp-3">{s.description}</p>
                <div className="mt-3">
                  <div className="h-1.5 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-[var(--brand-2)]"
                      style={{ width: `${ls.length ? (done / ls.length) * 100 : 0}%` }}
                    />
                  </div>
                  <div className="text-xs text-[var(--ink-muted)] mt-1">{done}/{ls.length} completed</div>
                </div>
                <p className="text-[11px] text-[var(--ink-muted)] mt-3 italic">{s.blueprintNote}</p>
              </Card>
            </Link>
          );
        })}
      </div>

      <Card>
        <SectionTitle icon="🧭">How to use this library</SectionTitle>
        <ul className="text-sm space-y-1 list-disc pl-5 text-[var(--ink-muted)]">
          <li><strong>Quick Review</strong> — high-yield facts and must-know tables for final-hour revision.</li>
          <li><strong>Deep Study</strong> — scientific reasoning, clinical significance, and lab interpretation.</li>
          <li>Every lesson lists its references; medical reference ranges always note method/lab variation.</li>
          <li>Use the practice questions at the end of each lesson, then convert tricky points into flashcards.</li>
        </ul>
      </Card>
    </div>
  );
}
