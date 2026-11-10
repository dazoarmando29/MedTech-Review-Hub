// Cozy feature logic: Study Garden growth, Cat Room unlocks, motivational
// rotation, and Weekly Wrapped computations. Pure functions (unit-tested).
import { getQuestion } from './content';
import type { AppState } from './store';
import type { GardenFlower } from './types';

export const FLOWER_KINDS: GardenFlower['kind'][] = ['tulip', 'daisy', 'sakura', 'lavender'];

/** Minutes of focused study needed for each growth stage. */
export const STAGES = { seed: 0, sprout: 15, bud: 40, bloom: 75 } as const;

export type FlowerStage = 'seed' | 'sprout' | 'bud' | 'bloom';

export function flowerStage(flower: Pick<GardenFlower, 'minutes'>): FlowerStage {
  if (flower.minutes >= STAGES.bloom) return 'bloom';
  if (flower.minutes >= STAGES.bud) return 'bud';
  if (flower.minutes >= STAGES.sprout) return 'sprout';
  return 'seed';
}

/**
 * Adds study minutes to the garden: the youngest non-bloomed flower grows;
 * when every flower has bloomed (or the garden is empty), a new seed is planted.
 */
export function addGardenMinutes(
  flowers: GardenFlower[],
  minutes: number,
  now: Date = new Date(),
): GardenFlower[] {
  if (minutes <= 0) return flowers;
  const next = flowers.map((f) => ({ ...f }));
  const growing = next.filter((f) => flowerStage(f) !== 'bloom');
  if (growing.length === 0) {
    next.push({
      id: `fl-${now.getTime()}-${Math.random().toString(36).slice(2, 6)}`,
      kind: FLOWER_KINDS[next.length % FLOWER_KINDS.length],
      minutes,
      plantedAt: now.toISOString(),
      bloomedAt: null,
    });
    return next;
  }
  // grow the youngest (latest planted) growing flower
  const target = growing[growing.length - 1];
  target.minutes += minutes;
  if (flowerStage(target) === 'bloom' && !target.bloomedAt) {
    target.bloomedAt = now.toISOString();
  }
  return next;
}

export interface CatRoomItem {
  id: string;
  name: string;
  description: string;
  need: string;
  unlocked: boolean;
  equipped: boolean;
}

const CAT_ROOM_ITEMS: { id: string; name: string; description: string; need: string; meets: (s: { sessions: number; streak: number; blooms: number }) => boolean }[] = [
  { id: 'bow', name: 'Pink Bow', description: 'A tiny bow for Mochi\u2019s ear.', need: 'Complete 1 study session', meets: (s) => s.sessions >= 1 },
  { id: 'milk', name: 'Strawberry Milk', description: 'Mochi\u2019s favorite study drink.', need: 'Complete 3 study sessions', meets: (s) => s.sessions >= 3 },
  { id: 'glasses', name: 'Study Glasses', description: 'For serious reading time.', need: 'Reach a 3-day streak', meets: (s) => s.streak >= 3 },
  { id: 'starlamp', name: 'Star Lamp', description: 'A cozy glow for late reviews.', need: 'Bloom 3 flowers in the Study Garden', meets: (s) => s.blooms >= 3 },
  { id: 'books', name: 'Book Stack', description: 'Mochi\u2019s favorite nap spot.', need: 'Complete 15 study sessions', meets: (s) => s.sessions >= 15 },
  { id: 'flower-crown', name: 'Flower Crown', description: 'For a top student\u2019s best friend.', need: 'Bloom 10 flowers', meets: (s) => s.blooms >= 10 },
  { id: 'grad-cap', name: 'Mini Graduation Cap', description: 'Future MLS energy.', need: 'Reach a 14-day streak', meets: (s) => s.streak >= 14 },
];

export function catRoomItems(stats: { sessions: number; streak: number; blooms: number }, equipped: string[]): CatRoomItem[] {
  return CAT_ROOM_ITEMS.map((i) => ({
    id: i.id,
    name: i.name,
    description: i.description,
    need: i.need,
    unlocked: i.meets(stats),
    equipped: equipped.includes(i.id),
  }));
}

export const MOTIVATION_LINES = [
  'Your future self is cheering for you!',
  'One chapter at a time, future MLS.',
  'You remembered that formula! Your brain deserves a little celebration.',
  'Small progress is still progress.',
  'Every flashcard is one step closer to that ASCPi dream.',
  'Rest is part of the plan — come back when you\u2019re ready.',
  'Your dedication would make any lab director proud.',
  'Kaya mo \u2019to, future MLS! 🌸',
];

/** Deterministic daily rotation (same line all day, new line tomorrow). */
export function pickMotivation(dayIndex: number = Math.floor(Date.now() / 86400000)): string {
  return MOTIVATION_LINES[Math.abs(dayIndex) % MOTIVATION_LINES.length];
}

export interface WeeklyWrapped {
  minutes: number;
  cardsReviewed: number;
  quizzesFinished: number;
  accuracy: number | null;
  bestSubject: string | null;
  streak: number;
  flowersBloomed: number;
  topCompliment: string;
}

export function weeklyWrapped(state: AppState, now: Date = new Date()): WeeklyWrapped {
  const weekAgo = now.getTime() - 7 * 86400000;
  const sessions = state.sessions.filter((s) => new Date(s.startedAt).getTime() >= weekAgo);
  const minutes = sessions.reduce((sum, s) => sum + s.minutes, 0);
  const cardsReviewed = Object.values(state.cards).reduce(
    (sum, c) => sum + c.history.filter((h) => new Date(h.at).getTime() >= weekAgo).length,
    0,
  );
  const attempts = state.quizAttempts.filter(
    (a) => a.finished && new Date(a.finishedAt ?? a.startedAt).getTime() >= weekAgo,
  );
  const answers = attempts.flatMap((a) => a.answers);
  const correct = answers.filter((a) => a.correct).length;

  const bySubject = new Map<string, { correct: number; total: number }>();
  for (const a of answers) {
    const q = questionSubjectOf(state, a.questionId);
    const e = bySubject.get(q) ?? { correct: 0, total: 0 };
    e.total += 1;
    if (a.correct) e.correct += 1;
    bySubject.set(q, e);
  }
  let bestSubject: string | null = null;
  let bestAcc = -1;
  for (const [subjectId, v] of bySubject) {
    const acc = v.total ? v.correct / v.total : 0;
    if (v.total >= 3 && acc > bestAcc) {
      bestAcc = acc;
      bestSubject = subjectId;
    }
  }

  const blooms = state.garden.flowers.filter((f) => f.bloomedAt).length;
  const streak = computeStreak(state, now);
  const topCompliment =
    minutes >= 300 ? 'Incredible week — you ate that syllabus! 🌸'
    : minutes >= 120 ? 'A beautiful, consistent week. Your cat is proud of you.'
    : minutes >= 30 ? 'Every minute counts, and you showed up. Lovely.'
    : 'A gentle week — rest is part of the plan. Come back when ready.';

  return {
    minutes,
    cardsReviewed,
    quizzesFinished: attempts.length,
    accuracy: answers.length ? correct / answers.length : null,
    bestSubject,
    streak,
    flowersBloomed: blooms,
    topCompliment,
  };
}

function questionSubjectOf(_state: AppState, questionId: string): string {
  return getQuestion(questionId)?.subjectId ?? 'mixed';
}

/** Consecutive study days ending today (or yesterday if today is still in progress). */
export function computeStreak(state: AppState, now: Date = new Date()): number {
  const days = new Set<string>([
    ...state.sessions.map((s) => s.startedAt.slice(0, 10)),
    ...state.diary.map((d) => d.date),
  ]);
  const cursor = new Date(now);
  const key = (d: Date) => d.toISOString().slice(0, 10);
  if (!days.has(key(cursor))) cursor.setUTCDate(cursor.getUTCDate() - 1);
  let streak = 0;
  while (days.has(key(cursor))) {
    streak += 1;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return streak;
}
