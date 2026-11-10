// Local-first application store. Persists to localStorage; optionally syncs to
// the MedTech backend (shared/api/API.md) when settings.apiBaseUrl is set.
'use client';

import { useSyncExternalStore } from 'react';
import { decks as seedDecks, glossarySeed, questionBank } from './content';
import { applyReview, isDue, newSchedule } from './fsrs';
import type {
  ContentIssueReport, DiaryEntry, FlashcardSeed, FocusSession, Habit, HighlightEntry,
  LessonProgress, PdfAnnotation, PdfDocMeta, PlanDay, Profile, QuizAttempt, Rating,
  ReviewRecord, Settings, StudyPlan, StudySession, UserCard,
} from './types';

const STORAGE_KEY = 'mtrh-state-v1';

export interface AdminOverride {
  lessons: Record<string, unknown>;
  questions: Record<string, unknown>;
  statuses: Record<string, string>;
  audit: { at: string; action: string; target: string; by: string }[];
}

export interface AppState {
  profile: Profile | null;
  settings: Settings;
  cards: Record<string, UserCard>;
  quizAttempts: QuizAttempt[];
  sessions: StudySession[];
  habits: Habit[];
  habitLogs: Record<string, number>; // `${habitId}|${date}` -> amount
  diary: DiaryEntry[];
  plan: StudyPlan | null;
  lessonProgress: Record<string, LessonProgress>;
  highlights: HighlightEntry[];
  pdfs: PdfDocMeta[];
  pdfAnnotations: PdfAnnotation[];
  focusSessions: FocusSession[];
  savedQuestions: string[];
  wrongQuestions: Record<string, number>;
  glossary: GlossaryTermSeed[];
  reports: ContentIssueReport[];
  admin: AdminOverride;
  lastSyncAt: string | null;
}

interface GlossaryTermSeed {
  id: string;
  term: string;
  acronym?: string;
  definition: string;
  subjectId: string;
  example?: string;
  source: string;
  custom?: boolean;
}

export const DEFAULT_SETTINGS: Settings = {
  theme: 'system',
  textScale: 100,
  seriousMode: false,
  reduceMotion: false,
  mascot: 'cat',
  mascotCommentary: true,
  explanationStyle: 'FORMAL_ENGLISH',
  ttsEnabled: false,
  apiBaseUrl: 'https://medtech-review-hub-api.dazoarmando.workers.dev',
  dailyNewCards: 20,
  dailyReviewLimit: 200,
  targetRetention: 0.9,
  highlightColors: {
    YELLOW: 'Must memorize',
    RED: 'High-priority exam fact',
    BLUE: 'Definition',
    GREEN: 'Fully understood',
    PURPLE: 'Confusing / needs further study',
  },
};

function seedCards(): Record<string, UserCard> {
  const out: Record<string, UserCard> = {};
  for (const deck of seedDecks) {
    for (const c of deck.cards as FlashcardSeed[]) {
      out[c.id] = {
        id: c.id,
        deckId: deck.id,
        front: c.front,
        back: c.back,
        type: c.type,
        tags: c.tags ?? [],
        subjectId: deck.subjectId,
        topicId: null,
        difficulty: (c.difficulty ?? 1) as 1 | 2 | 3,
        note: c.note ?? '',
        favorite: false,
        imageRef: null,
        source: c.source ?? { kind: 'MANUAL' },
        schedule: newSchedule(new Date(0)),
        history: [],
      };
    }
  }
  return out;
}

function initialState(): AppState {
  return {
    profile: null,
    settings: { ...DEFAULT_SETTINGS },
    cards: seedCards(),
    quizAttempts: [],
    sessions: [],
    habits: [],
    habitLogs: {},
    diary: [],
    plan: null,
    lessonProgress: {},
    highlights: [],
    pdfs: [],
    pdfAnnotations: [],
    focusSessions: [],
    savedQuestions: [],
    wrongQuestions: {},
    glossary: glossarySeed.map((g) => ({ ...g })),
    reports: [],
    admin: { lessons: {}, questions: {}, statuses: {}, audit: [] },
    lastSyncAt: null,
  };
}

function load(): AppState {
  if (typeof window === 'undefined') return initialState();
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return initialState();
    const parsed = JSON.parse(raw) as Partial<AppState>;
    const base = initialState();
    const merged: AppState = {
      ...base,
      ...parsed,
      settings: { ...base.settings, ...(parsed.settings ?? {}) },
      cards: { ...base.cards, ...(parsed.cards ?? {}) },
      admin: { ...base.admin, ...(parsed.admin ?? {}) },
    };
    // New library cards added since last visit are merged in.
    return merged;
  } catch {
    return initialState();
  }
}

let state: AppState = load();
const listeners = new Set<() => void>();

function emit() {
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // storage full — keep working in memory
    }
  }
  listeners.forEach((l) => l());
}

export function getState(): AppState {
  return state;
}

export function setState(mutator: (s: AppState) => AppState): void {
  state = mutator(state);
  emit();
}

export function useStore<T>(selector: (s: AppState) => T): T {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    () => selector(state),
    () => selector(state),
  );
}

export function resetAll(): void {
  state = initialState();
  emit();
}

// ---------- profile & settings ----------

export function saveProfile(profile: Profile): void {
  setState((s) => ({ ...s, profile }));
}

export function updateSettings(patch: Partial<Settings>): void {
  setState((s) => ({ ...s, settings: { ...s.settings, ...patch } }));
}

// ---------- flashcards ----------

export function reviewCard(cardId: string, rating: Rating, now = new Date()): void {
  setState((s) => {
    const card = s.cards[cardId];
    if (!card) return s;
    const { schedule, intervalDays } = applyReview(card.schedule, rating, now, s.settings.targetRetention);
    const history: ReviewRecord[] = [
      ...card.history,
      { at: now.toISOString(), rating, intervalDays: Math.round(intervalDays * 100) / 100 },
    ];
    return { ...s, cards: { ...s.cards, [cardId]: { ...card, schedule, history } } };
  });
}

export function upsertCard(card: UserCard): void {
  setState((s) => ({ ...s, cards: { ...s.cards, [card.id]: card } }));
}

export function deleteCard(cardId: string): void {
  setState((s) => {
    const cards = { ...s.cards };
    delete cards[cardId];
    return { ...s, cards };
  });
}

export function toggleFavorite(cardId: string): void {
  setState((s) => {
    const card = s.cards[cardId];
    if (!card) return s;
    return { ...s, cards: { ...s.cards, [cardId]: { ...card, favorite: !card.favorite } } };
  });
}

export function addCardFromSeed(seed: FlashcardSeed, deckId: string, subjectId: string): void {
  setState((s) => ({
    ...s,
    cards: {
      ...s.cards,
      [seed.id]: {
        id: seed.id,
        deckId,
        front: seed.front,
        back: seed.back,
        type: seed.type,
        tags: seed.tags ?? [],
        subjectId,
        topicId: null,
        difficulty: (seed.difficulty ?? 1) as 1 | 2 | 3,
        note: seed.note ?? '',
        favorite: false,
        imageRef: null,
        source: seed.source ?? { kind: 'MANUAL' },
        schedule: newSchedule(new Date(0)),
        history: [],
      },
    },
  }));
}

export function dueCards(now = new Date()): UserCard[] {
  return Object.values(state.cards)
    .filter((c) => c.schedule.state !== 'NEW' && isDue(c.schedule, now))
    .sort((a, b) => a.schedule.dueAt.localeCompare(b.schedule.dueAt));
}

// ---------- quiz ----------

export function startQuiz(attempt: QuizAttempt): void {
  setState((s) => ({ ...s, quizAttempts: [...s.quizAttempts, attempt] }));
}

export function updateQuiz(attempt: QuizAttempt): void {
  setState((s) => ({
    ...s,
    quizAttempts: s.quizAttempts.map((a) => (a.id === attempt.id ? attempt : a)),
  }));
}

export function finishQuiz(attempt: QuizAttempt): void {
  setState((s) => {
    const wrong = { ...s.wrongQuestions };
    for (const a of attempt.answers) {
      if (!a.correct) wrong[a.questionId] = (wrong[a.questionId] ?? 0) + 1;
    }
    return {
      ...s,
      quizAttempts: s.quizAttempts.map((x) => (x.id === attempt.id ? attempt : x)),
      wrongQuestions: wrong,
    };
  });
}

export function toggleSavedQuestion(questionId: string): void {
  setState((s) => ({
    ...s,
    savedQuestions: s.savedQuestions.includes(questionId)
      ? s.savedQuestions.filter((q) => q !== questionId)
      : [...s.savedQuestions, questionId],
  }));
}

// ---------- sessions / habits / diary ----------

export function logSession(session: StudySession): void {
  setState((s) => ({ ...s, sessions: [...s.sessions, session] }));
}

export function addHabit(habit: Habit): void {
  setState((s) => ({ ...s, habits: [...s.habits, habit] }));
}

export function removeHabit(id: string): void {
  setState((s) => ({ ...s, habits: s.habits.filter((h) => h.id !== id) }));
}

export function logHabit(habitId: string, date: string, amount: number): void {
  setState((s) => ({
    ...s,
    habitLogs: { ...s.habitLogs, [`${habitId}|${date}`]: amount },
  }));
}

export function addDiary(entry: DiaryEntry): void {
  setState((s) => ({ ...s, diary: [entry, ...s.diary] }));
}

export function removeDiary(id: string): void {
  setState((s) => ({ ...s, diary: s.diary.filter((d) => d.id !== id) }));
}

// ---------- plan & progress ----------

export function savePlan(plan: StudyPlan): void {
  setState((s) => ({ ...s, plan }));
}

export function completePlanItem(date: string, itemId: string, completed: boolean): void {
  setState((s) => {
    if (!s.plan) return s;
    const days: PlanDay[] = s.plan.days.map((d) =>
      d.date === date
        ? { ...d, items: d.items.map((i) => (i.id === itemId ? { ...i, completed } : i)) }
        : d,
    );
    return { ...s, plan: { ...s.plan, days } };
  });
}

export function setLessonProgress(lessonId: string, patch: Partial<LessonProgress>): void {
  setState((s) => {
    const prev: LessonProgress = s.lessonProgress[lessonId] ?? {
      lastSection: 0,
      percent: 0,
      bookmarked: false,
      completedAt: null,
    };
    return {
      ...s,
      lessonProgress: { ...s.lessonProgress, [lessonId]: { ...prev, ...patch } },
    };
  });
}

export function addHighlight(h: HighlightEntry): void {
  setState((s) => ({ ...s, highlights: [h, ...s.highlights] }));
}

export function removeHighlight(id: string): void {
  setState((s) => ({ ...s, highlights: s.highlights.filter((x) => x.id !== id) }));
}

// ---------- PDFs ----------

export function addPdf(meta: PdfDocMeta): void {
  setState((s) => ({ ...s, pdfs: [...s.pdfs, meta] }));
}

export function removePdf(id: string): void {
  setState((s) => ({
    ...s,
    pdfs: s.pdfs.filter((p) => p.id !== id),
    pdfAnnotations: s.pdfAnnotations.filter((a) => a.pdfId !== id),
  }));
}

export function setPdfLastPage(id: string, page: number): void {
  setState((s) => ({
    ...s,
    pdfs: s.pdfs.map((p) => (p.id === id ? { ...p, lastPage: page } : p)),
  }));
}

export function addPdfAnnotation(a: PdfAnnotation): void {
  setState((s) => ({ ...s, pdfAnnotations: [...s.pdfAnnotations, a] }));
}

export function removePdfAnnotation(id: string): void {
  setState((s) => ({ ...s, pdfAnnotations: s.pdfAnnotations.filter((a) => a.id !== id) }));
}

// ---------- focus, glossary, reports, admin ----------

export function addFocusSession(fs: FocusSession): void {
  setState((s) => ({ ...s, focusSessions: [...s.focusSessions, fs] }));
}

export function addGlossaryTerm(term: GlossaryTermSeed): void {
  setState((s) => ({ ...s, glossary: [...s.glossary, { ...term, custom: true }] }));
}

export function removeGlossaryTerm(id: string): void {
  setState((s) => ({ ...s, glossary: s.glossary.filter((g) => g.id !== id || !g.custom) }));
}

export function addReport(report: ContentIssueReport): void {
  setState((s) => ({ ...s, reports: [...s.reports, report] }));
}

export function adminAudit(action: string, target: string): void {
  setState((s) => ({
    ...s,
    admin: {
      ...s.admin,
      audit: [{ at: new Date().toISOString(), action, target, by: 'local-admin' }, ...s.admin.audit].slice(0, 200),
    },
  }));
}

export function setAdminOverride(kind: 'lessons' | 'questions', id: string, value: unknown): void {
  setState((s) => ({
    ...s,
    admin: { ...s.admin, [kind]: { ...s.admin[kind], [id]: value } },
  }));
}

export function setEditorialStatus(id: string, status: string): void {
  setState((s) => ({
    ...s,
    admin: {
      ...s.admin,
      statuses: { ...s.admin.statuses, [id]: status },
      audit: [{ at: new Date().toISOString(), action: `status:${status}`, target: id, by: 'local-admin' }, ...s.admin.audit].slice(0, 200),
    },
  }));
}

// ---------- analytics helpers ----------

export function todayKey(d = new Date()): string {
  return d.toISOString().slice(0, 10);
}

export function streakDays(s: AppState = state): number {
  const days = new Set(s.sessions.map((x) => x.startedAt.slice(0, 10)));
  const diaryDays = new Set(s.diary.map((d) => d.date));
  const all = new Set([...days, ...diaryDays]);
  let streak = 0;
  const cursor = new Date();
  // today may still be in progress; a missing today does not break the streak
  if (!all.has(todayKey(cursor))) cursor.setDate(cursor.getDate() - 1);
  while (all.has(todayKey(cursor))) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

export function minutesOn(dateKey: string, s: AppState = state): number {
  return s.sessions
    .filter((x) => x.startedAt.slice(0, 10) === dateKey)
    .reduce((sum, x) => sum + x.minutes, 0);
}

export function minutesToday(s: AppState = state): number {
  return minutesOn(todayKey(), s);
}

export function totalMinutes(s: AppState = state): number {
  return s.sessions.reduce((sum, x) => sum + x.minutes, 0);
}

export function questionsAnsweredToday(s: AppState = state): number {
  const key = todayKey();
  return s.quizAttempts
    .filter((a) => (a.finishedAt ?? a.startedAt).slice(0, 10) === key)
    .reduce((sum, a) => sum + a.answers.length, 0);
}

export function recentAccuracy(s: AppState = state): number | null {
  const finished = s.quizAttempts.filter((a) => a.finished && a.answers.length > 0).slice(-5);
  if (finished.length === 0) return null;
  const correct = finished.flatMap((a) => a.answers).filter((x) => x.correct).length;
  const total = finished.flatMap((a) => a.answers).length;
  return total === 0 ? null : correct / total;
}

export function subjectAccuracy(s: AppState = state): { subjectId: string; accuracy: number; total: number }[] {
  const map = new Map<string, { correct: number; total: number }>();
  for (const attempt of s.quizAttempts.filter((a) => a.finished)) {
    for (const ans of attempt.answers) {
      const q = questionBank.find((x) => x.id === ans.questionId);
      if (!q) continue;
      const e = map.get(q.subjectId) ?? { correct: 0, total: 0 };
      e.total += 1;
      if (ans.correct) e.correct += 1;
      map.set(q.subjectId, e);
    }
  }
  return [...map.entries()]
    .map(([subjectId, v]) => ({ subjectId, accuracy: v.total ? v.correct / v.total : 0, total: v.total }))
    .sort((a, b) => a.accuracy - b.accuracy);
}

export function weakestSubject(s: AppState = state): string | null {
  const acc = subjectAccuracy(s).filter((x) => x.total >= 5);
  return acc.length ? acc[0].subjectId : null;
}

export function dueCardList(s: AppState = state, now = new Date()): UserCard[] {
  return Object.values(s.cards)
    .filter((c) => c.schedule.state !== 'NEW' && isDue(c.schedule, now))
    .sort((a, b) => a.schedule.dueAt.localeCompare(b.schedule.dueAt));
}

export function newCardList(s: AppState = state): UserCard[] {
  return Object.values(s.cards).filter((c) => c.schedule.state === 'NEW');
}
