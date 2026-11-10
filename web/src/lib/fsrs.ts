// FSRS-4.5 spaced repetition scheduler.
// MUST stay in sync with shared/FSRS.md and
// android/app/src/main/java/com/medtech/reviewhub/domain/SpacedRepetition.kt
import type { Rating, SrsSchedule, SrsState } from './types';

export const DECAY = -0.5;
export const FACTOR = Math.pow(0.9, 1 / DECAY) - 1; // 19/81
export const DEFAULT_RETENTION = 0.9;

export const FSRS_W = [
  0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
  1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
];

const RATING_VALUE: Record<Rating, number> = { AGAIN: 1, HARD: 2, GOOD: 3, EASY: 4 };

export function clamp(v: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, v));
}

export function retrievability(elapsedDays: number, stability: number): number {
  const s = Math.max(stability, 0.1);
  return Math.pow(1 + (FACTOR * elapsedDays) / s, DECAY);
}

export function initStability(rating: Rating): number {
  return Math.max(FSRS_W[RATING_VALUE[rating] - 1], 0.1);
}

export function initDifficulty(rating: Rating): number {
  const g = RATING_VALUE[rating];
  return clamp(FSRS_W[4] - (g - 3) * FSRS_W[5], 1, 10);
}

export function nextDifficulty(prevDifficulty: number, rating: Rating): number {
  const g = RATING_VALUE[rating];
  const dPrime = prevDifficulty - FSRS_W[6] * (g - 3) * (10 - prevDifficulty) / 9;
  return clamp(FSRS_W[7] * initDifficulty('EASY') + (1 - FSRS_W[7]) * dPrime, 1, 10);
}

export function nextStability(
  difficulty: number,
  stability: number,
  rating: Rating,
  r: number,
): number {
  const s = Math.max(stability, 0.1);
  const d = clamp(difficulty, 1, 10);
  if (rating === 'AGAIN') {
    const failed =
      FSRS_W[11] * Math.pow(d, -FSRS_W[12]) * (Math.pow(s + 1, FSRS_W[13]) - 1) *
      Math.exp(FSRS_W[14] * (1 - r));
    return clamp(failed, 0.1, s);
  }
  const hard = rating === 'HARD' ? FSRS_W[15] : 1;
  const easy = rating === 'EASY' ? FSRS_W[16] : 1;
  const grown =
    s *
    (1 +
      Math.exp(FSRS_W[8]) *
        (11 - d) *
        Math.pow(s, -FSRS_W[9]) *
        (Math.exp(FSRS_W[10] * (1 - r)) - 1) *
        hard *
        easy);
  return clamp(grown, s, 36500);
}

export function intervalDays(stability: number, retention = DEFAULT_RETENTION): number {
  const i = (stability / FACTOR) * (Math.pow(retention, 1 / DECAY) - 1);
  return clamp(Math.round(i), 1, 36500);
}

const LEARNING_STEPS_MIN = [1, 10];
const RELEARNING_STEPS_MIN = [10];
const MIN_REVIEW_GAP_MS = 60_000;

export function newSchedule(now: Date = new Date()): SrsSchedule {
  return {
    state: 'NEW',
    stability: 0,
    difficulty: 0,
    dueAt: now.toISOString(),
    lastReviewAt: null,
    reps: 0,
    lapses: 0,
  };
}

/**
 * Applies a review rating to a card schedule, returning the updated schedule
 * and the interval in days until the next review.
 *
 * Learning flow (shared/FSRS.md): NEW enters LEARNING with steps [1 min, 10 min].
 * AGAIN restarts at step 1; HARD repeats the current step × 1.2; GOOD advances a
 * step (graduates after the last step); EASY graduates immediately. Reviews less
 * than 1 minute apart are ignored. A graduated RELEARNING card keeps the
 * stability produced by the lapse formula.
 */
export function applyReview(
  schedule: SrsSchedule,
  rating: Rating,
  now: Date = new Date(),
  retention: number = DEFAULT_RETENTION,
): { schedule: SrsSchedule; intervalDays: number } {
  const state: SrsState = schedule.state;

  if (schedule.lastReviewAt !== null && now.getTime() - new Date(schedule.lastReviewAt).getTime() < MIN_REVIEW_GAP_MS) {
    return { schedule, intervalDays: 0 };
  }

  const elapsed =
    schedule.lastReviewAt === null
      ? 0
      : (now.getTime() - new Date(schedule.lastReviewAt).getTime()) / 86400000;

  // Learning / relearning step handling
  if (state === 'NEW' || state === 'LEARNING' || state === 'RELEARNING') {
    const relearning = state === 'RELEARNING';
    const steps = relearning ? RELEARNING_STEPS_MIN : LEARNING_STEPS_MIN;
    const stepIndex = Math.min(schedule.reps, steps.length - 1);
    const base: SrsSchedule = {
      ...schedule,
      state: relearning ? 'RELEARNING' : 'LEARNING',
      lastReviewAt: now.toISOString(),
    };

    const graduate = (gradStability: number): { schedule: SrsSchedule; intervalDays: number } => {
      const interval = intervalDays(gradStability, retention);
      return {
        schedule: {
          state: 'REVIEW',
          stability: gradStability,
          difficulty: schedule.difficulty || initDifficulty(rating),
          lastReviewAt: now.toISOString(),
          dueAt: new Date(now.getTime() + interval * 86400000).toISOString(),
          reps: schedule.reps + 1,
          lapses: schedule.lapses,
        },
        intervalDays: interval,
      };
    };

    if (rating === 'EASY') {
      return graduate(
        relearning ? Math.max(schedule.stability, 0.1) : initStability('EASY'),
      );
    }
    if (rating === 'AGAIN') {
      return {
        schedule: {
          ...base,
          reps: 0,
          dueAt: new Date(now.getTime() + steps[0] * 60000).toISOString(),
          difficulty: schedule.difficulty || initDifficulty(rating),
        },
        intervalDays: steps[0] / 1440,
      };
    }
    if (rating === 'HARD') {
      const minutes = steps[stepIndex] * 1.2;
      return {
        schedule: {
          ...base,
          reps: schedule.reps + 1,
          dueAt: new Date(now.getTime() + minutes * 60000).toISOString(),
          difficulty: schedule.difficulty || initDifficulty(rating),
        },
        intervalDays: minutes / 1440,
      };
    }
    // GOOD: advance a step; graduate after the last step
    if (relearning) {
      return graduate(Math.max(schedule.stability, 0.1));
    }
    if (stepIndex + 1 < steps.length) {
      const minutes = steps[stepIndex + 1];
      return {
        schedule: {
          ...base,
          reps: schedule.reps + 1,
          dueAt: new Date(now.getTime() + minutes * 60000).toISOString(),
          difficulty: schedule.difficulty || initDifficulty(rating),
        },
        intervalDays: minutes / 1440,
      };
    }
    return graduate(initStability('GOOD'));
  }

  // Review cards
  const r = retrievability(Math.max(elapsed, 0), schedule.stability);
  const difficulty = nextDifficulty(schedule.difficulty, rating);
  const stability = nextStability(schedule.difficulty, schedule.stability, rating, r);
  const lapses = rating === 'AGAIN' ? schedule.lapses + 1 : schedule.lapses;

  if (rating === 'AGAIN') {
    const minutes = RELEARNING_STEPS_MIN[0];
    return {
      schedule: {
        state: 'RELEARNING',
        stability,
        difficulty,
        lastReviewAt: now.toISOString(),
        dueAt: new Date(now.getTime() + minutes * 60000).toISOString(),
        reps: schedule.reps + 1,
        lapses,
      },
      intervalDays: minutes / 1440,
    };
  }

  const interval = intervalDays(stability, retention);
  return {
    schedule: {
      state: 'REVIEW',
      stability,
      difficulty,
      lastReviewAt: now.toISOString(),
      dueAt: new Date(now.getTime() + interval * 86400000).toISOString(),
      reps: schedule.reps + 1,
      lapses,
    },
    intervalDays: interval,
  };
}

export function isDue(schedule: SrsSchedule, now: Date = new Date()): boolean {
  return new Date(schedule.dueAt).getTime() <= now.getTime();
}

/** Next interval preview shown on the rating buttons (Again/Hard/Good/Easy). */
export function previewIntervals(
  schedule: SrsSchedule,
  now: Date = new Date(),
  retention: number = DEFAULT_RETENTION,
): Record<Rating, string> {
  const out = {} as Record<Rating, string>;
  (['AGAIN', 'HARD', 'GOOD', 'EASY'] as Rating[]).forEach((rating) => {
    const { intervalDays: d } = applyReview(schedule, rating, now, retention);
    out[rating] = formatInterval(d);
  });
  return out;
}

export function formatInterval(days: number): string {
  if (days < 1 / 24) return `${Math.max(1, Math.round(days * 1440))}m`;
  if (days < 1) return `${Math.round(days * 24)}h`;
  if (days < 30) return `${Math.round(days)}d`;
  return `${(days / 30).toFixed(1)}mo`;
}
