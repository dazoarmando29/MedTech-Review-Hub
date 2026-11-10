'use client';

// Smart study planner + mastery map + weakness tracker.
import Link from 'next/link';
import React from 'react';
import { Badge, Card, SectionTitle, cx } from '@/components/ui';
import { getSubject, subjects, lessonsForSubject, SUBJECT_WEIGHTS_ASCPI, SUBJECT_WEIGHTS_PH } from '@/lib/content';
import { examCountdownDays, generatePlan } from '@/lib/plan';
import { completePlanItem, savePlan, subjectAccuracy, todayKey, useStore } from '@/lib/store';

export default function PlannerPage() {
  const profile = useStore((s) => s.profile);
  const plan = useStore((s) => s.plan);
  const progress = useStore((s) => s.lessonProgress);
  const accuracy = useStore((s) => subjectAccuracy(s));
  const countdown = examCountdownDays(profile);

  const weights = profile?.examTarget === 'PH_BOARD' ? SUBJECT_WEIGHTS_PH : SUBJECT_WEIGHTS_ASCPI;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1">
          <h1 className="font-heading text-2xl font-bold">Study Planner &amp; Mastery Map</h1>
          <p className="text-sm text-[var(--ink-muted)]">
            Your plan adapts to exam date, weak subjects, and recall performance.
            {countdown !== null && ` ${countdown} days to go.`}
          </p>
        </div>
        {profile && (
          <button
            className="btn-primary"
            onClick={() => savePlan(generatePlan(profile))}
          >
            Regenerate 4-week plan
          </button>
        )}
      </div>

      <Card>
        <SectionTitle icon="🗺️">Mastery map (exam syllabus)</SectionTitle>
        <div className="space-y-3">
          {subjects.map((s) => {
            const ls = lessonsForSubject(s.id);
            const done = ls.filter((l) => progress[l.id]?.completedAt).length;
            const mastery = ls.length ? done / ls.length : 0;
            const acc = accuracy.find((a) => a.subjectId === s.id);
            return (
              <div key={s.id}>
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium">{s.name}</span>
                  <span className="text-xs text-[var(--ink-muted)]">
                    lessons {done}/{ls.length}
                    {acc && ` · quiz accuracy ${Math.round(acc.accuracy * 100)}%`}
                    {' · outline weight ~' + Math.round((weights[s.id] ?? 0) * 100) + '%'}
                  </span>
                </div>
                <div className="h-2 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden mt-1">
                  <div
                    className={cx('h-full rounded-full', mastery >= 0.8 ? 'bg-emerald-500' : mastery >= 0.4 ? 'bg-[var(--brand-2)]' : 'bg-[var(--accent)]')}
                    style={{ width: `${Math.max(3, mastery * 100)}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
        <p className="text-xs text-[var(--ink-muted)] mt-3">
          Weights follow approximate exam content-outline proportions (documented in shared/content).
          They are not official ASCP percentages. Mastery combines lesson completion and your own quiz accuracy.
        </p>
      </Card>

      <Card>
        <SectionTitle icon="🎯">Weakness tracker</SectionTitle>
        {accuracy.filter((a) => a.total >= 3).length === 0 ? (
          <p className="text-sm text-[var(--ink-muted)]">
            Answer quizzes to build your weakness profile. Topics below 60% accuracy get extra sessions automatically.
          </p>
        ) : (
          <ul className="space-y-2">
            {accuracy.filter((a) => a.total >= 3).map((a) => (
              <li key={a.subjectId} className="flex items-center justify-between text-sm">
                <Link href={`/library/${a.subjectId}/`} className="underline">{getSubject(a.subjectId)?.name ?? a.subjectId}</Link>
                <Badge tone={a.accuracy < 0.6 ? 'danger' : a.accuracy < 0.8 ? 'warn' : 'success'}>
                  {Math.round(a.accuracy * 100)}% over {a.total} questions
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <div>
        <SectionTitle icon="📋">Schedule</SectionTitle>
        {plan ? (
          <div className="space-y-3">
            {plan.days.slice(0, 7).map((day) => (
              <Card key={day.date} className={cx(day.date === todayKey() && 'ring-2 ring-[var(--brand)]')}>
                <div className="flex items-center justify-between">
                  <h3 className="font-heading font-bold text-sm">
                    {day.date} {day.date === todayKey() && <Badge tone="info">today</Badge>}
                  </h3>
                  <span className="text-xs text-[var(--ink-muted)]">
                    {day.items.filter((i) => i.completed).length}/{day.items.length} done
                  </span>
                </div>
                <ul className="mt-2 space-y-1">
                  {day.items.map((item) => (
                    <li key={item.id} className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={item.completed}
                        onChange={(e) => completePlanItem(day.date, item.id, e.target.checked)}
                        aria-label={`Complete ${item.type}`}
                      />
                      <span className={cx(item.completed && 'line-through opacity-60')}>
                        {item.type.replace('_', ' ').toLowerCase()} · {getSubject(item.subjectId)?.shortName} · {item.targetMinutes} min
                      </span>
                      {item.lessonId && (
                        <Link
                          href={`/library/${item.subjectId}/${encodeURIComponent(item.lessonId.split('/')[1])}/`}
                          className="text-xs underline ml-auto"
                        >
                          open
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </Card>
            ))}
            <p className="text-xs text-[var(--ink-muted)]">
              Showing the first 7 days of a {plan.days.length}-day plan. Rescheduling unfinished items
              happens automatically when you regenerate.
            </p>
          </div>
        ) : (
          <p className="text-sm text-[var(--ink-muted)]">
            No plan yet — <Link href="/onboarding/" className="underline">complete onboarding</Link> to generate one.
          </p>
        )}
      </div>
    </div>
  );
}
