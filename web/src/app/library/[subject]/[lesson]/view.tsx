'use client';

// Lesson view: Quick Review / Deep Study modes, keyword learning, highlights,
// text-to-speech, practice questions, memory hooks, references.
import Link from 'next/link';
import { notFound, useParams, useRouter } from 'next/navigation';
import React, { useEffect, useMemo, useState } from 'react';
import { Badge, Card, DataTable, EmptyState, RichText, SectionTitle, Tabs, cx } from '@/components/ui';
import { getLesson, getQuestion, getSubject, questionBank } from '@/lib/content';
import {
  addCardFromSeed, addGlossaryTerm, addHighlight, setLessonProgress, toggleSavedQuestion, useStore,
} from '@/lib/store';
import { newAttemptId, selectQuestions } from '@/lib/quiz';
import { startQuiz } from '@/lib/store';
import { api as ai, AiUnavailableError, ApiUnavailableError, configureApi } from '@/lib/api';
import { contextQuote } from '@/lib/motivation';
import { speak, stopSpeaking, ttsSupported } from '@/lib/tts';
import type { HighlightColor, QuizAttempt } from '@/lib/types';

const COLORS: HighlightColor[] = ['YELLOW', 'PINK', 'PURPLE', 'BLUE', 'GREEN', 'ROSE'];

export default function LessonView() {
  const params = useParams<{ subject: string; lesson: string }>();
  const router = useRouter();
  const lesson = getLesson(`${params.subject}/${params.lesson}`);
  const [mode, setMode] = useState<'quick' | 'deep' | 'practice'>('quick');
  const [hideKeywords, setHideKeywords] = useState(false);
  const [selection, setSelection] = useState<string | null>(null);
  const [showHook, setShowHook] = useState(true);
  const [aiText, setAiText] = useState<string | null>(null);
  const [aiNote, setAiNote] = useState<string | null>(null);
  const [kudos, setKudos] = useState<string | null>(null);
  const [rejected, setRejected] = useState<Record<string, boolean>>({});
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const apiBase = useStore((s) => s.settings.apiBaseUrl);
  const progress = useStore((s) => s.lessonProgress[lesson?.id ?? '']);
  const savedQuestions = useStore((s) => s.savedQuestions);
  const wrongQuestions = useStore((s) => s.wrongQuestions);

  const explainSimply = async () => {
    if (!lesson) return;
    configureApi(apiBase);
    setAiNote(null);
    setAiText('Asking Mochi’s AI helper…');
    try {
      const res = await ai.aiExplain(
        `${lesson.title}\n${lesson.summary}\n${lesson.deepStudy.introduction}\n${lesson.deepStudy.sections.map((s) => s.body).join('\n')}`,
        'SIMPLE_ENGLISH',
      );
      setAiText(typeof res.result === 'string' ? res.result : JSON.stringify(res.result, null, 2));
    } catch (e) {
      setAiText(null);
      setAiNote(
        e instanceof AiUnavailableError
          ? 'AI is not configured on the server right now — everything else in this lesson works offline.'
          : e instanceof ApiUnavailableError
            ? 'No server connection — try again later or study the Deep Study tab meanwhile.'
            : 'Something went wrong asking the AI. Please try again.',
      );
    }
  };

  const quizMe = () => {
    if (!lesson) return;
    const selected = selectQuestions(
      { kind: 'incorrect', questionIds: lesson.practiceQuestionIds, wrongQuestions: {} },
      lesson.practiceQuestionIds.length,
    );
    const fallback = questionBank.filter((q) => q.subjectId === lesson.subjectId).slice(0, 5);
    const use = selected.length ? selected : fallback;
    const attempt: QuizAttempt = {
      id: newAttemptId(),
      startedAt: new Date().toISOString(),
      finishedAt: null,
      mode: 'STUDY',
      filters: { label: `Quiz me — ${lesson.title}` },
      questionIds: use.map((q) => q.id),
      answers: [],
      timeLimitSeconds: null,
      finished: false,
    };
    startQuiz(attempt);
    router.push('/quiz/');
  };

  useEffect(() => {
    if (lesson) setLessonProgress(lesson.id, { percent: Math.max(progress?.percent ?? 0, 10) });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lesson?.id]);

  useEffect(() => {
    const onSelection = () => {
      const sel = window.getSelection()?.toString().trim();
      setSelection(sel && sel.length > 8 ? sel.slice(0, 500) : null);
    };
    document.addEventListener('mouseup', onSelection);
    return () => document.removeEventListener('mouseup', onSelection);
  }, []);

  if (!lesson) return notFound();
  const subject = getSubject(lesson.subjectId);
  const questions = lesson.practiceQuestionIds
    .map((id) => getQuestion(id))
    .filter((q): q is NonNullable<typeof q> => Boolean(q));

  const markRead = () => {
    setLessonProgress(lesson.id, { percent: 100, completedAt: new Date().toISOString() });
    setKudos(contextQuote('afterLesson'));
  };

  return (
    <div className="space-y-6">
      <div>
        <Link href={`/library/${lesson.subjectId}/`} className="text-sm underline text-[var(--ink-muted)]">
          ← {subject?.shortName}
        </Link>
        <h1 className="font-heading text-2xl font-bold mt-2">{lesson.title}</h1>
        <p className="text-sm text-[var(--ink-muted)] max-w-3xl">{lesson.summary}</p>
        <div className="flex flex-wrap gap-2 mt-3 items-center">
          <Badge tone="info">{subject?.shortName}</Badge>
          <Badge>{lesson.difficulty.toLowerCase()}</Badge>
          <Badge>⏱ {lesson.readingMinutes} min</Badge>
          <Badge tone={lesson.editorialStatus === 'PUBLISHED' ? 'success' : 'warn'}>
            {lesson.editorialStatus.toLowerCase()}
          </Badge>
          <button className="btn-secondary !py-1.5 !px-3 !min-h-[36px] text-xs" onClick={() => {
            addCardFromSeed(
              {
                id: `fc-lesson-${Date.now()}`,
                type: 'BASIC',
                front: `Key points: ${lesson.title}`,
                back: lesson.quickReview.highYield.slice(0, 3).join(' · '),
                tags: ['from-lesson', lesson.topicId],
                difficulty: 2,
                source: { kind: 'MANUAL', lessonId: lesson.id },
              },
              'lesson-notes',
              lesson.subjectId,
            );
          }}>
            🃏 Turn this into a flashcard
          </button>
          <button className="btn-secondary !py-1.5 !px-3 !min-h-[36px] text-xs" onClick={explainSimply}>
            💬 Explain this simply
          </button>
          <button className="btn-primary !py-1.5 !px-3 !min-h-[36px] text-xs" onClick={quizMe}>
            📝 Quiz me on this!
          </button>
          {ttsSupported() && (
            <button
              className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs"
              onClick={() =>
                speak(mode === 'quick' ? lesson.quickReview.highYield.join('. ') : lesson.deepStudy.introduction)
              }
            >
              🔊 Read aloud
            </button>
          )}
          <button className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs" onClick={markRead}>
            ✅ Mark as studied
          </button>
        </div>
      </div>

      {kudos && (
        <div className="sticky-note confetti-pop flex items-center justify-between gap-3">
          <p className="text-sm font-semibold">🌸 {kudos}</p>
          <button className="chip" onClick={() => setKudos(null)}>dismiss</button>
        </div>
      )}

      {(aiText || aiNote) && (
        <div className="card-soft p-4">
          <SectionTitle icon="🤖">Simply explained</SectionTitle>
          {aiNote && <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>{aiNote}</p>}
          {aiText && <RichText text={aiText} className="text-sm" />}
          <button className="btn-ghost !py-1 !px-3 !min-h-[32px] text-xs mt-2" onClick={() => { setAiText(null); setAiNote(null); }}>
            Close
          </button>
        </div>
      )}

      {/* automatic keyword suggestions — accept adds to your glossary */}
      <div className="card-soft p-4">
        <SectionTitle icon="🔑">Keyword suggestions for this lesson</SectionTitle>
        <p className="text-xs mb-2" style={{ color: 'var(--muted)' }}>
          Tap a keyword to save it to your personal glossary. Dismiss the ones you already know.
        </p>
        <div className="flex flex-wrap gap-2">
          {lesson.keywords.map((k) => (
            rejected[k] ? (
              <button key={k} className="chip opacity-50" onClick={() => setRejected((r) => ({ ...r, [k]: false }))}>
                {k} · dismissed (tap to restore)
              </button>
            ) : (
              <span key={k} className="inline-flex gap-1">
                <button
                  className="chip hover:shadow-lift"
                  title="Save to glossary"
                  onClick={() =>
                    addGlossaryTerm({
                      id: `glo-key-${Date.now()}-${k.slice(0, 4)}`,
                      term: k,
                      definition: `Keyword from “${lesson.title}”. Add your own definition as you study.`,
                      subjectId: lesson.subjectId,
                      source: `lesson: ${lesson.id}`,
                    })
                  }
                >
                  ✨ {k}
                </button>
                <button
                  className="chip"
                  aria-label={`Dismiss ${k}`}
                  onClick={() => setRejected((r) => ({ ...r, [k]: true }))}
                >
                  ✕
                </button>
              </span>
            )
          ))}
        </div>
      </div>

      {selection && (
        <div className="card p-5 border-l-4" style={{ borderLeftColor: 'var(--brand-3)' }}>
          <SectionTitle icon="🖍️">Highlight selection</SectionTitle>
          <p className="text-xs text-[var(--ink-muted)] mb-2">Selected: “{selection.slice(0, 120)}…”</p>
          <div className="flex flex-wrap gap-2">
            {COLORS.map((c) => (
              <button
                key={c}
                className={`btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs hl-${c}`}
                onClick={() => {
                  addHighlight({
                    id: `hl-${Date.now()}`,
                    lessonId: lesson.id,
                    text: selection,
                    color: c,
                    note: '',
                    createdAt: new Date().toISOString(),
                  });
                  setSelection(null);
                }}
              >
                {c.toLowerCase()}
              </button>
            ))}
            <button
              className="btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs"
              onClick={() => {
                addCardFromSeed(
                  {
                    id: `fc-hl-${Date.now()}`,
                    type: 'BASIC',
                    front: selection.slice(0, 120),
                    back: '(your answer here — edit in Flashcards)',
                    tags: ['from-highlight', lesson.topicId],
                    difficulty: 2,
                    source: { kind: 'PDF_HIGHLIGHT', lessonId: lesson.id },
                  },
                  'user-created',
                  lesson.subjectId,
                );
                setSelection(null);
              }}
            >
              → flashcard
            </button>
          </div>
        </div>
      )}

      <Tabs
        tabs={[
          { id: 'quick', label: '⚡ Quick Review' },
          { id: 'deep', label: '🔬 Deep Study' },
          { id: 'practice', label: `📝 Practice (${questions.length})` },
        ]}
        active={mode}
        onChange={(id) => setMode(id as 'quick' | 'deep' | 'practice')}
      />

      {mode === 'quick' && (
        <div className="space-y-4">
          <Card>
            <SectionTitle icon="⚡" action={
              <label className="text-xs flex items-center gap-2">
                <input type="checkbox" checked={hideKeywords} onChange={(e) => setHideKeywords(e.target.checked)} />
                Hide the keywords
              </label>
            }>
              High-yield facts
            </SectionTitle>
            <ul className="space-y-2">
              {lesson.quickReview.highYield.map((fact, i) => (
                <li key={i} className="flex gap-2 text-sm">
                  <span aria-hidden>🔹</span>
                  <span>{hideKeywords ? <HiddenKeywords text={fact} keywords={lesson.keywords} /> : fact}</span>
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <SectionTitle icon="📊">Must-know table</SectionTitle>
            <DataTable table={lesson.quickReview.mustKnowTable} />
          </Card>

          <Card>
            <SectionTitle icon="🧠">Memory hook</SectionTitle>
            <p className="text-sm">{lesson.quickReview.mnemonic}</p>
          </Card>

          {lesson.memoryHooks.length > 0 && !seriousMode && (
            <Card className="border-l-4" style={{ borderLeftColor: 'var(--primary-soft)' }}>
              <SectionTitle icon="😹">Meme of the lesson</SectionTitle>
              {lesson.memoryHooks.map((h, i) => (
                <div key={i} className="sticky-note my-2">
                  <p>{h.text}</p>
                  {h.funny && showHook && (
                    <p className="italic mt-1 text-xs" style={{ color: 'var(--muted-strong)' }}>{h.funny}</p>
                  )}
                </div>
              ))}
              <p className="text-[11px] mt-2" style={{ color: 'var(--muted)' }}>
                Playful explanations are clearly labeled — the joke never replaces the clinical explanation.
                <button className="underline ml-2" onClick={() => setShowHook((v) => !v)}>
                  {showHook ? 'hide joke' : 'show joke'}
                </button>
              </p>
            </Card>
          )}
        </div>
      )}

      {mode === 'deep' && (
        <div className="space-y-4">
          <Card>
            <SectionTitle icon="🔬">Introduction</SectionTitle>
            <RichText text={lesson.deepStudy.introduction} />
          </Card>

          {lesson.deepStudy.sections.map((sec, i) => (
            <Card key={i}>
              <SectionTitle icon="📖">{sec.heading}</SectionTitle>
              <RichText text={hideKeywords ? maskKeywords(sec.body, lesson.keywords) : sec.body} />
            </Card>
          ))}

          <Card>
            <SectionTitle icon="🏥">Clinical significance</SectionTitle>
            <RichText text={lesson.deepStudy.clinicalSignificance} />
          </Card>

          <Card>
            <SectionTitle icon="🧪">Laboratory test interpretation</SectionTitle>
            <RichText text={lesson.deepStudy.labInterpretation} />
            <p className="text-xs text-[var(--ink-muted)] mt-3">
              Reference intervals vary by laboratory, method, age, sex, and population — always verify
              against your local laboratory&apos;s established intervals.
            </p>
          </Card>

          {lesson.deepStudy.comparisonTables.map((t, i) => (
            <Card key={i}>
              <SectionTitle icon="⚖️">{t.columns[0] ? `${t.columns[0]} comparison` : 'Comparison'}</SectionTitle>
              <DataTable table={t} />
            </Card>
          ))}

          <Card className="border-l-4 border-l-red-500">
            <SectionTitle icon="⚠️">Common exam traps</SectionTitle>
            <ul className="space-y-2">
              {lesson.deepStudy.examTraps.map((trap, i) => (
                <li key={i} className="text-sm flex gap-2">
                  <span aria-hidden>🪤</span>
                  <span>{trap}</span>
                </li>
              ))}
            </ul>
          </Card>

          {lesson.deepStudy.workedExample && (
            <Card>
              <SectionTitle icon="🧮">Worked example</SectionTitle>
              <RichText text={lesson.deepStudy.workedExample} />
            </Card>
          )}
        </div>
      )}

      {mode === 'practice' && (
        <div className="space-y-4">
          {questions.length === 0 && (
            <EmptyState icon="📝" title="No linked questions yet" hint="Question authors: link ids in the lesson's practiceQuestionIds." />
          )}
          {questions.map((q) => (
            <PracticeQuestion key={q.id} questionId={q.id} saved={savedQuestions.includes(q.id)} wrongCount={wrongQuestions[q.id] ?? 0} />
          ))}
        </div>
      )}

      <div className="grid lg:grid-cols-2 gap-4">
        <Card>
          <SectionTitle icon="🃏">Related flashcards</SectionTitle>
          <div className="flex flex-wrap gap-2">
            {lesson.relatedFlashcardIds.map((id) => (
              <Link key={id} href="/flashcards/" className="chip hover:underline">{id}</Link>
            ))}
          </div>
        </Card>

        <Card>
          <SectionTitle icon="➡️">Suggested follow-up</SectionTitle>
          <div className="flex flex-wrap gap-2">
            {lesson.followUp.map((id) => {
              const [subj, slug] = id.split('/');
              return (
                <Link key={id} href={`/library/${subj}/${encodeURIComponent(slug)}/`} className="chip hover:underline">
                  {id.replace('/', ' · ')}
                </Link>
              );
            })}
          </div>
        </Card>
      </div>

      <Card>
        <SectionTitle icon="📚">References</SectionTitle>
        <ul className="space-y-1 text-sm">
          {lesson.references.map((r, i) => (
            <li key={i}>
              {r.url ? (
                <a href={r.url} className="underline" target="_blank" rel="noreferrer">{r.title}</a>
              ) : (
                <span>{r.title}</span>
              )}
              {r.detail && <span className="text-[var(--ink-muted)]"> — {r.detail}</span>}
            </li>
          ))}
        </ul>
        <p className="text-xs text-[var(--ink-muted)] mt-3">{lesson.reviewerNote}</p>
      </Card>
    </div>
  );
}

function HiddenKeywords({ text, keywords }: { text: string; keywords: string[] }) {
  return <span>{replaceKeywords(text, keywords, true)}</span>;
}

function maskKeywords(text: string, keywords: string[]): string {
  if (!keywords.length) return text;
  const sorted = [...keywords].sort((a, b) => b.length - a.length);
  const pattern = new RegExp(`(${sorted.map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi');
  return text.replace(pattern, '[HIDDEN]');
}

function replaceKeywords(text: string, keywords: string[], interactive = false): React.ReactNode {
  if (!keywords.length) return text;
  const sorted = [...keywords].sort((a, b) => b.length - a.length);
  const pattern = new RegExp(`(${sorted.map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi');
  const parts = text.split(pattern);
  return parts.map((part, i) => {
    const isKeyword = sorted.some((k) => k.toLowerCase() === part.toLowerCase());
    if (!isKeyword) return <React.Fragment key={i}>{part}</React.Fragment>;
    return interactive ? (
      <RevealKeyword key={i} word={part} />
    ) : (
      <mark key={i} className="hl-YELLOW rounded px-0.5">{part}</mark>
    );
  });
}

function RevealKeyword({ word }: { word: string }) {
  const [shown, setShown] = useState(false);
  return (
    <button
      onClick={() => setShown((v) => !v)}
      className={cx('rounded px-1 font-semibold', shown ? 'hl-GREEN' : 'bg-slate-300 dark:bg-slate-600 text-transparent select-none')}
      aria-label={shown ? word : 'hidden keyword, press to reveal'}
    >
      {shown ? word : word.replace(/./g, '█')}
    </button>
  );
}

function PracticeQuestion({ questionId, saved, wrongCount }: { questionId: string; saved: boolean; wrongCount: number }) {
  const q = getQuestion(questionId);
  const [chosen, setChosen] = useState<string | null>(null);
  const [showWhy, setShowWhy] = useState(false);
  if (!q) return null;

  return (
    <Card>
      <div className="flex items-start justify-between gap-2">
        <div className="flex gap-2">
          <Badge tone="info">{q.type === 'SCENARIO' ? 'clinical scenario' : 'mcq'}</Badge>
          <Badge>{q.difficulty.toLowerCase()}</Badge>
          {wrongCount > 0 && <Badge tone="danger">missed {wrongCount}×</Badge>}
        </div>
        <button className="btn-ghost !py-1 !px-2 !min-h-[32px] text-xs" onClick={() => toggleSavedQuestion(q.id)}>
          {saved ? '★ saved' : '☆ save'}
        </button>
      </div>

      {q.vignette && <p className="text-sm mt-3 p-3 rounded-md bg-slate-50 dark:bg-slate-800">{q.vignette}</p>}
      <p className="font-semibold mt-3">{q.stem}</p>

      <ul className="space-y-2 mt-3">
        {q.choices.map((c) => {
          const isCorrect = c.id === q.correctChoiceId;
          const isChosen = chosen === c.id;
          const revealed = chosen !== null;
          return (
            <li key={c.id}>
              <button
                className={cx(
                  'w-full text-left rounded-md border p-3 text-sm min-h-[44px]',
                  !revealed && 'border-slate-300 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800',
                  revealed && isCorrect && 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/40',
                  revealed && isChosen && !isCorrect && 'border-red-500 bg-red-50 dark:bg-red-950/40',
                  revealed && !isCorrect && !isChosen && 'opacity-70',
                )}
                disabled={revealed}
                onClick={() => setChosen(c.id)}
              >
                <strong>{c.id}.</strong> {c.text}
                {revealed && isCorrect && ' ✓'}
                {revealed && isChosen && !isCorrect && ' ✗'}
              </button>
              {revealed && (
                <p className="text-xs text-[var(--ink-muted)] mt-1 pl-3">
                  <strong>{c.id}:</strong> {q.rationales[c.id]}
                </p>
              )}
            </li>
          );
        })}
      </ul>

      {chosen && (
        <div className="mt-4 space-y-2">
          <div className={cx('rounded-md p-3 text-sm', chosen === q.correctChoiceId ? 'bg-emerald-50 dark:bg-emerald-950/40' : 'bg-amber-50 dark:bg-amber-950/40')}>
            <strong>{chosen === q.correctChoiceId ? 'Correct!' : 'Not quite.'}</strong> {q.explanation}
          </div>
          {chosen !== q.correctChoiceId && (
            <button className="btn-ghost text-xs" onClick={() => setShowWhy((v) => !v)}>
              {showWhy ? 'Hide' : 'Why was I wrong?'}
            </button>
          )}
          {showWhy && (
            <div className="rounded-md p-3 text-sm bg-slate-50 dark:bg-slate-800">
              <strong>Likely misconception:</strong> {q.misconception}
            </div>
          )}
          <p className="text-xs text-[var(--ink-muted)]">Reference: {q.reference}</p>
        </div>
      )}
    </Card>
  );
}
