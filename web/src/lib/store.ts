// Local-first application store. Persists to localStorage; optionally syncs to
// the MedTech backend (shared/api/API.md) when settings.apiBaseUrl is set.
'use client';

import { useRef, useSyncExternalStore } from 'react';
import { addGardenMinutes } from './cozy';
import { decks as seedDecks, glossarySeed, questionBank } from './content';
import { applyReview, isDue, newSchedule } from './fsrs';
import type {
  CatRoomState, ContentIssueReport, DiaryEntry, FlashcardSeed, FocusSession, GardenFlower,
  Habit, HighlightEntry, HighlightColor, LessonProgress, LoveNote, MusicPrefs, PdfAnnotation,
  PdfDocMeta, PlanDay, Profile, QuizAttempt, QuizQuestion, Rating, ReviewRecord, Settings,
  StudyPlan, StudySession, ThemeId, UserCard,
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
  garden: { flowers: GardenFlower[] };
  catRoom: CatRoomState;
  customQuestions: QuizQuestion[];
  music: {
    favorites: string[];
    playlists: { id: string; name: string; trackIds: string[] }[];
    downloads: string[];
    lastTrackId: string | null;
    lastPositionSec: number;
  };
  loveNotes: LoveNote[];
  quoteFavorites: string[];
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
  appTheme: 'strawberry',
  textScale: 100,
  seriousMode: false,
  easyMode: false,
  reduceMotion: false,
  lowBandwidth: false,
  funnyCommentary: true,
  mascot: 'cat',
  mascotName: 'Mochi',
  mascotCommentary: true,
  displayName: 'future MLS',
  giftNote: 'Made with love for a future Medical Laboratory Scientist — someone believes in you. 🌸',
  motivationNote: '',
  explanationStyle: 'FORMAL_ENGLISH',
  ttsEnabled: false,
  apiBaseUrl: 'https://medtech-review-hub-api.dazoarmando.workers.dev',
  dailyNewCards: 20,
  dailyReviewLimit: 200,
  targetRetention: 0.9,
  music: {
    defaultPlaylistId: 'pink-study',
    morningPlaylistId: 'pink-study',
    nightPlaylistId: 'midnight-review',
    volume: 0.6,
    autoplay: false,
    musicDuring: { quizzes: false, flashcards: true, reading: true, breaks: true },
    chime: true,
    soundEffects: true,
    quoteFrequency: 'daily',
    ambientAnimations: true,
  },
  highlightColors: {
    YELLOW: 'Important fact',
    PINK: 'Must memorize',
    PURPLE: 'Definition',
    BLUE: 'Clinical significance',
    GREEN: 'Already mastered',
    ROSE: 'Needs review',
  },
};

// v1.0.0 default meanings — replaced by v2 defaults on migration only when the
// user never customized them (custom labels are always preserved).
const V1_HIGHLIGHT_DEFAULTS = {
  YELLOW: 'Must memorize',
  RED: 'High-priority exam fact',
  BLUE: 'Definition',
  GREEN: 'Fully understood',
  PURPLE: 'Confusing / needs further study',
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

export function initialState(): AppState {
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
    garden: { flowers: [] },
    catRoom: { equipped: [] },
    customQuestions: [],
    music: { favorites: [], playlists: [], downloads: [], lastTrackId: null, lastPositionSec: 0 },
    loveNotes: [],
    quoteFavorites: [],
    lastSyncAt: null,
  };
}

/** Upgrades v1.0.0 persisted state to the v2 (pink) schema without data loss. */
export function migrateState(raw: Partial<AppState>): Partial<AppState> {
  const out: Partial<AppState> = { ...raw };
  const settings = { ...(raw.settings ?? {}) } as Partial<Settings> & Record<string, unknown>;

  // theme (v1: system|light|dark) -> appTheme (v2: theme id or 'system')
  if (settings.appTheme === undefined) {
    settings.appTheme =
      settings.theme === 'dark' ? 'night' : settings.theme === 'light' ? 'strawberry' : 'system';
  }
  // highlight color labels: upgrade untouched v1 defaults to v2 defaults
  const hc = settings.highlightColors as Record<string, string> | undefined;
  if (hc) {
    const isUntouchedV1 =
      hc.YELLOW === V1_HIGHLIGHT_DEFAULTS.YELLOW &&
      hc.BLUE === V1_HIGHLIGHT_DEFAULTS.BLUE &&
      hc.GREEN === V1_HIGHLIGHT_DEFAULTS.GREEN &&
      hc.PURPLE === V1_HIGHLIGHT_DEFAULTS.PURPLE;
    if (isUntouchedV1 || hc.RED !== undefined) {
      // legacy keys are remapped: RED annotation colors become PINK, PURPLE become ROSE
      // (see DESIGN.md v2 migration table); labels get the v2 defaults unless customized.
      settings.highlightColors = {
        YELLOW: hc.YELLOW === V1_HIGHLIGHT_DEFAULTS.YELLOW ? DEFAULT_SETTINGS.highlightColors.YELLOW : hc.YELLOW,
        PINK: hc.RED === V1_HIGHLIGHT_DEFAULTS.RED || hc.RED === undefined ? DEFAULT_SETTINGS.highlightColors.PINK : hc.RED,
        PURPLE: hc.BLUE === V1_HIGHLIGHT_DEFAULTS.BLUE ? DEFAULT_SETTINGS.highlightColors.PURPLE : hc.BLUE ?? hc.PURPLE ?? DEFAULT_SETTINGS.highlightColors.PURPLE,
        BLUE: DEFAULT_SETTINGS.highlightColors.BLUE,
        GREEN: hc.GREEN === V1_HIGHLIGHT_DEFAULTS.GREEN ? DEFAULT_SETTINGS.highlightColors.GREEN : hc.GREEN,
        ROSE: hc.PURPLE === V1_HIGHLIGHT_DEFAULTS.PURPLE || hc.PURPLE === undefined ? DEFAULT_SETTINGS.highlightColors.ROSE : hc.PURPLE,
      } as Settings['highlightColors'];
    }
  }
  out.settings = settings as Settings;

  // legacy annotation/highlight color keys -> v2 keys
  const mapLegacy = (c: string): HighlightColor =>
    c === 'RED' ? 'PINK' : c === 'PURPLE' ? 'ROSE' : (c as HighlightColor);
  if (raw.pdfAnnotations) {
    out.pdfAnnotations = raw.pdfAnnotations.map((a) => ({ ...a, color: mapLegacy(a.color) }));
  }
  if (raw.highlights) {
    out.highlights = raw.highlights.map((h) => ({ ...h, color: mapLegacy(h.color) }));
  }
  if (!raw.garden) out.garden = { flowers: [] };
  if (!raw.catRoom) out.catRoom = { equipped: [] };
  if (!raw.customQuestions) out.customQuestions = [];
  if (!raw.music) out.music = { favorites: [], playlists: [], downloads: [], lastTrackId: null, lastPositionSec: 0 };
  if (!raw.loveNotes) out.loveNotes = [];
  if (!raw.quoteFavorites) out.quoteFavorites = [];
  if (out.settings && !out.settings.music) {
    out.settings.music = DEFAULT_SETTINGS.music;
  }
  return out;
}

function load(): AppState {
  if (typeof window === 'undefined') return initialState();
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return initialState();
    const parsed = migrateState(JSON.parse(raw) as Partial<AppState>);
    const base = initialState();
    const merged: AppState = {
      ...base,
      ...parsed,
      settings: { ...base.settings, ...(parsed.settings ?? {}) },
      cards: { ...base.cards, ...(parsed.cards ?? {}) },
      admin: { ...base.admin, ...(parsed.admin ?? {}) },
      garden: parsed.garden ?? base.garden,
      catRoom: parsed.catRoom ?? base.catRoom,
    };
    return merged;
  } catch {
    return initialState();
  }
}

let state: AppState = load();
// Deterministic snapshot used for SSR/static-export parity during hydration.
const serverState: AppState = initialState();
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

function subscribe(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}

/**
 * React binding for the store. Subscribes to the whole state (stable reference)
 * and memoizes the selected value per state identity, so selectors may safely
 * return derived arrays/objects without tripping useSyncExternalStore's
 * cached-snapshot requirement.
 */
export function useStore<T>(selector: (s: AppState) => T): T {
  const snapshot = useSyncExternalStore(subscribe, getState, () => serverState);
  const cache = useRef<{ source: AppState; value: T } | null>(null);
  if (cache.current === null || cache.current.source !== snapshot) {
    cache.current = { source: snapshot, value: selector(snapshot) };
  }
  return cache.current.value;
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

/** Saves AI/PDF-generated questions so they can be practiced again like bank questions. */
export function addCustomQuestions(questions: QuizQuestion[]): void {
  setState((s) => {
    const existing = new Set(s.customQuestions.map((q) => q.id));
    const fresh = questions.filter((q) => !existing.has(q.id));
    return { ...s, customQuestions: [...s.customQuestions, ...fresh] };
  });
}

// ---------- sessions / habits / diary ----------

export function logSession(session: StudySession): void {
  setState((s) => ({
    ...s,
    sessions: [...s.sessions, session],
    // every completed study session helps the Study Garden grow
    garden: { flowers: addGardenMinutes(s.garden.flowers, session.minutes) },
  }));
}

export function equipCatItem(id: string): void {
  setState((s) => {
    const equipped = s.catRoom.equipped.includes(id)
      ? s.catRoom.equipped.filter((x) => x !== id)
      : [...s.catRoom.equipped, id];
    return { ...s, catRoom: { equipped } };
  });
}

// ---------- study music ----------

export function toggleFavoriteTrack(trackId: string): void {
  setState((s) => {
    const favorites = s.music.favorites.includes(trackId)
      ? s.music.favorites.filter((x) => x !== trackId)
      : [...s.music.favorites, trackId];
    return { ...s, music: { ...s.music, favorites } };
  });
}

export function toggleDownload(trackId: string): void {
  setState((s) => {
    const downloads = s.music.downloads.includes(trackId)
      ? s.music.downloads.filter((x) => x !== trackId)
      : [...s.music.downloads, trackId];
    return { ...s, music: { ...s.music, downloads } };
  });
}

export function savePlaylist(playlist: { id: string; name: string; trackIds: string[] }): void {
  setState((s) => {
    const exists = s.music.playlists.some((p) => p.id === playlist.id);
    return {
      ...s,
      music: {
        ...s.music,
        playlists: exists
          ? s.music.playlists.map((p) => (p.id === playlist.id ? playlist : p))
          : [...s.music.playlists, playlist],
      },
    };
  });
}

export function removePlaylist(id: string): void {
  setState((s) => ({ ...s, music: { ...s.music, playlists: s.music.playlists.filter((p) => p.id !== id) } }));
}

export function setLastPlayed(trackId: string | null, positionSec: number): void {
  setState((s) => ({ ...s, music: { ...s.music, lastTrackId: trackId, lastPositionSec: positionSec } }));
}

// ---------- little notes & quotes ----------

export function upsertLoveNote(note: LoveNote): void {
  setState((s) => {
    const exists = s.loveNotes.some((n) => n.id === note.id);
    return {
      ...s,
      loveNotes: exists ? s.loveNotes.map((n) => (n.id === note.id ? note : n)) : [note, ...s.loveNotes],
    };
  });
}

export function removeLoveNote(id: string): void {
  setState((s) => ({ ...s, loveNotes: s.loveNotes.filter((n) => n.id !== id) }));
}

export function toggleQuoteFavorite(text: string): void {
  setState((s) => ({
    ...s,
    quoteFavorites: s.quoteFavorites.includes(text)
      ? s.quoteFavorites.filter((t) => t !== text)
      : [text, ...s.quoteFavorites],
  }));
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
