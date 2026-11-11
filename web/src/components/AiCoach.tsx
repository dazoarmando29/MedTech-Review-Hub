'use client';

// Post-exam AI coach — funny Taglish answers a non-medtech can understand,
// plus practical advice on what to review first.
import React, { useState } from 'react';
import { SectionTitle } from './ui';
import { resolveQuestion } from '@/lib/questions';
import { api, configureApi, friendlyApiError } from '@/lib/api';
import type { QuizResult } from '@/lib/quiz';
import type { QuizAttempt } from '@/lib/types';

const PRESETS: { label: string; prompt: string }[] = [
  {
    label: '🎯 What should I review first?',
    prompt:
      'Based on my quiz results below, tell me what to review FIRST and what can wait. Give a simple, kind study plan for the next 3 days.',
  },
  {
    label: '😂 Explain my mistakes the funny way',
    prompt:
      'Explain my wrong answers one by one in a funny, meme-like Taglish way that even a non-medtech can understand — but keep every fact accurate.',
  },
  {
    label: '🧠 Make the hardest topic easy',
    prompt:
      'Pick the topic I did worst on and explain it from scratch in funny Taglish, like a friend teaching me. Include one memory hook.',
  },
  {
    label: '💪 Cheer me up + advice',
    prompt:
      'Cheer me up about this quiz result in a warm, funny Taglish way, then give me 3 concrete tips on how to study smarter this week.',
  },
];

export function AiCoach({ attempt, result }: { attempt: QuizAttempt; result: QuizResult }) {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [apiBase, setApiBase] = useState<string | null>(null);

  const buildContext = () => {
    const wrongList = result.incorrectQuestionIds
      .map((id) => resolveQuestion(id))
      .filter(Boolean)
      .slice(0, 8)
      .map((q) => `- ${q!.stem} | correct: ${q!.choices.find((c) => c.id === q!.correctChoiceId)?.text} | why tricky: ${q!.misconception}`)
      .join('\n');
    const subjects = result.bySubject
      .map((s) => `${s.subjectId}: ${Math.round(s.accuracy * 100)}% (${s.correct}/${s.total})`)
      .join(', ');
    const weak = result.weakTopics.slice(0, 5).map((t) => `${t.topicId} (${Math.round(t.accuracy * 100)}%)`).join(', ');
    return [
      `MY QUIZ RESULT: ${result.correct}/${result.total} correct (${Math.round(result.score * 100)}%) in ${Math.round(result.timeSeconds / 60)} minutes.`,
      `Accuracy by subject: ${subjects || 'n/a'}.`,
      `Weakest topics: ${weak || 'none below 60%'}.`,
      wrongList ? `MY WRONG ANSWERS:\n${wrongList}` : 'I got everything right this time!',
    ].join('\n\n');
  };

  const ask = async (prompt: string) => {
    let base = apiBase;
    if (!base) {
      try {
        const saved = window.localStorage.getItem('mtrh-state-v1');
        base = saved ? (JSON.parse(saved)?.settings?.apiBaseUrl ?? '') : '';
      } catch {
        base = '';
      }
      setApiBase(base);
    }
    configureApi(base ?? '');
    setBusy(true);
    setNote(null);
    setAnswer('Mochi is thinking… 🐾');
    try {
      const res = await aiAsk(buildContext(), prompt);
      setAnswer(res);
    } catch (e) {
      setAnswer(null);
      setNote(friendlyApiError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card p-5">
      <SectionTitle icon="🤖">Ask your AI study coach</SectionTitle>
      <p className="text-sm" style={{ color: 'var(--muted-strong)' }}>
        Funny Taglish explanations anyone can understand — plus real advice on what to
        review first. (This coach is an AI helper, not a human tutor.)
      </p>

      <div className="flex flex-wrap gap-2 mt-3">
        {PRESETS.map((p) => (
          <button key={p.label} className="btn-secondary !py-1.5 text-xs" disabled={busy} onClick={() => void ask(p.prompt)}>
            {p.label}
          </button>
        ))}
      </div>

      <div className="flex gap-2 mt-3">
        <input
          className="input"
          placeholder="or ask your own question… e.g. bakit may anti-A sa group B?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && question.trim()) {
              void ask(question.trim());
              setQuestion('');
            }
          }}
        />
        <button
          className="btn-primary"
          disabled={busy || !question.trim()}
          onClick={() => {
            void ask(question.trim());
            setQuestion('');
          }}
        >
          Ask
        </button>
      </div>

      {note && <p className="text-sm mt-3 rounded-[12px] p-3" style={{ background: 'var(--chip)', color: 'var(--muted-strong)' }}>{note}</p>}
      {answer && (
        <pre className="text-sm mt-3 whitespace-pre-wrap rounded-[12px] p-4 max-h-80 overflow-auto" style={{ background: 'var(--surface-2)', fontFamily: 'inherit' }}>
          {answer}
        </pre>
      )}
    </div>
  );
}

async function aiAsk(context: string, prompt: string): Promise<string> {
  const res = await api.aiExplain(
    `${prompt}\n\n${context}`,
    'TAGLISH_FUNNY',
  );
  return typeof res.result === 'string' ? res.result : JSON.stringify(res.result, null, 2);
}
