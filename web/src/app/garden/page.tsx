'use client';

// Study Garden (flowers grow with study sessions) + Cat Room (cosmetic unlocks).
import React from 'react';
import Link from 'next/link';
import { Badge, Card, EmptyState, SectionTitle, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { catRoomItems, flowerStage, STAGES, type FlowerStage } from '@/lib/cozy';
import { computeStreak } from '@/lib/cozy';
import { equipCatItem, logSession, useStore } from '@/lib/store';
import type { GardenFlower } from '@/lib/types';

export default function GardenPage() {
  const garden = useStore((s) => s.garden);
  const catRoom = useStore((s) => s.catRoom);
  const streak = useStore((s) => computeStreak(s));
  const sessions = useStore((s) => s.sessions.length);

  const blooms = garden.flowers.filter((f) => flowerStage(f) === 'bloom').length;
  const items = catRoomItems({ sessions, streak, blooms }, catRoom.equipped);

  return (
    <div className="space-y-6">
      <div className="hero-gradient p-6 sm:p-8">
        <div className="flex flex-wrap items-center gap-6">
          <div className="flex-1 min-w-[260px]">
            <h1 className="font-display text-2xl font-bold">Study Garden 🌷</h1>
            <p className="text-sm mt-1" style={{ color: 'var(--muted-strong)' }}>
              Every study session helps your garden grow. Flowers bloom at {STAGES.bloom} minutes
              of care — one petal at a time, future MLS.
            </p>
            <div className="flex gap-2 mt-3">
              <span className="chip">🌱 {garden.flowers.length} planted</span>
              <span className="chip">🌸 {blooms} bloomed</span>
              <span className="chip">🔥 {streak}-day streak</span>
            </div>
            <button
              className="btn-primary mt-4"
              onClick={() => logSession({ id: `sess-${Date.now()}`, startedAt: new Date().toISOString(), kind: 'FOCUS', subjectId: null, minutes: 15, itemsCompleted: 1 })}
            >
              Log a 15-minute study session 🌿
            </button>
            <p className="text-[11px] mt-2" style={{ color: 'var(--muted)' }}>
              Quiz and reading sessions are counted automatically too.
            </p>
          </div>
          <GardenArt flowers={garden.flowers} />
        </div>
      </div>

      <section>
        <SectionTitle icon="🪴">Your flowers</SectionTitle>
        {garden.flowers.length === 0 ? (
          <EmptyState
            mood="encouraging"
            title="Your garden is waiting for its first seed"
            hint="Complete any study session and a seed appears here. It grows as you do."
            action={<Link href="/flashcards/" className="btn-primary">Study some flashcards</Link>}
          />
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {garden.flowers.slice().reverse().map((f) => (
              <FlowerCard key={f.id} flower={f} />
            ))}
          </div>
        )}
      </section>

      <section>
        <SectionTitle icon="🎀">Cat Room — cozy decorations for Mochi</SectionTitle>
        <div className="grid lg:grid-cols-3 gap-4">
          <Card className="flex flex-col items-center justify-center gap-2">
            <Mochi mood="happy" size={150} equipped={catRoom.equipped} />
            <p className="text-sm font-semibold">Mochi looks lovely!</p>
            <p className="text-xs text-center" style={{ color: 'var(--muted)' }}>
              Equip or unequip decorations below. They’re purely cosmetic — earned by studying,
              never bought.
            </p>
          </Card>
          <div className="lg:col-span-2 grid sm:grid-cols-2 gap-3">
            {items.map((item) => (
              <Card key={item.id} className={cx(!item.unlocked && 'opacity-70')}>
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="font-heading font-bold text-sm">{item.name}</h3>
                    <p className="text-xs" style={{ color: 'var(--muted)' }}>{item.description}</p>
                  </div>
                  <Badge tone={item.unlocked ? 'success' : 'default'}>
                    {item.unlocked ? 'unlocked' : 'locked'}
                  </Badge>
                </div>
                <p className="text-xs mt-2" style={{ color: 'var(--muted-strong)' }}>🎯 {item.need}</p>
                <button
                  className={cx('mt-2 w-full text-xs', item.equipped ? 'btn-secondary' : 'btn-ghost')}
                  disabled={!item.unlocked}
                  onClick={() => equipCatItem(item.id)}
                >
                  {item.equipped ? 'Unequip' : item.unlocked ? 'Equip on Mochi' : 'Keep studying to unlock'}
                </button>
              </Card>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}

const STAGE_EMOJI: Record<FlowerStage, string> = { seed: '🫘', sprout: '🌿', bud: '🌱', bloom: '🌸' };

function FlowerCard({ flower }: { flower: GardenFlower }) {
  const stage = flowerStage(flower);
  const next =
    stage === 'seed' ? STAGES.sprout : stage === 'sprout' ? STAGES.bud : stage === 'bud' ? STAGES.bloom : null;
  return (
    <div className="card p-4 text-center">
      <div className="text-3xl">{STAGE_EMOJI[stage]}</div>
      <div className="text-xs font-semibold mt-1 capitalize">{flower.kind} · {stage}</div>
      {next !== null ? (
        <>
          <div className="h-1.5 rounded-full mt-2 overflow-hidden" style={{ background: 'var(--chip)' }}>
            <div className="h-full rounded-full" style={{ width: `${Math.min(100, (flower.minutes / next) * 100)}%`, background: 'var(--primary-soft)' }} />
          </div>
          <div className="text-[10px] mt-1" style={{ color: 'var(--muted)' }}>
            {flower.minutes}/{next} min to next stage
          </div>
        </>
      ) : (
        <div className="text-[10px] mt-2" style={{ color: 'var(--muted)' }}>Fully bloomed 🎉</div>
      )}
    </div>
  );
}

function GardenArt({ flowers }: { flowers: GardenFlower[] }) {
  const shown = flowers.slice(-7);
  return (
    <svg viewBox="0 0 220 130" width={220} height={130} aria-hidden className="decor-only">
      <rect x="0" y="92" width="220" height="38" rx="12" fill="#f8bbd0" opacity="0.55" />
      {shown.map((f, i) => {
        const x = 24 + i * 28;
        const stage = flowerStage(f);
        const h = stage === 'bloom' ? 52 : stage === 'bud' ? 36 : stage === 'sprout' ? 22 : 8;
        return (
          <g key={f.id}>
            <path d={`M${x} 92 V ${92 - h}`} stroke="#7fae7f" strokeWidth="3" strokeLinecap="round" />
            {stage === 'bloom' && (
              <>
                <circle cx={x} cy={92 - h - 8} r="7" fill="#f472b6" />
                <circle cx={x - 8} cy={92 - h - 2} r="5" fill="#f9a8d4" />
                <circle cx={x + 8} cy={92 - h - 2} r="5" fill="#f9a8d4" />
                <circle cx={x} cy={92 - h - 1} r="3.5" fill="#fde68a" />
              </>
            )}
            {stage === 'bud' && <circle cx={x} cy={92 - h - 3} r="5" fill="#c084fc" />}
            {stage === 'sprout' && <circle cx={x + 5} cy={92 - h} r="4" fill="#86efac" />}
          </g>
        );
      })}
    </svg>
  );
}
