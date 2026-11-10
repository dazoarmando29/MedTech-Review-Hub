// Study plan generator. Mirrors backend PlanGenerator and Android StudyPlanGenerator:
// weak subjects get 1.5x time, subject weights depend on the exam profile.
import { SUBJECT_WEIGHTS_ASCPI, SUBJECT_WEIGHTS_PH, subjects, lessonsForSubject } from './content';
import type { PlanDay, PlanItem, Profile, StudyPlan } from './types';

const PLAN_DAYS = 28;
const KIND_BY_PREF: PlanItem['type'][] = ['READ', 'FLASHCARDS', 'QUIZ', 'REVIEW_WRONG'];

function weightsFor(profile: Profile): Record<string, number> {
  const base =
    profile.examTarget === 'PH_BOARD' ? { ...SUBJECT_WEIGHTS_PH } : { ...SUBJECT_WEIGHTS_ASCPI };
  // renormalize to sum to 1 (weak-subject boost is applied to minutes, not weights)
  const total = Object.values(base).reduce((a, b) => a + b, 0);
  for (const k of Object.keys(base)) base[k] = base[k] / total;
  void profile;
  return base;
}

export function generatePlan(profile: Profile, now = new Date()): StudyPlan {
  const weights = weightsFor(profile);
  const rankedSubjects = subjects
    .map((s) => ({ id: s.id, w: weights[s.id] ?? 0.05 }))
    .sort((a, b) => b.w - a.w);

  const days: PlanDay[] = [];
  for (let d = 0; d < PLAN_DAYS; d++) {
    const date = new Date(now);
    date.setDate(date.getDate() + d);
    const dateKey = date.toISOString().slice(0, 10);
    const weekday = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'][date.getDay()];
    const windows = profile.preferredSchedule?.[weekday] ?? [];
    const dayMinutes =
      windows.reduce((sum, [a, b]) => sum + Math.max(0, b - a), 0) || profile.minutesPerDay;

    // Rotate through subjects by weight; prioritize weak subjects in the first week.
    const items: PlanItem[] = [];
    const dailyBudget = Math.max(15, Math.round(dayMinutes));
    const slots = dailyBudget >= 90 ? 4 : dailyBudget >= 50 ? 3 : 2;

    for (let i = 0; i < slots; i++) {
      const subject = rankedSubjects[(d * slots + i) % rankedSubjects.length];
      const type = KIND_BY_PREF[(d + i) % KIND_BY_PREF.length];
      const subjectLessons = lessonsForSubject(subject.id);
      const lesson = subjectLessons.length
        ? subjectLessons[(d + i) % subjectLessons.length]
        : null;
      // Minutes scale with the subject's outline weight; weak subjects get 1.5x.
      const relativeWeight = subject.w * subjects.length;
      const boost = profile.weakestSubjects.includes(subject.id) ? 1.5 : 1;
      items.push({
        id: `plan-${dateKey}-${i}`,
        type,
        subjectId: subject.id,
        topicId: lesson?.topicId ?? null,
        lessonId: type === 'READ' ? (lesson?.id ?? null) : null,
        targetMinutes: Math.max(10, Math.round((dailyBudget / slots) * relativeWeight * boost)),
        completed: false,
      });
    }
    days.push({ date: dateKey, items });
  }

  return {
    generatedAt: now.toISOString(),
    examTarget: profile.examTarget,
    days,
  };
}

export function examCountdownDays(profile: Profile | null, now = new Date()): number | null {
  if (!profile?.targetExamDate) return null;
  const target = new Date(profile.targetExamDate + 'T00:00:00Z');
  return Math.max(0, Math.ceil((target.getTime() - now.getTime()) / 86400000));
}

export function planForDate(plan: StudyPlan | null, dateKey: string): PlanDay | null {
  return plan?.days.find((d) => d.date === dateKey) ?? null;
}
