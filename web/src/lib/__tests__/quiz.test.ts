import { describe, expect, it } from 'vitest';
import { balancedSample, computeResult, gradeAnswer, nextAdaptiveQuestion, selectQuestions } from '../quiz';
import { questionBank } from '../content';
import type { QuizAttempt } from '../types';

describe('question selection', () => {
  it('selects by subject', () => {
    const qs = selectQuestions({ kind: 'subject', subjectId: 'hematology' }, 5);
    expect(qs.length).toBeGreaterThan(0);
    expect(qs.every((q) => q.subjectId === 'hematology')).toBe(true);
  });

  it('selects previously incorrect questions only', () => {
    const target = questionBank[0];
    const qs = selectQuestions({ kind: 'incorrect', wrongQuestions: { [target.id]: 2 } }, 10);
    expect(qs.map((q) => q.id)).toContain(target.id);
  });

  it('balanced sample covers multiple subjects for mixed mode', () => {
    const sample = balancedSample(questionBank, 21);
    expect(sample.length).toBe(21);
    expect(new Set(sample.map((q) => q.subjectId)).size).toBeGreaterThan(3);
    expect(new Set(sample.map((q) => q.id)).size).toBe(21);
  });

  it('mock mode returns up to 100 questions', () => {
    const qs = selectQuestions({ kind: 'mock' }, 100);
    expect(qs.length).toBe(Math.min(100, questionBank.length));
  });
});

describe('grading and results', () => {
  it('gradeAnswer checks correct choice', () => {
    const q = questionBank[0];
    expect(gradeAnswer(q, q.correctChoiceId)).toBe(true);
    const wrong = q.choices.find((c) => c.id !== q.correctChoiceId)!;
    expect(gradeAnswer(q, wrong.id)).toBe(false);
  });

  it('computeResult aggregates score, subjects, weak topics', () => {
    const q1 = questionBank[0];
    const q2 = questionBank.find((q) => q.subjectId !== q1.subjectId)!;
    const attempt: QuizAttempt = {
      id: 't1',
      startedAt: '2026-01-01T00:00:00Z',
      finishedAt: '2026-01-01T00:10:00Z',
      mode: 'STUDY',
      filters: { label: 'test' },
      questionIds: [q1.id, q2.id],
      answers: [
        { questionId: q1.id, chosenId: q1.correctChoiceId, correct: true, seconds: 30 },
        { questionId: q2.id, chosenId: 'X', correct: false, seconds: 45 },
      ],
      timeLimitSeconds: null,
      finished: true,
    };
    const result = computeResult(attempt);
    expect(result.total).toBe(2);
    expect(result.correct).toBe(1);
    expect(result.score).toBeCloseTo(0.5, 6);
    expect(result.timeSeconds).toBe(75);
    expect(result.bySubject.length).toBe(2);
    expect(result.incorrectQuestionIds).toEqual([q2.id]);
  });

  it('every shipped question has 4 choices, one correct, full rationales, and a misconception', () => {
    for (const q of questionBank) {
      expect(q.choices.length).toBe(4);
      expect(q.choices.map((c) => c.id).sort()).toEqual(['A', 'B', 'C', 'D']);
      expect(q.choices.some((c) => c.id === q.correctChoiceId)).toBe(true);
      for (const c of q.choices) {
        expect((q.rationales[c.id] ?? '').length).toBeGreaterThan(5);
      }
      expect(q.misconception.length).toBeGreaterThan(5);
      expect(q.explanation.length).toBeGreaterThan(5);
    }
  });
});

describe('adaptive difficulty rule (documented, not ASCP CAT)', () => {
  it('moves difficulty up after a correct answer and down after a miss', () => {
    const pool = questionBank.slice(0, 30);
    const up = nextAdaptiveQuestion(pool, [], true, 2);
    expect(up.level).toBe(3);
    const down = nextAdaptiveQuestion(pool, [], false, 2);
    expect(down.level).toBe(1);
  });

  it('never repeats seen questions', () => {
    const pool = questionBank.slice(0, 10);
    const seen = pool.map((q) => q.id);
    const { question } = nextAdaptiveQuestion(pool, seen, true, 2);
    expect(question).toBeNull();
  });
});
