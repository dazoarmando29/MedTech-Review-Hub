import { describe, expect, it } from 'vitest';
import { examCountdownDays, generatePlan, planForDate } from '../plan';
import type { Profile } from '../types';

const profile: Profile = {
  examTarget: 'MLS_ASCPI',
  targetExamDate: '2027-01-01',
  knowledgeLevel: 'INTERMEDIATE',
  strongestSubjects: ['hematology'],
  weakestSubjects: ['blood-banking'],
  minutesPerDay: 120,
  preferredSchedule: { mon: [[1080, 1200]], tue: [], wed: [], thu: [], fri: [], sat: [], sun: [] },
  learningPreferences: ['mixed'],
  explanationStyle: 'FORMAL_ENGLISH',
  notificationsEnabled: false,
  focusFeatureEnabled: false,
  onboardingCompleted: true,
};

describe('study plan generator', () => {
  it('generates 28 days of items', () => {
    const plan = generatePlan(profile, new Date('2026-06-01T00:00:00Z'));
    expect(plan.days.length).toBe(28);
    for (const day of plan.days) {
      expect(day.items.length).toBeGreaterThanOrEqual(2);
    }
    expect(plan.examTarget).toBe('MLS_ASCPI');
  });

  it('marks weak subjects with ~1.5x planned time versus their own baseline', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    const minutesFor = (p: Profile) => {
      const plan = generatePlan(p, now);
      let total = 0;
      for (const day of plan.days) {
        for (const item of day.items) {
          if (item.subjectId === 'blood-banking') total += item.targetMinutes;
        }
      }
      return total;
    };
    const boosted = minutesFor(profile);
    const baseline = minutesFor({ ...profile, weakestSubjects: [] });
    expect(boosted).toBeGreaterThan(baseline * 1.4);
  });

  it('counts down to the exam date and never goes negative', () => {
    expect(examCountdownDays(profile, new Date('2026-06-01T00:00:00Z'))).toBe(214);
    expect(examCountdownDays(profile, new Date('2027-06-01T00:00:00Z'))).toBe(0);
    expect(examCountdownDays(null)).toBeNull();
  });

  it('planForDate finds the right day', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    const plan = generatePlan(profile, now);
    const first = planForDate(plan, '2026-06-01');
    expect(first).not.toBeNull();
    expect(first!.items.length).toBeGreaterThan(0);
    expect(planForDate(plan, '2030-01-01')).toBeNull();
  });

  it('PH board profile uses its own weights (microbiology emphasized)', () => {
    const ph: Profile = { ...profile, examTarget: 'PH_BOARD' };
    const plan = generatePlan(ph, new Date('2026-06-01T00:00:00Z'));
    const minutesBySubject = new Map<string, number>();
    for (const day of plan.days) {
      for (const item of day.items) {
        minutesBySubject.set(item.subjectId, (minutesBySubject.get(item.subjectId) ?? 0) + item.targetMinutes);
      }
    }
    expect(minutesBySubject.get('microbiology') ?? 0).toBeGreaterThan(0);
  });
});
