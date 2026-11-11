'use client';

// Quiz engine UI: study/exam/mock/adaptive modes, results, "Why was I wrong?".
import Link from 'next/link';
import React, { useMemo, useRef, useState } from 'react';
import { Badge, Card, EmptyState, ProgressRing, SectionTitle, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { AiCoach } from '@/components/AiCoach';
import { contextQuote } from '@/lib/motivation';
import { getQuestion, subjects } from '@/lib/content';
import {
  MOCK_QUESTION_COUNT, MOCK_TIME_LIMIT_SECONDS, computeResult, newAttemptId,
  nextAdaptiveQuestion, selectQuestions,
} from '@/lib/quiz';
import { finishQuiz, startQuiz, toggleSavedQuestion, updateQuiz, useStore, addCardFromSeed, subjectAccuracy } from '@/lib/store';
import type { QuizAttempt, QuizQuestion } from '@/lib/types';

type Phase = 'setup' | 'running' | 'results';

export default function QuizPage() {
  const [phase, setPhase] = useState<Phase>('setup');
  const [mode, setMode] = useState<'STUDY' | 'EXAM' | 'MOCK' | 'ADAPTIVE'>('STUDY');
  const [kind, setKind] = useState<'subject' | 'topic' | 'random' | 'weak' | 'incorrect' | 'saved' | 'mixed' | 'mock'>('subject');
  const [subjectId, setSubjectId] = useState<string>(subjects[0].id);
  const [count, setCount] = useState(10);
  const [timed, setTimed] = useState(false);
  const attemptRef = useRef<QuizAttempt | null>(null);
  const [currentIdx, setCurrentIdx] = useState(0);
  const [chosenByQ, setChosenByQ] = useState<Record<string, string>>({});
  const [reveal, setReveal] = useState(false);
  const [adaptiveLevel, setAdaptiveLevel] = useState(2);

  const attempts = useStore((s) => s.quizAttempts);
  const saved = useStore((s) => s.savedQuestions);
  const wrong = useStore((s) => s.wrongQuestions);
  const profile = useStore((s) => s.profile);
  const weakSubjectsLabel = useStore((s) =>
    subjectAccuracy(s)
      .filter((x) => x.total >= 5 && x.accuracy < 0.6)
      .map((x) => `${x.subjectId} (${Math.round(x.accuracy * 100)}%)`),
  );

  const attempt = attemptRef.current;
  const questions: QuizQuestion[] = useMemo(() => {
    if (!attempt) return [];
    return attempt.questionIds.map((id) => getQuestion(id)).filter((q): q is QuizQuestion => Boolean(q));
  }, [attempt, currentIdx]);

  const current = questions[Math.min(currentIdx, questions.length - 1)];

  const begin = () => {
    const selected = selectQuestions(
      {
        kind,
        subjectId,
        wrongQuestions: wrong,
        savedQuestions: saved,
        weakSubjectIds: profile?.weakestSubjects ?? [],
      },
      kind === 'mock' ? MOCK_QUESTION_COUNT : count,
    );
    if (!selected.length) {
      alert('No questions match these filters yet. Try a different mode.');
      return;
    }
    const a: QuizAttempt = {
      id: newAttemptId(),
      startedAt: new Date().toISOString(),
      finishedAt: null,
      mode,
      filters: { subjectId, topicId: null, label: `${kind} · ${subjectId}` },
      questionIds: selected.map((q) => q.id),
      answers: [],
      timeLimitSeconds: mode === 'MOCK' ? MOCK_TIME_LIMIT_SECONDS : timed ? count * 90 : null,
      finished: false,
    };
    attemptRef.current = a;
    startQuiz(a);
    setCurrentIdx(0);
    setChosenByQ({});
    setReveal(false);
    setPhase('running');
  };

  const answer = (choiceId: string) => {
    if (!attempt || !current) return;
    setChosenByQ((m) => ({ ...m, [current.id]: choiceId }));
    const correct = choiceId === current.correctChoiceId;
    const updated: QuizAttempt = {
      ...attempt,
      answers: [
        ...attempt.answers,
        { questionId: current.id, chosenId: choiceId, correct, seconds: 30 },
      ],
    };
    attemptRef.current = updated;
    updateQuiz(updated);
    if (mode === 'STUDY' || mode === 'ADAPTIVE') setReveal(true);
  };

  const next = () => {
    if (!attempt) return;
    setReveal(false);
    if (currentIdx + 1 >= questions.length) {
      const done: QuizAttempt = { ...attempt, finished: true, finishedAt: new Date().toISOString() };
      attemptRef.current = done;
      finishQuiz(done);
      setPhase('results');
      return;
    }
    if (mode === 'ADAPTIVE') {
      const last = attempt.answers[attempt.answers.length - 1];
      const { question, level } = nextAdaptiveQuestion(
        questions,
        attempt.answers.map((a) => a.questionId),
        last?.correct ?? true,
        adaptiveLevel,
      );
      setAdaptiveLevel(level);
      if (question && !attempt.questionIds.includes(question.id)) {
        const updated: QuizAttempt = { ...attempt, questionIds: [...attempt.questionIds, question.id] };
        attemptRef.current = updated;
        updateQuiz(updated);
      }
    }
    setCurrentIdx((i) => i + 1);
  };

  const elapsedLabel = attempt
    ? `${Math.round((Date.now() - new Date(attempt.startedAt).getTime()) / 60000)} min elapsed`
    : '';

  if (phase === 'setup') {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="font-heading text-2xl font-bold">Quiz &amp; Mock Exam</h1>
          <p className="text-sm text-[var(--ink-muted)] max-w-2xl">
            Study mode gives instant feedback and teaching rationales. Exam mode hides answers until
            you finish. Scores here are your own practice estimates — never official ASCP scores or
            predictions of passing.
          </p>
        </div>

        <Card>
          <SectionTitle icon="🎛️">Build your quiz</SectionTitle>
          <div className="grid sm:grid-cols-2 gap-4 text-sm">
            <label>
              Mode
              <select className="input mt-1" value={mode} onChange={(e) => setMode(e.target.value as typeof mode)}>
                <option value="STUDY">Study mode (instant feedback)</option>
                <option value="EXAM">Exam mode (feedback at the end)</option>
                <option value="ADAPTIVE">Adaptive practice (self-adjusting difficulty — not ASCP CAT)</option>
                <option value="MOCK">Full mock exam (100 questions / 150 minutes)</option>
              </select>
            </label>
            <label>
              Question set
              <select
                className="input mt-1"
                value={kind}
                onChange={(e) => {
                  const k = e.target.value as typeof kind;
                  setKind(k);
                  if (k === 'mock') setMode('MOCK');
                }}
              >
                <option value="subject">By subject</option>
                <option value="random">Random questions</option>
                <option value="mixed">Mixed subjects</option>
                <option value="weak">Weak areas only</option>
                <option value="incorrect">Previously incorrect</option>
                <option value="saved">Saved questions</option>
                <option value="mock">Full mock examination</option>
              </select>
            </label>
            {kind === 'subject' && (
              <label>
                Subject
                <select className="input mt-1" value={subjectId} onChange={(e) => setSubjectId(e.target.value)}>
                  {subjects.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </label>
            )}
            {kind !== 'mock' && (
              <label>
                Number of questions: <strong>{count}</strong>
                <input type="range" min={5} max={50} step={5} value={count} className="w-full mt-1"
                  onChange={(e) => setCount(Number(e.target.value))} />
              </label>
            )}
          </div>
          {kind !== 'mock' && (
            <label className="flex items-center gap-2 text-sm mt-3">
              <input type="checkbox" checked={timed} onChange={(e) => setTimed(e.target.checked)} />
              Timed quiz (~90 seconds per question)
            </label>
          )}
          <button className="btn-primary mt-4" onClick={begin}>
            {kind === 'mock' ? 'Start 100-question mock exam' : 'Start quiz'}
          </button>
        </Card>

        {kind === 'weak' && (
          <Card>
            <SectionTitle icon="📉">Weak areas</SectionTitle>
            <p className="text-sm text-[var(--ink-muted)]">
              Based on your quiz history:{' '}
              {weakSubjectsLabel.length ? weakSubjectsLabel.join(', ') : 'not enough data yet — answer some quizzes first'}.
            </p>
          </Card>
        )}

        <Card>
          <SectionTitle icon="📊">Recent attempts</SectionTitle>
          {attempts.filter((a) => a.finished).length === 0 ? (
            <p className="text-sm text-[var(--ink-muted)]">No finished attempts yet.</p>
          ) : (
            <ul className="space-y-1 text-sm">
              {attempts.filter((a) => a.finished).slice(-5).reverse().map((a) => {
                const result = computeResult(a);
                return (
                  <li key={a.id} className="flex justify-between border-b border-slate-100 dark:border-slate-800 py-2">
                    <span>{a.filters.label} · {result.total} questions</span>
                    <span className="font-semibold">{Math.round(result.score * 100)}%</span>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      </div>
    );
  }

  if (phase === 'running' && attempt && current) {
    const chosen = chosenByQ[current.id];
    return (
      <div className="max-w-3xl mx-auto space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <Badge tone="info">{mode.toLowerCase()} mode</Badge>
            <span className="text-xs text-[var(--ink-muted)] ml-2">
              Question {currentIdx + 1} of {questions.length} · {elapsedLabel}
            </span>
          </div>
          <button
            className="btn-ghost text-xs"
            onClick={() => {
              const done: QuizAttempt = { ...attempt, finished: true, finishedAt: new Date().toISOString() };
              attemptRef.current = done;
              finishQuiz(done);
              setPhase('results');
            }}
          >
            End quiz
          </button>
        </div>

        <div className="h-1.5 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
          <div className="h-full bg-[var(--brand)]" style={{ width: `${((currentIdx + 1) / questions.length) * 100}%` }} />
        </div>

        <Card>
          {current.vignette && (
            <p className="text-sm p-3 rounded-md bg-slate-50 dark:bg-slate-800 mb-3">{current.vignette}</p>
          )}
          <p className="font-semibold text-lg">{current.stem}</p>
          <ul className="space-y-2 mt-4">
            {current.choices.map((c) => {
              const isCorrect = c.id === current.correctChoiceId;
              const isChosen = chosen === c.id;
              const showFeedback = reveal && chosen !== undefined;
              return (
                <li key={c.id}>
                  <button
                    className={cx(
                      'w-full text-left rounded-md border p-3 min-h-[44px]',
                      !chosen && 'border-slate-300 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800',
                      showFeedback && isCorrect && 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40',
                      showFeedback && isChosen && !isCorrect && 'border-red-500 bg-red-50 dark:bg-red-950/40',
                    )}
                    disabled={Boolean(chosen)}
                    onClick={() => answer(c.id)}
                  >
                    <strong>{c.id}.</strong> {c.text}
                  </button>
                  {showFeedback && <p className="text-xs text-[var(--ink-muted)] mt-1 pl-3">{current.rationales[c.id]}</p>}
                </li>
              );
            })}
          </ul>

          {reveal && (
            <div className="mt-4 space-y-2">
              <div className={cx('rounded-md p-3 text-sm', chosen === current.correctChoiceId ? 'bg-emerald-50 dark:bg-emerald-950/40' : 'bg-amber-50 dark:bg-amber-950/40')}>
                <strong>{chosen === current.correctChoiceId ? 'Correct.' : 'Incorrect.'}</strong> {current.explanation}
              </div>
              {chosen !== current.correctChoiceId && (
                <div className="rounded-md p-3 text-sm bg-slate-50 dark:bg-slate-800">
                  <strong>Why was I wrong?</strong> {current.misconception}
                  <div className="mt-2">
                    <button
                      className="btn-ghost !py-1 !px-2 !min-h-[32px] text-xs"
                      onClick={() =>
                        addCardFromSeed(
                          {
                            id: `fc-miss-${current.id}-${Date.now()}`,
                            type: 'BASIC',
                            front: current.stem,
                            back: `${current.choices.find((c) => c.id === current.correctChoiceId)?.text} — ${current.explanation.slice(0, 160)}`,
                            tags: ['missed-question', current.topicId],
                            difficulty: 3,
                            source: { kind: 'QUIZ_MISS', lessonId: current.lessonId },
                          },
                          'missed-questions',
                          current.subjectId,
                        )
                      }
                    >
                      → turn into flashcard
                    </button>
                  </div>
                </div>
              )}
              <button className="btn-primary" onClick={next}>
                {currentIdx + 1 >= questions.length ? 'Finish quiz' : 'Next question'}
              </button>
            </div>
          )}
          {!reveal && chosen && mode === 'EXAM' && (
            <button className="btn-primary mt-4" onClick={next}>
              {currentIdx + 1 >= questions.length ? 'Finish quiz' : 'Next question'}
            </button>
          )}
        </Card>

        <div className="flex justify-between text-xs text-[var(--ink-muted)]">
          <button className="underline" onClick={() => toggleSavedQuestion(current.id)}>
            {saved.includes(current.id) ? 'Unsave question' : 'Save question for later'}
          </button>
          <button className="underline" onClick={() => setCurrentIdx((i) => Math.max(0, i - 1))} disabled={currentIdx === 0}>
            Previous
          </button>
        </div>
      </div>
    );
  }

  if (phase === 'results' && attempt) {
    return <ResultsView attempt={attempt} onRestart={() => setPhase('setup')} />;
  }

  return <EmptyState icon="📝" title="No active quiz" action={<button className="btn-primary" onClick={() => setPhase('setup')}>Set up a quiz</button>} />;
}

function ResultsView({ attempt, onRestart }: { attempt: QuizAttempt; onRestart: () => void }) {
  const result = useMemo(() => computeResult(attempt), [attempt]);
  const funny = useStore((s) => s.settings.funnyCommentary);
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const mascotName = useStore((s) => s.settings.mascotName);
  const weakLabel = result.weakTopics.slice(0, 3);
  const showFunny = funny && !seriousMode;

  const banner =
    result.score >= 0.9 ? { title: 'You ate that quiz! 🌸', mood: 'celebrating' as const }
    : result.score >= 0.7 ? { title: 'Beautiful work — your brain is blooming!', mood: 'happy' as const }
    : result.score >= 0.5 ? { title: `${mascotName} believes in you. Let’s review those tricky questions.`, mood: 'encouraging' as const }
    : { title: 'A brave attempt — every wrong answer is now a teacher.', mood: 'encouraging' as const };

  return (
    <div className="space-y-6">
      <div className="hero-gradient p-6 confetti-pop">
        <div className="flex flex-wrap items-center gap-6">
          <Mochi mood={showFunny ? banner.mood : 'happy'} size={110} />
          <ProgressRing percent={result.score} label={`Score ${Math.round(result.score * 100)}%`} />
          <div>
            <h1 className="font-display text-2xl font-bold">{showFunny ? banner.title : 'Quiz complete'}</h1>
            <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
              {result.correct}/{result.total} correct · {Math.round(result.score * 100)}% ·{' '}
              {Math.round(result.timeSeconds / 60)} min
            </p>
            <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              Practice estimates only — not ASCP scores and not guarantees of passing.
            </p>
            {result.incorrectQuestionIds.length > 0 && (
              <p className="text-sm mt-2 sticky-note inline-block">
                💗 {contextQuote('afterWrong')}
              </p>
            )}
            <button className="btn-primary mt-3" onClick={onRestart}>New quiz</button>
          </div>
        </div>
      </div>

      <div className="grid lg:grid-cols-2 gap-4">
        <Card>
          <SectionTitle icon="📊">Accuracy by subject</SectionTitle>
          <ul className="space-y-2">
            {result.bySubject.map((s) => (
              <li key={s.subjectId} className="text-sm">
                <div className="flex justify-between">
                  <span>{s.subjectId}</span>
                  <span>{Math.round(s.accuracy * 100)}% ({s.correct}/{s.total})</span>
                </div>
                <div className="h-1.5 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
                  <div className="h-full bg-[var(--brand-2)]" style={{ width: `${s.accuracy * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
        </Card>

        <Card>
          <SectionTitle icon="🎯">Weak concepts</SectionTitle>
          {weakLabel.length === 0 ? (
            <p className="text-sm text-[var(--ink-muted)]">No topic below 60% in this attempt — keep it up!</p>
          ) : (
            <ul className="space-y-1 text-sm">
              {weakLabel.map((t) => (
                <li key={t.topicId} className="flex justify-between">
                  <span>{t.topicId}</span>
                  <span>{Math.round(t.accuracy * 100)}%</span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-xs text-[var(--ink-muted)] mt-3">
            The planner automatically prioritizes these topics in your next sessions.
          </p>
        </Card>
      </div>

      <Card>
        <SectionTitle icon="❌">Incorrect answers — review with explanations</SectionTitle>
        {result.incorrectQuestionIds.length === 0 ? (
          <p className="text-sm text-[var(--ink-muted)]">Perfect attempt — no incorrect answers.</p>
        ) : (
          <div className="space-y-3">
            {result.incorrectQuestionIds.map((id) => {
              const q = getQuestion(id);
              if (!q) return null;
              return (
                <div key={id} className="rounded-md border border-slate-200 dark:border-slate-700 p-3 text-sm">
                  <p className="font-semibold">{q.stem}</p>
                  <p className="mt-1">
                    <strong>Answer:</strong> {q.correctChoiceId} — {q.choices.find((c) => c.id === q.correctChoiceId)?.text}
                  </p>
                  <p className="text-[var(--ink-muted)] mt-1">{q.explanation}</p>
                  <p className="text-xs mt-1"><strong>Why was I wrong?</strong> {q.misconception}</p>
                  {q.lessonId && (
                    <Link
                      className="text-xs underline mt-1 inline-block"
                      href={`/library/${q.lessonId.split('/')[0]}/${encodeURIComponent(q.lessonId.split('/')[1])}/`}
                    >
                      Review related lesson
                    </Link>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <AiCoach attempt={attempt} result={result} />
    </div>
  );
}
