'use client';

// Mistake Notebook — every incorrect answer is collected here with its teaching
// explanation and misconception note, ready for re-practice.
import Link from 'next/link';
import React, { useMemo } from 'react';
import { Badge, Card, EmptyState, SectionTitle } from '@/components/ui';
import { getQuestion, getSubject, questionBank, getLesson } from '@/lib/content';
import { newAttemptId, selectQuestions } from '@/lib/quiz';
import { startQuiz, useStore } from '@/lib/store';
import type { QuizAttempt, QuizQuestion } from '@/lib/types';
import { useRouter } from 'next/navigation';

export default function MistakesPage() {
  const router = useRouter();
  const wrongQuestions = useStore((s) => s.wrongQuestions);
  const attempts = useStore((s) => s.quizAttempts);

  const mistakes = useMemo(() => {
    const byQuestion = new Map<string, { question: QuizQuestion; wrongCount: number; lastChosen: string | null }>();
    for (const [qid, count] of Object.entries(wrongQuestions)) {
      const q = getQuestion(qid);
      if (q) byQuestion.set(qid, { question: q, wrongCount: count, lastChosen: null });
    }
    for (const a of attempts) {
      for (const ans of a.answers.filter((x) => !x.correct)) {
        const entry = byQuestion.get(ans.questionId);
        if (entry) entry.lastChosen = ans.chosenId;
      }
    }
    return [...byQuestion.values()].sort((a, b) => b.wrongCount - a.wrongCount);
  }, [wrongQuestions, attempts]);

  const bySubject = useMemo(() => {
    const map = new Map<string, typeof mistakes>();
    for (const m of mistakes) {
      const list = map.get(m.question.subjectId) ?? [];
      list.push(m);
      map.set(m.question.subjectId, list);
    }
    return [...map.entries()];
  }, [mistakes]);

  const practiceThese = () => {
    const ids = mistakes.map((m) => m.question.id);
    if (!ids.length) return;
    const selected = selectQuestions({ kind: 'incorrect', questionIds: ids, wrongQuestions }, Math.min(20, ids.length));
    const attempt: QuizAttempt = {
      id: newAttemptId(),
      startedAt: new Date().toISOString(),
      finishedAt: null,
      mode: 'STUDY',
      filters: { label: 'Mistake Notebook practice' },
      questionIds: selected.map((q) => q.id),
      answers: [],
      timeLimitSeconds: null,
      finished: false,
    };
    startQuiz(attempt);
    router.push('/quiz/');
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex-1">
          <h1 className="font-display text-2xl font-bold">Mistake Notebook 📔</h1>
          <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
            Wrong answers aren’t failures — they’re your most honest teachers. Each entry below
            explains exactly why the tempting answer is wrong.
          </p>
        </div>
        {mistakes.length > 0 && (
          <button className="btn-primary" onClick={practiceThese}>
            Practice these {mistakes.length} questions
          </button>
        )}
      </div>

      {mistakes.length === 0 ? (
        <EmptyState
          mood="happy"
          title="No mistakes collected yet"
          hint="Take a quiz and anything you miss will be organized here with a full teaching explanation."
          action={<Link href="/quiz/" className="btn-primary">Start a quiz</Link>}
        />
      ) : (
        bySubject.map(([subjectId, list]) => (
          <section key={subjectId}>
            <SectionTitle icon="📌">
              {getSubject(subjectId)?.name ?? subjectId}
              <Badge tone="danger">{list.length} to revisit</Badge>
            </SectionTitle>
            <div className="space-y-3">
              {list.map(({ question: q, wrongCount, lastChosen }) => (
                <Card key={q.id}>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone="warn">missed {wrongCount}×</Badge>
                    <Badge>{q.difficulty.toLowerCase()}</Badge>
                    {q.lessonId && (
                      <Link
                        className="text-xs link-cute"
                        href={`/library/${q.lessonId.split('/')[0]}/${encodeURIComponent(q.lessonId.split('/')[1])}/`}
                      >
                        Review the lesson →
                      </Link>
                    )}
                  </div>
                  {q.vignette && (
                    <p className="text-sm mt-3 rounded-[12px] p-3" style={{ background: 'var(--surface-2)' }}>{q.vignette}</p>
                  )}
                  <p className="font-semibold mt-3">{q.stem}</p>
                  <ul className="mt-2 space-y-1 text-sm">
                    {q.choices.map((c) => {
                      const isCorrect = c.id === q.correctChoiceId;
                      const isChosen = c.id === lastChosen;
                      return (
                        <li
                          key={c.id}
                          className="rounded-[10px] px-3 py-2"
                          style={{
                            background: isCorrect ? '#dcfce7' : isChosen ? '#ffe4e6' : 'var(--surface-2)',
                            color: 'var(--ink)',
                          }}
                        >
                          <strong>{c.id}.</strong> {c.text}
                          {isCorrect && ' ✓ correct answer'}
                          {isChosen && !isCorrect && ' ✗ your answer'}
                        </li>
                      );
                    })}
                  </ul>
                  <div className="mt-3 rounded-[12px] p-3 text-sm" style={{ background: 'var(--chip)' }}>
                    <strong>Why was I wrong?</strong> {q.misconception}
                    <p className="mt-2">{q.explanation}</p>
                    <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>📚 {q.reference}</p>
                  </div>
                </Card>
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}
