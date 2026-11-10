'use client';

// Subject view — topics and lesson cards.
import Link from 'next/link';
import { notFound, useParams } from 'next/navigation';
import React from 'react';
import { Badge, Card, EmptyState } from '@/components/ui';
import { getSubject, lessonsForSubject } from '@/lib/content';
import { useStore } from '@/lib/store';

export default function SubjectView() {
  const params = useParams<{ subject: string }>();
  const subject = getSubject(params.subject);
  const progress = useStore((s) => s.lessonProgress);
  if (!subject) return notFound();

  const lessons = lessonsForSubject(subject.id);

  return (
    <div className="space-y-6">
      <div>
        <Link href="/library/" className="text-sm underline text-[var(--ink-muted)]">← All subjects</Link>
        <h1 className="font-heading text-2xl font-bold mt-2">{subject.name}</h1>
        <p className="text-sm text-[var(--ink-muted)] max-w-2xl">{subject.description}</p>
        <p className="text-xs text-[var(--ink-muted)] mt-2 italic">{subject.blueprintNote}</p>
      </div>

      {subject.topics.map((topic) => {
        const topicLessons = topic.lessonIds
          .map((id) => lessons.find((l) => l.id === id))
          .filter((l): l is NonNullable<typeof l> => Boolean(l));
        return (
          <section key={topic.id}>
            <h2 className="section-title mb-3">🧩 {topic.name}</h2>
            <div className="grid sm:grid-cols-2 gap-4">
              {topicLessons.map((l) => {
                const p = progress[l.id];
                return (
                  <Link key={l.id} href={`/library/${subject.id}/${encodeURIComponent(l.id.split('/')[1])}/`}>
                    <Card className="h-full hover:shadow-lift transition-shadow">
                      <div className="flex items-start justify-between gap-2">
                        <h3 className="font-heading font-bold text-base">{l.title}</h3>
                        <Badge tone={l.difficulty === 'BEGINNER' ? 'success' : l.difficulty === 'INTERMEDIATE' ? 'warn' : 'danger'}>
                          {l.difficulty.toLowerCase()}
                        </Badge>
                      </div>
                      <p className="text-sm text-[var(--ink-muted)] mt-2 line-clamp-2">{l.summary}</p>
                      <div className="flex flex-wrap gap-1 mt-3">
                        {l.keywords.slice(0, 4).map((k) => (
                          <span key={k} className="chip">{k}</span>
                        ))}
                      </div>
                      <div className="text-xs text-[var(--ink-muted)] mt-3 flex justify-between">
                        <span>⏱ {l.readingMinutes} min read</span>
                        <span>{p?.completedAt ? '✅ completed' : p?.percent ? `📖 ${p.percent}% read` : 'not started'}</span>
                      </div>
                    </Card>
                  </Link>
                );
              })}
            </div>
          </section>
        );
      })}

      {lessons.length === 0 && (
        <EmptyState
          icon="🚧"
          title="Lessons coming soon"
          hint="This subject's lessons are being written. Check back after the next content release."
        />
      )}
    </div>
  );
}
