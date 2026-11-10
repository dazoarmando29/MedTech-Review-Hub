'use client';

// Personalized pink dashboard — answers "What should I study next?" first.
import Link from 'next/link';
import React, { useMemo, useState } from 'react';
import { Badge, Card, EmptyState, SectionTitle, StatTile, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { MotivationWidget } from '@/components/MotivationWidget';
import { IconGarden } from '@/components/icons';
import { getLesson, getSubject, lessons } from '@/lib/content';
import { computeStreak, weeklyWrapped, flowerStage } from '@/lib/cozy';
import { examCountdownDays, planForDate } from '@/lib/plan';
import {
  completePlanItem, dueCardList, minutesToday, questionsAnsweredToday,
  recentAccuracy, todayKey, totalMinutes, useStore,
} from '@/lib/store';

export default function DashboardPage() {
  const profile = useStore((s) => s.profile);
  const plan = useStore((s) => s.plan);
  const lessonProgress = useStore((s) => s.lessonProgress);
  const quizAttempts = useStore((s) => s.quizAttempts);
  const settings = useStore((s) => s.settings);
  const garden = useStore((s) => s.garden);
  const cardsDue = useStore((s) => dueCardList(s).length);
  const streak = useStore((s) => computeStreak(s));
  const minutes = useStore((s) => minutesToday(s));
  const answered = useStore((s) => questionsAnsweredToday(s));
  const accuracy = useStore((s) => recentAccuracy(s));
  const total = useStore((s) => totalMinutes(s));
  const [showWrapped, setShowWrapped] = useState(false);

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

  const resumeQuiz = useMemo(() => quizAttempts.filter((a) => !a.finished).slice(-1)[0] ?? null, [quizAttempts]);

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

  const wrapped = useStore((s) => weeklyWrapped(s));

  if (!profile?.onboardingCompleted) {
    return (
      <div className="hero-gradient p-8 sm:p-12">
        <div className="flex flex-wrap items-center gap-8">
          <Mochi mood="excited" size={150} className="loading-mochi" />
          <div className="flex-1 min-w-[260px]">
            <h1 className="font-display text-3xl font-bold">Welcome to your study companion 🌸</h1>
            <p className="mt-2 text-sm max-w-md" style={{ color: 'var(--muted-strong)' }}>
              A cozy little corner for a future Medical Laboratory Scientist. Let’s build your
              personalized ASCPi review plan — it takes about two minutes, and you can change
              anything later.
            </p>
            <Link href="/onboarding/" className="btn-primary mt-5">Start onboarding</Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* greeting hero */}
      <section className="hero-gradient p-6 sm:p-8">
        <div className="flex flex-wrap items-center gap-6">
          <div className="flex-1 min-w-[260px]">
            <h1 className="font-display text-2xl sm:text-3xl font-bold">
              {greeting()}, {settings.displayName || 'future MLS'}! 🌸
            </h1>
            <p className="mt-1 text-sm" style={{ color: 'var(--muted-strong)' }}>
              Another day closer to that ASCPi dream.
            </p>
            <div className="flex flex-wrap gap-2 mt-4">
              {countdown !== null && (
                <span className="chip">
                  🌷 {countdown} {countdown === 1 ? 'day' : 'days'} to your target exam
                </span>
              )}
              <span className="chip">🔥 {streak}-day streak</span>
              <span className="chip">⏱ {minutes} min today</span>
            </div>
          </div>
          <Mochi mood={minutes > 0 ? 'happy' : 'encouraging'} size={130} />
        </div>
      </section>

      {/* motivation + gift note */}
      <MotivationWidget />
      <section className="grid lg:grid-cols-3 gap-4">
        <div className="card p-5 border-l-4 lg:col-span-2" style={{ borderLeftColor: 'var(--primary-soft)' }}>
          <p className="text-sm italic leading-relaxed">“{settings.giftNote}”</p>
          <p className="text-[11px] mt-2" style={{ color: 'var(--muted)' }}>A little note for you — edit it in Settings.</p>
        </div>
        <div className="card p-5">
          <SectionTitle icon="🛋️">Cozy Study Room</SectionTitle>
          <p className="text-xs" style={{ color: 'var(--muted)' }}>
            Music + Pomodoro + motivation in one calm, full-focus space.
          </p>
          <Link href="/study-room/" className="btn-primary mt-3 w-full">Enter Study Room</Link>
        </div>
      </section>

      {/* stats */}
      <section aria-label="Key statistics" className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatTile label="Streak" value={<span>{streak} 🔥</span>} sub="study days in a row" />
        <StatTile label="Minutes today" value={minutes} sub={`goal ${profile.minutesPerDay} min`} />
        <StatTile label="Flashcards due" value={cardsDue} sub="FSRS schedule" href="/flashcards/" />
        <StatTile label="Questions today" value={answered} sub="answered" href="/quiz/" />
        <StatTile
          label="Recent accuracy"
          value={accuracy === null ? '—' : `${Math.round(accuracy * 100)}%`}
          sub="your own estimate — not an ASCP score"
          href="/quiz/"
        />
        <StatTile label="Total studied" value={`${Math.round(total / 60)}h`} sub="all time" href="/planner/" />
      </section>

      {/* plan + continue */}
      <section className="grid lg:grid-cols-3 gap-4">
        <div className="card p-5 lg:col-span-2">
          <SectionTitle icon="📋" action={<Link href="/planner/" className="text-sm link-cute">Full plan</Link>}>
            Today’s study plan
          </SectionTitle>
          {todayPlan ? (
            <ul className="space-y-2">
              {todayPlan.items.map((item) => {
                const subject = getSubject(item.subjectId);
                const lesson = item.lessonId ? getLesson(item.lessonId) : null;
                return (
                  <li key={item.id} className="flex items-center gap-3 rounded-[14px] border p-3" style={{ borderColor: 'var(--line)', background: 'var(--surface-2)' }}>
                    <input
                      type="checkbox"
                      checked={item.completed}
                      onChange={(e) => completePlanItem(todayPlan.date, item.id, e.target.checked)}
                      className="h-5 w-5 rounded accent-pink-600"
                      aria-label={`Mark ${item.type} complete`}
                    />
                    <div className="flex-1">
                      <div className="text-sm font-semibold">
                        {lesson ? lesson.title : `${item.type.replace('_', ' ')} — ${subject?.shortName ?? item.subjectId}`}
                      </div>
                      <div className="text-xs" style={{ color: 'var(--muted)' }}>
                        {item.targetMinutes} min · {subject?.shortName}
                      </div>
                    </div>
                    {lesson && (
                      <Link
                        href={`/library/${lesson.subjectId}/${encodeURIComponent(lesson.id.split('/')[1])}/`}
                        className="btn-secondary !py-1.5 !px-3 !min-h-[36px] text-xs"
                      >
                        Open
                      </Link>
                    )}
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              No plan yet. <Link href="/onboarding/" className="link-cute">Generate your study plan</Link>.
            </p>
          )}
          {todayPlan && (
            <p className="text-xs mt-3" style={{ color: 'var(--muted)' }}>
              {completedToday}/{todayPlan.items.length} done today — small progress is still progress.
            </p>
          )}
        </div>

        <div className="space-y-4">
          <div className="card p-5">
            <SectionTitle icon="📖">Continue learning</SectionTitle>
            {continueLesson && (
              <div>
                <div className="font-semibold text-sm">{continueLesson.title}</div>
                <p className="text-xs mt-1 line-clamp-2" style={{ color: 'var(--muted)' }}>{continueLesson.summary}</p>
                <Link
                  href={`/library/${continueLesson.subjectId}/${encodeURIComponent(continueLesson.id.split('/')[1])}/`}
                  className="btn-primary mt-3 w-full"
                >
                  Resume lesson
                </Link>
              </div>
            )}
          </div>

          <div className="card p-5">
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
          </div>

          {resumeQuiz && (
            <div className="card p-5">
              <SectionTitle icon="📝">Resume quiz</SectionTitle>
              <p className="text-xs" style={{ color: 'var(--muted)' }}>
                {resumeQuiz.questionIds.length - resumeQuiz.answers.length} questions remaining.
              </p>
              <Link href="/quiz/" className="btn-ghost mt-3 w-full">Resume session</Link>
            </div>
          )}
        </div>
      </section>

      {/* week + garden + wrapped */}
      <section className="grid lg:grid-cols-3 gap-4">
        <div className="card p-5">
          <SectionTitle icon="📊">This week</SectionTitle>
          <div className="flex items-end gap-2 h-28" role="img" aria-label="Weekly study minutes chart">
            {weekly.map((d) => {
              const max = Math.max(60, ...weekly.map((x) => x.minutes));
              return (
                <div key={d.date} className="flex-1 flex flex-col items-center gap-1">
                  <div
                    className="w-full rounded-t-full"
                    style={{
                      height: `${Math.max(6, (d.minutes / max) * 100)}%`,
                      background: d.minutes > 0 ? 'var(--primary-soft)' : 'var(--chip)',
                    }}
                    title={`${d.date}: ${d.minutes} min`}
                  />
                  <span className="text-[10px]" style={{ color: 'var(--muted)' }}>{d.date.slice(5)}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="card p-5">
          <SectionTitle icon="🌷" action={<Link href="/garden/" className="text-sm link-cute"><IconGarden size={16} /></Link>}>
            Study Garden
          </SectionTitle>
          <div className="flex items-end gap-1 h-24">
            {garden.flowers.slice(-8).map((f) => (
              <span key={f.id} className="text-2xl" title={`${f.kind} — ${flowerStage(f)}`}>
                {flowerStage(f) === 'bloom' ? '🌸' : flowerStage(f) === 'bud' ? '🌱' : flowerStage(f) === 'sprout' ? '🌿' : '🫘'}
              </span>
            ))}
            {garden.flowers.length === 0 && (
              <p className="text-xs self-center" style={{ color: 'var(--muted)' }}>
                Study sessions plant flowers here. Your first seed is waiting! 🌱
              </p>
            )}
          </div>
          <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
            {garden.flowers.filter((f) => flowerStage(f) === 'bloom').length} bloomed · {garden.flowers.length} planted
          </p>
        </div>

        <div className="card p-5">
          <SectionTitle icon="🎁">Weekly Wrapped</SectionTitle>
          {!showWrapped ? (
            <button className="btn-primary w-full" onClick={() => setShowWrapped(true)}>
              Open this week’s recap
            </button>
          ) : (
            <div className="text-sm space-y-1.5 confetti-pop">
              <p><strong>{wrapped.minutes} minutes</strong> of study this week 🌸</p>
              <p><strong>{wrapped.cardsReviewed}</strong> flashcards reviewed</p>
              <p><strong>{wrapped.quizzesFinished}</strong> quizzes finished{wrapped.accuracy !== null && <> · {Math.round(wrapped.accuracy * 100)}% accuracy</>}</p>
              {wrapped.bestSubject && <p>Best subject: <strong>{getSubject(wrapped.bestSubject)?.shortName ?? wrapped.bestSubject}</strong></p>}
              <p className="italic" style={{ color: 'var(--muted-strong)' }}>{wrapped.topCompliment}</p>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function greeting(): string {
  try {
    const hour = Number(
      new Date().toLocaleString('en-US', { timeZone: 'Asia/Manila', hour: 'numeric', hour12: false }),
    );
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  } catch {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
  }
}
