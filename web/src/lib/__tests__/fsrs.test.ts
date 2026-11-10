import { describe, expect, it } from 'vitest';
import {
  DEFAULT_RETENTION, FACTOR, FSRS_W, applyReview, formatInterval, initDifficulty,
  initStability, intervalDays, isDue, newSchedule, nextDifficulty, nextStability,
  previewIntervals, retrievability,
} from '../fsrs';

describe('FSRS-4.5 core formulas (shared/FSRS.md)', () => {
  it('FACTOR equals 19/81', () => {
    expect(FACTOR).toBeCloseTo(19 / 81, 10);
  });

  it('retrievability at t = S is 0.9', () => {
    expect(retrievability(3.7145, 3.7145)).toBeCloseTo(0.9, 6);
    expect(retrievability(10, 10)).toBeCloseTo(0.9, 6);
  });

  it('init stability maps ratings to W[0..3]', () => {
    expect(initStability('AGAIN')).toBeCloseTo(FSRS_W[0], 6);
    expect(initStability('HARD')).toBeCloseTo(FSRS_W[1], 6);
    expect(initStability('GOOD')).toBeCloseTo(FSRS_W[2], 6);
    expect(initStability('EASY')).toBeCloseTo(FSRS_W[3], 6);
  });

  it('init difficulty matches W4 - (G-3)*W5 and clamps to 1..10', () => {
    expect(initDifficulty('GOOD')).toBeCloseTo(FSRS_W[4], 6);
    expect(initDifficulty('EASY')).toBeCloseTo(FSRS_W[4] - FSRS_W[5], 6);
    expect(initDifficulty('AGAIN')).toBeLessThanOrEqual(10);
    expect(initDifficulty('EASY')).toBeGreaterThanOrEqual(1);
  });

  it('difficulty stays within 1..10 after many reviews', () => {
    let d = initDifficulty('GOOD');
    for (let i = 0; i < 50; i++) d = nextDifficulty(d, i % 2 ? 'AGAIN' : 'EASY');
    expect(d).toBeGreaterThanOrEqual(1);
    expect(d).toBeLessThanOrEqual(10);
  });

  it('recall grows stability, lapse shrinks it', () => {
    const s = 3.7145;
    const d = initDifficulty('GOOD');
    const r = retrievability(4, s);
    const grown = nextStability(d, s, 'GOOD', r);
    expect(grown).toBeGreaterThan(s);
    const failed = nextStability(d, s, 'AGAIN', r);
    expect(failed).toBeLessThan(s);
  });

  it('interval equals stability at default retention', () => {
    expect(intervalDays(3.7145, DEFAULT_RETENTION)).toBe(4); // rounded
    expect(intervalDays(10, DEFAULT_RETENTION)).toBe(10);
    expect(intervalDays(0.5, DEFAULT_RETENTION)).toBe(1); // min 1 day
  });
});

describe('applyReview scheduling flow', () => {
  it('EASY on a new card graduates immediately with stability W[3]', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const { schedule, intervalDays: iv } = applyReview(newSchedule(now), 'EASY', now);
    expect(schedule.state).toBe('REVIEW');
    expect(schedule.stability).toBeCloseTo(FSRS_W[3], 6);
    expect(iv).toBe(intervalDays(schedule.stability));
  });

  it('GOOD advances a learning step; second GOOD graduates with stability W[2]', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const step1 = applyReview(newSchedule(now), 'GOOD', now);
    expect(step1.schedule.state).toBe('LEARNING');
    const gapMs = new Date(step1.schedule.dueAt).getTime() - now.getTime();
    expect(gapMs).toBe(10 * 60 * 1000); // second step: 10 minutes

    const step2 = applyReview(step1.schedule, 'GOOD', new Date(now.getTime() + 11 * 60000));
    expect(step2.schedule.state).toBe('REVIEW');
    expect(step2.schedule.stability).toBeCloseTo(FSRS_W[2], 6);
    expect(Math.round(step2.intervalDays)).toBe(4);
  });

  it('learning steps: AGAIN restarts at 1 minute', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const { schedule } = applyReview(newSchedule(now), 'AGAIN', now);
    expect(schedule.state).toBe('LEARNING');
    const diffMs = new Date(schedule.dueAt).getTime() - now.getTime();
    expect(diffMs).toBe(60 * 1000);
    expect(schedule.reps).toBe(0);
  });

  it('EASY graduates with a longer interval than GOOD', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const easy = applyReview(newSchedule(now), 'EASY', now);
    const good = applyReview(
      applyReview(newSchedule(now), 'GOOD', now).schedule,
      'GOOD',
      new Date(now.getTime() + 11 * 60000),
    );
    expect(easy.intervalDays).toBeGreaterThan(good.intervalDays);
  });

  it('review lapse increments lapses and moves to RELEARNING', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const graduated = applyReview(newSchedule(now), 'EASY', now).schedule;
    const later = new Date('2026-01-10T00:00:00Z');
    const lapsed = applyReview(graduated, 'AGAIN', later).schedule;
    expect(lapsed.lapses).toBe(1);
    expect(lapsed.state).toBe('RELEARNING');
  });

  it('isDue respects dueAt', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const { schedule } = applyReview(newSchedule(now), 'EASY', now);
    expect(isDue(schedule, new Date('2026-01-02T00:00:00Z'))).toBe(false);
    expect(isDue(schedule, new Date('2026-02-01T00:00:00Z'))).toBe(true);
  });

  it('reviews less than 1 minute apart are ignored', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const first = applyReview(newSchedule(now), 'GOOD', now).schedule;
    const quick = applyReview(first, 'GOOD', new Date(now.getTime() + 30 * 1000));
    expect(quick.schedule).toEqual(first);
  });

  it('previewIntervals shows a future string for every rating', () => {
    const now = new Date('2026-01-01T00:00:00Z');
    const previews = previewIntervals(newSchedule(now), now);
    for (const k of ['AGAIN', 'HARD', 'GOOD', 'EASY'] as const) {
      expect(previews[k]).toMatch(/\d/);
    }
    expect(formatInterval(4)).toBe('4d');
  });
});
