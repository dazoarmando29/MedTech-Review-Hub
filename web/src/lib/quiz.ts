// Quiz engine: selection modes, grading, and results analytics.
// Mirrors Android QuizScoring and backend QuizService semantics.
import { questionBank, getQuestion, SUBJECT_WEIGHTS_ASCPI } from './content';
import type { QuizAttempt, QuizQuestion } from './types';

export type QuizMode = 'STUDY' | 'EXAM' | 'ADAPTIVE' | 'MOCK';

export interface QuizFilters {
  subjectId?: string | null;
  topicId?: string | null;
  kind: 'subject' | 'topic' | 'random' | 'weak' | 'incorrect' | 'saved' | 'mixed' | 'mock';
  wrongQuestions?: Record<string, number>;
  savedQuestions?: string[];
  weakSubjectIds?: string[];
  questionIds?: string[];
}

export const MOCK_QUESTION_COUNT = 100;
export const MOCK_TIME_LIMIT_SECONDS = 150 * 60;

export function selectQuestions(filters: QuizFilters, count: number): QuizQuestion[] {
  let pool = questionBank.slice();

  if (filters.questionIds?.length) {
    pool = filters.questionIds
      .map((id) => getQuestion(id))
      .filter((q): q is QuizQuestion => Boolean(q));
  } else {
    switch (filters.kind) {
      case 'subject':
        pool = pool.filter((q) => q.subjectId === filters.subjectId);
        break;
      case 'topic':
        pool = pool.filter((q) => q.topicId === filters.topicId);
        break;
      case 'incorrect':
        pool = pool.filter((q) => (filters.wrongQuestions?.[q.id] ?? 0) > 0);
        break;
      case 'saved':
        pool = pool.filter((q) => filters.savedQuestions?.includes(q.id));
        break;
      case 'weak':
        pool = pool.filter((q) => filters.weakSubjectIds?.includes(q.subjectId));
        break;
      case 'mixed':
      case 'random':
      case 'mock':
        break;
    }
  }

  // balanced sampling across subjects for mixed/mock
  if (filters.kind === 'mixed' || filters.kind === 'mock') {
    return balancedSample(pool, count);
  }
  return shuffle(pool).slice(0, count);
}

export function balancedSample(pool: QuizQuestion[], count: number): QuizQuestion[] {
  const bySubject = new Map<string, QuizQuestion[]>();
  for (const q of pool) {
    const list = bySubject.get(q.subjectId) ?? [];
    list.push(q);
    bySubject.set(q.subjectId, list);
  }
  for (const list of bySubject.values()) shuffleInPlace(list);

  const target = new Map<string, number>();
  const subjects = [...bySubject.keys()];
  for (const s of subjects) {
    const weight = SUBJECT_WEIGHTS_ASCPI[s] ?? 1 / subjects.length;
    target.set(s, Math.max(1, Math.round(weight * count)));
  }
  const out: QuizQuestion[] = [];
  let added = true;
  while (out.length < count && added) {
    added = false;
    for (const s of subjects) {
      const list = bySubject.get(s)!;
      if (list.length && out.length < count) {
        out.push(list.shift()!);
        added = true;
      }
    }
  }
  return shuffle(out);
}

export function gradeAnswer(question: QuizQuestion, chosenId: string): boolean {
  return question.correctChoiceId === chosenId;
}

export interface QuizResult {
  score: number;
  correct: number;
  total: number;
  timeSeconds: number;
  bySubject: { subjectId: string; correct: number; total: number; accuracy: number }[];
  weakTopics: { topicId: string; accuracy: number; total: number }[];
  incorrectQuestionIds: string[];
}

export function computeResult(attempt: QuizAttempt): QuizResult {
  const answers = attempt.answers;
  const correct = answers.filter((a) => a.correct).length;
  const total = answers.length;

  const bySubjectMap = new Map<string, { correct: number; total: number }>();
  const byTopicMap = new Map<string, { correct: number; total: number }>();
  for (const a of answers) {
    const q = getQuestion(a.questionId);
    if (!q) continue;
    const s = bySubjectMap.get(q.subjectId) ?? { correct: 0, total: 0 };
    s.total += 1;
    if (a.correct) s.correct += 1;
    bySubjectMap.set(q.subjectId, s);
    const t = byTopicMap.get(q.topicId) ?? { correct: 0, total: 0 };
    t.total += 1;
    if (a.correct) t.correct += 1;
    byTopicMap.set(q.topicId, t);
  }

  const timeSeconds = attempt.answers.reduce((sum, a) => sum + a.seconds, 0);

  return {
    score: total ? correct / total : 0,
    correct,
    total,
    timeSeconds,
    bySubject: [...bySubjectMap.entries()].map(([subjectId, v]) => ({
      subjectId,
      correct: v.correct,
      total: v.total,
      accuracy: v.total ? v.correct / v.total : 0,
    })),
    weakTopics: [...byTopicMap.entries()]
      .map(([topicId, v]) => ({ topicId, accuracy: v.total ? v.correct / v.total : 0, total: v.total }))
      .filter((t) => t.accuracy < 0.6)
      .sort((a, b) => a.accuracy - b.accuracy),
    incorrectQuestionIds: answers.filter((a) => !a.correct).map((a) => a.questionId),
  };
}

/**
 * Documented adaptive rule (NOT ASCP computer-adaptive testing):
 * after each answer, adjust the target difficulty. Correct -> move up one level,
 * incorrect -> move down one level; sample the next question closest to the
 * target difficulty among unseen questions.
 */
export function nextAdaptiveQuestion(
  pool: QuizQuestion[],
  seenIds: string[],
  lastCorrect: boolean,
  currentLevel: number,
): { question: QuizQuestion | null; level: number } {
  const level = Math.min(3, Math.max(1, lastCorrect ? currentLevel + 1 : currentLevel - 1));
  const unseen = pool.filter((q) => !seenIds.includes(q.id));
  if (!unseen.length) return { question: null, level };
  const difficultyValue = (q: QuizQuestion) =>
    q.difficulty === 'EASY' ? 1 : q.difficulty === 'MEDIUM' ? 2 : 3;
  unseen.sort(
    (a, b) =>
      Math.abs(difficultyValue(a) - level) - Math.abs(difficultyValue(b) - level),
  );
  return { question: unseen[0], level };
}

function shuffle<T>(arr: T[]): T[] {
  const out = arr.slice();
  shuffleInPlace(out);
  return out;
}

function shuffleInPlace<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export function newAttemptId(): string {
  return `qa-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}
