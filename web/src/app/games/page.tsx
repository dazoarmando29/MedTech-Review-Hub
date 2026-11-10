'use client';

// Educational humor system: memes, mnemonics, and mini-games. Humor always
// reinforces accurate facts — every entry carries a mandatory scientificFact.
import React, { useMemo, useState } from 'react';
import { Badge, Card, EmptyState, SectionTitle, Tabs, cx } from '@/components/ui';
import { LabVisual } from '@/components/visuals';
import { memes as allMemes, subjects, visualIdBank } from '@/lib/content';
import { addCardFromSeed, useStore } from '@/lib/store';

type Filter = 'ALL' | 'MEME_OF_THE_LESSON' | 'MEMORY_HOOK' | 'GUESS_THE_DIAGNOSIS' | 'LABORATORY_MYSTERY' | 'IMPOSTOR' | 'BOSS_BATTLE' | 'MISTAKE_OF_THE_DAY' | 'KEYWORD_CHALLENGE' | 'VISUAL_ID';

const TYPE_LABEL: Record<string, string> = {
  MEME_OF_THE_LESSON: 'Meme of the lesson',
  MEMORY_HOOK: 'Memory hook',
  KEYWORD_CHALLENGE: 'Keyword challenge',
  GUESS_THE_DIAGNOSIS: 'Guess the diagnosis',
  LABORATORY_MYSTERY: 'Laboratory mystery',
  IMPOSTOR: 'Which one is the impostor?',
  BOSS_BATTLE: 'Boss battle',
  MISTAKE_OF_THE_DAY: 'Mistake of the day',
};

export default function GamesPage() {
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [revealed, setRevealed] = useState<Record<string, boolean>>({});
  const [score, setScore] = useState({ right: 0, wrong: 0 });

  const entries = useMemo(
    () => (filter === 'ALL' ? allMemes : allMemes.filter((m) => m.type === filter)),
    [filter],
  );

  if (seriousMode) {
    return (
      <EmptyState
        icon="🎩"
        title="Serious Mode is on"
        hint="Memes, mini-games, and mascot commentary are hidden. You can turn Serious Mode off in Settings."
      />
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Memes, Mnemonics &amp; Mini-Games</h1>
        <p className="text-sm text-[var(--ink-muted)] max-w-2xl">
          Serious medicine, funny hooks. Every joke here is attached to a verified scientific fact —
          humor is never a substitute for the clinical explanation. Original content only.
        </p>
        <div className="flex gap-2 mt-2">
          <Badge tone="success">✓ {score.right} recalled</Badge>
          <Badge tone="danger">✗ {score.wrong} missed</Badge>
        </div>
      </div>

      <Tabs
        tabs={[
          { id: 'ALL', label: 'All' },
          { id: 'VISUAL_ID', label: '🔬 Visual ID' },
          { id: 'GUESS_THE_DIAGNOSIS', label: 'Guess the dx' },
          { id: 'LABORATORY_MYSTERY', label: 'Lab mystery' },
          { id: 'IMPOSTOR', label: 'Impostor' },
          { id: 'BOSS_BATTLE', label: 'Boss battle' },
          { id: 'MISTAKE_OF_THE_DAY', label: 'Mistake of day' },
          { id: 'MEME_OF_THE_LESSON', label: 'Memes' },
          { id: 'MEMORY_HOOK', label: 'Hooks' },
          { id: 'KEYWORD_CHALLENGE', label: 'Keywords' },
        ]}
        active={filter}
        onChange={(id) => setFilter(id as Filter)}
      />

      {filter === 'VISUAL_ID' && <VisualIdGame />}

      {filter !== 'VISUAL_ID' && (entries.length === 0 ? (
        <EmptyState icon="🎨" title="No entries in this category yet" hint="Content team: add entries to shared/content/memes.json." />
      ) : (
        <div className="grid sm:grid-cols-2 gap-4">
          {entries.map((m) => {
            const isRevealed = revealed[m.id];
            const isGame = ['GUESS_THE_DIAGNOSIS', 'LABORATORY_MYSTERY', 'IMPOSTOR', 'BOSS_BATTLE', 'KEYWORD_CHALLENGE'].includes(m.type);
            return (
              <Card key={m.id} className="flex flex-col">
                <div className="flex items-center justify-between">
                  <Badge tone="info">{TYPE_LABEL[m.type] ?? m.type}</Badge>
                  <Badge>{subjects.find((s) => s.id === m.subjectId)?.shortName ?? m.subjectId}</Badge>
                </div>
                <h3 className="font-heading font-bold mt-2">{m.title}</h3>
                <p className="text-sm mt-2 whitespace-pre-wrap">{m.body}</p>

                {isGame ? (
                  <div className="mt-3 space-y-2">
                    {!isRevealed ? (
                      <div className="flex gap-2">
                        <button
                          className="btn-secondary text-xs"
                          onClick={() => {
                            setScore((s) => ({ ...s, right: s.right + 1 }));
                            setRevealed((r) => ({ ...r, [m.id]: true }));
                          }}
                        >
                          I think I know it — reveal
                        </button>
                        <button
                          className="btn-ghost text-xs"
                          onClick={() => {
                            setScore((s) => ({ ...s, wrong: s.wrong + 1 }));
                            setRevealed((r) => ({ ...r, [m.id]: true }));
                          }}
                        >
                          Show me
                        </button>
                      </div>
                    ) : (
                      <div className="rounded-md bg-slate-50 dark:bg-slate-800 p-3 text-sm space-y-2">
                        <p><strong>Answer:</strong> {m.punchline}</p>
                        <p className="text-xs"><strong>Scientific fact:</strong> {m.scientificFact}</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="mt-3 rounded-md bg-amber-50 dark:bg-amber-950/30 p-3 text-sm space-y-2">
                    <p className="italic">{m.punchline}</p>
                    <p className="text-xs text-[var(--ink-muted)]">
                      <strong>Scientific fact:</strong> {m.scientificFact}
                    </p>
                  </div>
                )}

                <div className="mt-auto pt-3">
                  <button
                    className="btn-ghost !py-1.5 !min-h-[36px] text-xs w-full"
                    onClick={() =>
                      addCardFromSeed(
                        {
                          id: `fc-meme-${m.id}-${Date.now()}`,
                          type: 'BASIC',
                          front: m.title,
                          back: `${m.punchline} — ${m.scientificFact}`,
                          tags: ['meme-hook', m.subjectId],
                          difficulty: 1,
                          source: { kind: 'MEME', lessonId: m.lessonId },
                        },
                        'meme-hooks',
                        m.subjectId,
                      )
                    }
                  >
                    → meme to flashcard
                  </button>
                </div>
              </Card>
            );
          })}
        </div>
      ))}

      <Card>
        <SectionTitle icon="📜">Humor guidelines</SectionTitle>
        <ul className="text-xs text-[var(--ink-muted)] list-disc pl-5 space-y-1">
          <li>Every meme is scientifically accurate and links back to its lesson.</li>
          <li>Playful explanations are labeled separately from clinical explanations.</li>
          <li>No copyrighted meme images — original illustrations and user-created content only.</li>
          <li>No jokes in formal mock exam mode; no stereotypes or patient humor.</li>
          <li>Want it quiet? Serious Mode in Settings hides everything on this page.</li>
        </ul>
      </Card>
    </div>
  );
}

function VisualIdGame() {
  const [idx, setIdx] = useState(0);
  const [picked, setPicked] = useState<string | null>(null);
  const [score, setScore] = useState({ right: 0, total: 0 });
  const items = visualIdBank;

  if (!items.length) {
    return <EmptyState mood="confused" title="Visual ID bank is loading" hint="Original laboratory illustrations will appear here." />;
  }

  const item = items[idx % items.length];
  const done = picked !== null;

  return (
    <div className="grid lg:grid-cols-2 gap-4">
      <Card className="flex flex-col items-center">
        <Badge tone="info">identify this</Badge>
        <div className="mt-3">
          <LabVisual id={item.svgId} size={240} />
        </div>
        <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
          Original illustration — {item.subjectId.replace('-', ' ')}
        </p>
      </Card>
      <Card>
        <SectionTitle icon="🎯">What is it?</SectionTitle>
        <div className="space-y-2">
          {item.choices.map((c) => {
            const correct = c === item.correct;
            return (
              <button
                key={c}
                disabled={done}
                onClick={() => {
                  setPicked(c);
                  setScore((s) => ({ right: s.right + (c === item.correct ? 1 : 0), total: s.total + 1 }));
                }}
                className="w-full text-left rounded-[14px] border p-3 min-h-[44px]"
                style={{
                  borderColor: done && correct ? '#4ade80' : done && c === picked ? '#fb7185' : 'var(--line-strong)',
                  background: done && correct ? '#dcfce7' : done && c === picked ? '#ffe4e6' : 'var(--surface-2)',
                }}
              >
                {c} {done && correct && '✓'} {done && c === picked && !correct && '✗'}
              </button>
            );
          })}
        </div>
        {done && (
          <div className="rounded-[12px] p-3 text-sm mt-3" style={{ background: 'var(--chip)' }}>
            <strong>{picked === item.correct ? 'Correct! 🌸' : 'Not quite —'}</strong> {item.explanation}
          </div>
        )}
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs" style={{ color: 'var(--muted)' }}>
            Score: {score.right}/{score.total} · original artwork, no copyrighted images
          </span>
          <button className="btn-primary !py-1.5 text-xs" onClick={() => { setIdx((i) => i + 1); setPicked(null); }}>
            Next image →
          </button>
        </div>
      </Card>
    </div>
  );
}
