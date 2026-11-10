'use client';

// Home dashboard — answers "What should I study next?" first.
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import React, { useMemo } from 'react';
import { Badge, Card, EmptyState, ProgressRing, SectionTitle, StatTile } from '@/components/ui';
import { getLesson, getSubject, lessons } from '@/lib/content';
import { examCountdownDays, planForDate } from '@/lib/plan';
import {
  dueCardList, minutesToday, questionsAnsweredToday, recentAccuracy, streakDays,
  todayKey, totalMinutes, useStore, completePlanItem,
} from '@/lib/store';

export default function DashboardPage() {
  const router = useRouter();
  const profile = useStore((s) => s.profile);
  const plan = useStore((s) => s.plan);
  const lessonProgress = useStore((s) => s.lessonProgress);
  const quizAttempts = useStore((s) => s.quizAttempts);
  const cardsDue = useStore((s) => dueCardList(s).length);
  const streak = useStore((s) => streakDays(s));
  const minutes = useStore((s) => minutesToday(s));
  const answered = useStore((s) => questionsAnsweredToday(s));
  const accuracy = useStore((s) => recentAccuracy(s));
  const total = useStore((s) => totalMinutes(s));

  const todayPlan = planForDate(plan, todayKey());
  const countdown = examCountdownDays(profile);
  const completedToday = todayPlan?.items.filter((i) => i.completed).length ?? 0;

  const continueLesson = useMemo(() => {
    const inProgress = Object.entries(lessonProgress)
      .filter(([, p]) => p.percent > 0 && p.percent < 100 && !p.completedAt)
      .sort((a, b) => b[1].percent - a[1].percent)[0];
    if (inProgress) return getLesson(inProgress[0]);
    return lessons.find((l) => !lessonProgress[l.id]?.completedAt) ?? lessons[0];
  }, [lessonProgress]);

  const resumeQuiz = useMemo(() => {
    const unfinished = quizAttempts.filter((a) => !a.finished).slice(-1)[0];
    return unfinished ?? null;
  }, [quizAttempts]);

  const suggested = useMemo(() => {
    const weakest = profile?.weakestSubjects?.[0];
    if (weakest) {
      const l = lessons.find((x) => x.subjectId === weakest && !lessonProgress[x.id]?.completedAt);
      if (l) return l;
    }
    return lessons.find((l) => !lessonProgress[l.id]?.completedAt) ?? lessons[0];
  }, [profile, lessonProgress]);

  const weekly = useStore((s) => {
    const out: { date: string; minutes: number }[] = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      const key = todayKey(d);
      out.push({
        date: key,
        minutes: s.sessions
          .filter((x) => x.startedAt.slice(0, 10) === key)
          .reduce((sum, x) => sum + x.minutes, 0),
      });
    }
    return out;
  });

  if (!profile?.onboardingCompleted) {
    return (
      <EmptyState
        icon="🎓"
        title="Welcome to MedTech Review Hub"
        hint="Let's build your personalized ASCPi review plan. It takes about two minutes — and you can change anything later."
        action={
          <Link href="/onboarding/" className="btn-primary">
            Start onboarding
          </Link>
        }
      />
    );
  }

  return (
    <div className="space-y-6">
      <section className="flex flex-wrap items-center gap-4">
        <div className="flex-1 min-w-[240px]">
          <h1 className="font-heading text-2xl font-bold">
            {greeting()}, future RMT {profile.examTarget === 'PH_BOARD' ? '(PH Board)' : profile.examTarget === 'MLS_ASCPI' ? '(MLS ASCPi)' : ''}
          </h1>
          <p className="text-[var(--ink-muted)] text-sm mt-1">
            {countdown !== null
              ? `${countdown} days to your target exam date. `
              : 'Set your exam date in Settings to enable the countdown. '}
            You have studied {total} minutes in total.
          </p>
        </div>
        <ProgressRing
          percent={todayPlan ? completedToday / Math.max(1, todayPlan.items.length) : 0}
          label="Today's plan completion"
        />
      </section>

      <section aria-label="Key statistics" className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatTile label="Streak" value={<span className="streak-flame">{streak} 🔥</span>} sub="consecutive study days" />
        <StatTile label="Minutes today" value={minutes} sub={`goal ${profile.minutesPerDay} min`} accent="var(--brand-2)" />
        <StatTile label="Flashcards due" value={cardsDue} sub="FSRS schedule" accent="var(--brand-3)" href="/flashcards/" />
        <StatTile label="Questions today" value={answered} sub="answered" href="/quiz/" />
        <StatTile
          label="Recent accuracy"
          value={accuracy === null ? '—' : `${Math.round(accuracy * 100)}%`}
          sub="last 5 quizzes (estimate, not ASCP score)"
          accent="var(--accent)"
          href="/quiz/"
        />
        <StatTile
          label="Exam countdown"
          value={countdown === null ? '—' : `${countdown}d`}
          sub="target date"
          href="/settings/"
        />
      </section>

      <section className="grid lg:grid-cols-3 gap-4">
        <Card className="lg:col-span-2">
          <SectionTitle icon="📋" action={<Link href="/planner/" className="text-sm underline">Full plan</Link>}>
            Today&rsquo;s study goals
          </SectionTitle>
          {todayPlan ? (
            <ul className="space-y-2">
              {todayPlan.items.map((item) => {
                const subject = getSubject(item.subjectId);
                const lesson = item.lessonId ? getLesson(item.lessonId) : null;
                return (
                  <li key={item.id} className="flex items-center gap-3 rounded-md border border-slate-200 dark:border-slate-700 p-3">
                    <input
                      type="checkbox"
                      checked={item.completed}
                      onChange={(e) => completePlanItem(todayPlan.date, item.id, e.target.checked)}
                      className="h-5 w-5"
                      aria-label={`Mark ${item.type} complete`}
                    />
                    <div className="flex-1">
                      <div className="text-sm font-semibold">
                        {lesson ? lesson.title : `${item.type.replace('_', ' ')} — ${subject?.shortName ?? item.subjectId}`}
                      </div>
                      <div className="text-xs text-[var(--ink-muted)]">
                        {item.targetMinutes} min · {subject?.shortName}
                      </div>
                    </div>
                    {lesson && (
                      <Link
                        href={`/library/${lesson.subjectId}/${encodeURIComponent(lesson.id.split('/')[1])}/`}
                        className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs"
                      >
                        Open
                      </Link>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="text-sm text-[var(--ink-muted)]">
              No plan yet. <Link href="/onboarding/" className="underline">Generate your study plan</Link>.
            </p>
          )}
        </Card>

        <div className="space-y-4">
          <Card>
            <SectionTitle icon="📖">Continue reading</SectionTitle>
            {continueLesson && (
              <div>
                <div className="font-semibold text-sm">{continueLesson.title}</div>
                <p className="text-xs text-[var(--ink-muted)] mt-1 line-clamp-2">{continueLesson.summary}</p>
                <Link
                  href={`/library/${continueLesson.subjectId}/${encodeURIComponent(continueLesson.id.split('/')[1])}/`}
                  className="btn-primary mt-3 w-full"
                >
                  Resume lesson
                </Link>
              </div>
            )}
          </Card>

          <Card>
            <SectionTitle icon="⚡">Suggested next</SectionTitle>
            {suggested && (
              <div>
                <Badge tone="info">{getSubject(suggested.subjectId)?.shortName}</Badge>
                <div className="font-semibold text-sm mt-2">{suggested.title}</div>
                <Link
                  href={`/library/${suggested.subjectId}/${encodeURIComponent(suggested.id.split('/')[1])}/`}
                  className="btn-secondary mt-3 w-full"
                >
                  Study now
                </Link>
              </div>
            )}
          </Card>

          {resumeQuiz && (
            <Card>
              <SectionTitle icon="📝">Resume quiz</SectionTitle>
              <p className="text-xs text-[var(--ink-muted)]">
                {resumeQuiz.questionIds.length - resumeQuiz.answers.length} questions remaining in your last session.
              </p>
              <Link href="/quiz/" className="btn-ghost mt-3 w-full">Resume session</Link>
            </Card>
          )}
        </div>
      </section>

      <section className="grid lg:grid-cols-2 gap-4">
        <Card>
          <SectionTitle icon="📊">This week</SectionTitle>
          <div className="flex items-end gap-2 h-32" role="img" aria-label="Weekly study minutes chart">
            {weekly.map((d) => {
              const max = Math.max(60, ...weekly.map((x) => x.minutes));
              return (
                <div key={d.date} className="flex-1 flex flex-col items-center gap-1">
                  <div
                    className="w-full rounded-t-md"
                    style={{
                      height: `${Math.max(4, (d.minutes / max) * 100)}%`,
                      background: d.minutes > 0 ? 'var(--brand-2)' : 'var(--slate-200, #e2e8f0)',
                    }}
                    title={`${d.date}: ${d.minutes} min`}
                  />
                  <span className="text-[10px] text-[var(--ink-muted)]">{d.date.slice(5)}</span>
                </div>
              );
            })}
          </div>
          <p className="text-xs text-[var(--ink-muted)] mt-2">
            Rest days are part of the plan — a missed day never resets your progress data.
          </p>
        </Card>

        <Card>
          <SectionTitle icon="🎯">Weakest subject focus</SectionTitle>
          <WeakSubjectPanel />
        </Card>
      </section>
    </div>
  );
}

function WeakSubjectPanel() {
  const profile = useStore((s) => s.profile);
  const weak = profile?.weakestSubjects ?? [];
  if (!weak.length) {
    return (
      <p className="text-sm text-[var(--ink-muted)]">
        No weak subjects marked yet. Add them in{' '}
        <Link href="/onboarding/" className="underline">onboarding</Link> and the planner will
        automatically schedule extra time for them.
      </p>
    );
  }
  return (
    <ul className="space-y-2">
      {weak.map((id) => {
        const subject = getSubject(id);
        return (
          <li key={id} className="flex items-center justify-between gap-2">
            <div>
              <div className="font-semibold text-sm">{subject?.name ?? id}</div>
              <div className="text-xs text-[var(--ink-muted)]">1.5x planned study time</div>
            </div>
            <Link href={`/library/${id}/`} className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs">
              Review
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function greeting(): string {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}
