'use client';

// Personalized onboarding wizard → generates the study plan.
import { useRouter } from 'next/navigation';
import React, { useState } from 'react';
import { Card, SectionTitle, cx } from '@/components/ui';
import { subjects } from '@/lib/content';
import { generatePlan } from '@/lib/plan';
import { saveProfile, savePlan, useStore } from '@/lib/store';
import type { ExamTarget, ExplanationStyle, KnowledgeLevel, LearningPref, Profile } from '@/lib/types';

const STEPS = ['Goal', 'Schedule', 'Subjects', 'Preferences', 'Plan'];

const DEFAULT_SCHEDULE: Record<string, [number, number][]> = {
  mon: [[1080, 1320]],
  tue: [[1080, 1320]],
  wed: [[1080, 1320]],
  thu: [[1080, 1320]],
  fri: [[1080, 1260]],
  sat: [[600, 900]],
  sun: [],
};

export default function OnboardingPage() {
  const router = useRouter();
  const existing = useStore((s) => s.profile);
  const [step, setStep] = useState(0);
  const [profile, setProfile] = useState<Profile>(
    existing ?? {
      examTarget: 'MLS_ASCPI',
      targetExamDate: null,
      knowledgeLevel: 'INTERMEDIATE',
      strongestSubjects: [],
      weakestSubjects: [],
      minutesPerDay: 120,
      preferredSchedule: DEFAULT_SCHEDULE,
      learningPreferences: ['mixed'],
      explanationStyle: 'FORMAL_ENGLISH',
      notificationsEnabled: false,
      focusFeatureEnabled: false,
      onboardingCompleted: false,
    },
  );

  const patch = (p: Partial<Profile>) => setProfile((prev) => ({ ...prev, ...p }));

  const finish = () => {
    const done: Profile = { ...profile, onboardingCompleted: true };
    saveProfile(done);
    savePlan(generatePlan(done));
    router.push('/');
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Build your study plan</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          Step {step + 1} of {STEPS.length}: {STEPS[step]} — you can change any of this later.
        </p>
        <div className="flex gap-1 mt-3" role="progressbar" aria-valuenow={step + 1} aria-valuemin={1} aria-valuemax={STEPS.length}>
          {STEPS.map((s, i) => (
            <div key={s} className={cx('h-1.5 flex-1 rounded-full', i <= step ? 'bg-[var(--brand)]' : 'bg-slate-200 dark:bg-slate-700')} />
          ))}
        </div>
      </div>

      <Card>
        {step === 0 && (
          <div className="space-y-4">
            <SectionTitle icon="🎯">Exam goal</SectionTitle>
            <fieldset>
              <legend className="text-sm font-medium mb-2">Which exam are you preparing for?</legend>
              <div className="grid gap-2">
                {([
                  ['MLS_ASCPI', 'MLS(ASCPi) — Medical Laboratory Scientist, international'],
                  ['PH_BOARD', 'Philippine MedTech Licensure Examination (MTLE)'],
                  ['CUSTOM', 'Custom review (no specific exam blueprint)'],
                ] as [ExamTarget, string][]).map(([value, label]) => (
                  <label key={value} className={cx('flex items-center gap-3 rounded-md border p-3 cursor-pointer',
                    profile.examTarget === value ? 'border-[var(--brand)] bg-blue-50 dark:bg-blue-950/40' : 'border-slate-200 dark:border-slate-700')}>
                    <input type="radio" name="exam" checked={profile.examTarget === value} onChange={() => patch({ examTarget: value })} />
                    <span className="text-sm">{label}</span>
                  </label>
                ))}
              </div>
              <p className="text-xs text-[var(--ink-muted)] mt-2">
                ASCPi and PH board content profiles are kept separate — their blueprints are not identical.
              </p>
            </fieldset>

            <label className="block text-sm font-medium">
              Target exam date (optional)
              <input
                type="date"
                className="input mt-1"
                value={profile.targetExamDate ?? ''}
                onChange={(e) => patch({ targetExamDate: e.target.value || null })}
              />
            </label>

            <fieldset>
              <legend className="text-sm font-medium mb-2">Current knowledge level</legend>
              <div className="flex gap-2 flex-wrap">
                {(['BEGINNER', 'INTERMEDIATE', 'ADVANCED'] as KnowledgeLevel[]).map((lvl) => (
                  <button key={lvl} type="button"
                    className={cx('btn', profile.knowledgeLevel === lvl ? 'bg-[var(--brand)] text-white' : 'btn-ghost')}
                    onClick={() => patch({ knowledgeLevel: lvl })}>
                    {lvl.charAt(0) + lvl.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
            </fieldset>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <SectionTitle icon="⏰">Study time</SectionTitle>
            <label className="block text-sm font-medium">
              Study minutes per day: <strong>{profile.minutesPerDay}</strong>
              <input
                type="range" min={15} max={360} step={15} value={profile.minutesPerDay}
                onChange={(e) => patch({ minutesPerDay: Number(e.target.value) })}
                className="w-full mt-2"
              />
            </label>
            <fieldset>
              <legend className="text-sm font-medium mb-2">Preferred schedule (evening windows are prefilled)</legend>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {Object.keys(DEFAULT_SCHEDULE).map((day) => {
                  const active = (profile.preferredSchedule[day]?.length ?? 0) > 0;
                  return (
                    <button
                      key={day}
                      type="button"
                      onClick={() =>
                        patch({
                          preferredSchedule: {
                            ...profile.preferredSchedule,
                            [day]: active ? [] : DEFAULT_SCHEDULE[day],
                          },
                        })
                      }
                      className={cx('btn text-xs capitalize', active ? 'bg-[var(--brand-2)] text-white' : 'btn-ghost')}
                      aria-pressed={active}
                    >
                      {day}
                    </button>
                  );
                })}
              </div>
              <p className="text-xs text-[var(--ink-muted)] mt-2">
                Rest days are fine. Missing a day never triggers guilt trips here.
              </p>
            </fieldset>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4">
            <SectionTitle icon="📚">Subjects</SectionTitle>
            <p className="text-sm text-[var(--ink-muted)]">Pick at least one weakest subject — it gets 1.5x study time in your plan.</p>
            <div className="space-y-2">
              {subjects.map((s) => {
                const weak = profile.weakestSubjects.includes(s.id);
                const strong = profile.strongestSubjects.includes(s.id);
                return (
                  <div key={s.id} className="flex items-center gap-2 rounded-md border border-slate-200 dark:border-slate-700 p-3">
                    <div className="flex-1 text-sm font-medium">{s.name}</div>
                    <button
                      type="button"
                      className={cx('btn !py-1.5 !px-3 !min-h-[36px] text-xs', strong ? 'bg-emerald-600 text-white' : 'btn-ghost')}
                      aria-pressed={strong}
                      onClick={() =>
                        patch({
                          strongestSubjects: strong
                            ? profile.strongestSubjects.filter((x) => x !== s.id)
                            : [...profile.strongestSubjects, s.id],
                        })
                      }
                    >
                      Strong
                    </button>
                    <button
                      type="button"
                      className={cx('btn !py-1.5 !px-3 !min-h-[36px] text-xs', weak ? 'bg-red-600 text-white' : 'btn-ghost')}
                      aria-pressed={weak}
                      onClick={() =>
                        patch({
                          weakestSubjects: weak
                            ? profile.weakestSubjects.filter((x) => x !== s.id)
                            : [...profile.weakestSubjects, s.id],
                        })
                      }
                    >
                      Needs work
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <SectionTitle icon="🎨">Preferences</SectionTitle>
            <fieldset>
              <legend className="text-sm font-medium mb-2">Learning preferences</legend>
              <div className="flex gap-2 flex-wrap">
                {(['reading', 'flashcards', 'quizzes', 'diagrams', 'mixed'] as LearningPref[]).map((p) => {
                  const active = profile.learningPreferences.includes(p);
                  return (
                    <button key={p} type="button"
                      className={cx('btn capitalize', active ? 'bg-[var(--brand-3)] text-white' : 'btn-ghost')}
                      aria-pressed={active}
                      onClick={() =>
                        patch({
                          learningPreferences: active
                            ? profile.learningPreferences.filter((x) => x !== p)
                            : [...profile.learningPreferences, p],
                        })
                      }>
                      {p}
                    </button>
                  );
                })}
              </div>
            </fieldset>

            <fieldset>
              <legend className="text-sm font-medium mb-2">Explanation style</legend>
              <div className="grid gap-2">
                {([
                  ['FORMAL_ENGLISH', 'Formal English — exam-standard phrasing'],
                  ['SIMPLE_ENGLISH', 'Simple English — plain-language explanations'],
                  ['TAGLISH', 'Taglish — English science, Filipino-friendly notes'],
                ] as [ExplanationStyle, string][]).map(([v, label]) => (
                  <label key={v} className={cx('flex items-center gap-3 rounded-md border p-3 cursor-pointer',
                    profile.explanationStyle === v ? 'border-[var(--brand)] bg-blue-50 dark:bg-blue-950/40' : 'border-slate-200 dark:border-slate-700')}>
                    <input type="radio" name="style" checked={profile.explanationStyle === v} onChange={() => patch({ explanationStyle: v })} />
                    <span className="text-sm">{label}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            <label className="flex items-center gap-3 text-sm">
              <input type="checkbox" checked={profile.notificationsEnabled} onChange={(e) => patch({ notificationsEnabled: e.target.checked })} />
              Enable study reminders (browser notifications where supported)
            </label>
            <label className="flex items-center gap-3 text-sm">
              <input type="checkbox" checked={profile.focusFeatureEnabled} onChange={(e) => patch({ focusFeatureEnabled: e.target.checked })} />
              I want focus tools. App blocking is available in the Android app only — the website has a Pomodoro timer instead.
            </label>
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4">
            <SectionTitle icon="✨">Your plan is ready</SectionTitle>
            <p className="text-sm">
              We generated a 4-week plan using your exam profile ({profile.examTarget.replace('_', ' ')}),
              {' '}{profile.minutesPerDay} minutes/day, with extra time for{' '}
              {profile.weakestSubjects.length ? profile.weakestSubjects.join(', ') : 'your weakest subjects once you mark them'}.
            </p>
            <p className="text-xs text-[var(--ink-muted)]">
              Subject weights follow approximate exam content-outline proportions (documented in
              shared/content — they are not official percentages). Your performance data adjusts
              recommendations over time.
            </p>
          </div>
        )}

        <div className="flex justify-between mt-6">
          <button type="button" className="btn-ghost" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0}>
            Back
          </button>
          {step < STEPS.length - 1 ? (
            <button type="button" className="btn-primary" onClick={() => setStep((s) => s + 1)}>
              Next
            </button>
          ) : (
            <button type="button" className="btn-primary" onClick={finish}>
              Generate plan &amp; go to dashboard
            </button>
          )}
        </div>
      </Card>
    </div>
  );
}
