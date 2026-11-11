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
        <h1 className="font-heading text-2xl font-bold">ASCPi Review Library 📚</h1>
        <p className="text-sm max-w-2xl" style={{ color: 'var(--muted-strong)' }}>
          Seven subject areas, organized exactly like the exam blueprint. Each lesson has a
          <strong> Quick Review</strong> (fast facts) and <strong>Deep Study</strong> (full
          explanations) — pick whichever fits your time today.
        </p>
      </div>

      <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {subjects.map((s) => {
          const ls = lessonsForSubject(s.id);
          const done = ls.filter((l) => progress[l.id]?.completedAt).length;
          return (
            <Link key={s.id} href={`/library/${s.id}/`} className="block h-full">
              <Card className="h-full flex flex-col hover:shadow-lift transition-shadow">
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-heading font-bold leading-snug min-w-0">{s.name}</h2>
                  <Badge tone="info">{ls.length} lessons</Badge>
                </div>
                <p className="text-sm mt-2 line-clamp-3" style={{ color: 'var(--muted-strong)' }}>
                  {s.description}
                </p>
                <div className="mt-auto pt-4">
                  <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}>
                    <div
                      className="h-full rounded-full"
                      style={{ width: `${ls.length ? (done / ls.length) * 100 : 0}%`, background: 'var(--primary-soft)' }}
                    />
                  </div>
                  <div className="text-xs mt-1.5" style={{ color: 'var(--muted)' }}>
                    {done}/{ls.length} lessons completed
                  </div>
                </div>
              </Card>
            </Link>
          );
        })}
      </div>

      <Card>
        <SectionTitle icon="🧭">New here? Start like this</SectionTitle>
        <ol className="text-sm space-y-2 list-decimal pl-5" style={{ color: 'var(--muted-strong)' }}>
          <li><strong>Open any subject</strong> that feels hardest for you — that is where review pays off most.</li>
          <li><strong>Read the Quick Review</strong> first. Short, high-yield facts you can finish in minutes.</li>
          <li><strong>Try the practice questions</strong> at the end of each lesson — mistakes become flashcards.</li>
          <li><strong>Review a little daily.</strong> 20 minutes every day beats 5 hours the night before.</li>
        </ol>
        <p className="text-xs mt-4" style={{ color: 'var(--muted)' }}>
          Lesson topics are mapped to the official ASCP BOC MLS content outline (approximate
          weights, not official percentages). This is an independent reviewer — not affiliated
          with or endorsed by ASCP.
        </p>
      </Card>
    </div>
  );
}
