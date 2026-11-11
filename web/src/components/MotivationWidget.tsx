'use client';

// "A Little Motivation for You" — daily quote card with favorite / copy /
// share / PNG export, plus "Little Notes for You" (private, user-written).
import React, { useState } from 'react';
import { Badge, Card, SectionTitle, cx } from '@/components/ui';
import { Mochi } from './Mochi';
import {
  dailyQuote, drawQuoteCard, pickLoveNote, randomQuote, wrapText,
} from '@/lib/motivation';
import { toggleQuoteFavorite, useStore } from '@/lib/store';

export function MotivationWidget() {
  const quoteFavorites = useStore((s) => s.quoteFavorites);
  const loveNotes = useStore((s) => s.loveNotes);
  const seriousMode = useStore((s) => s.settings.seriousMode);
  const personalNote = useStore((s) => s.settings.motivationNote);
  const [quote, setQuote] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const current = personalNote || quote || dailyQuote();
  const note = pickLoveNote(loveNotes);
  const favorite = quoteFavorites.includes(current);

  const exportCard = async () => {
    const canvas = document.createElement('canvas');
    drawQuoteCard(canvas, current);
    canvas.toBlob(async (blob) => {
      if (!blob) return;
      const file = new File([blob], 'motivation.png', { type: 'image/png' });
      if (navigator.canShare?.({ files: [file] })) {
        try {
          await navigator.share({ files: [file], title: 'A little motivation' });
          return;
        } catch {
          /* fall through to download */
        }
      }
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'motivation-card.png';
      a.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  };

  return (
    <div className="grid lg:grid-cols-3 gap-4">
      <div className="card-soft p-5 lg:col-span-2">
        <SectionTitle
          icon="🌸"
          action={<Badge tone="info">A little motivation for you</Badge>}
        >
          Today’s motivation
        </SectionTitle>
        <div className="flex items-start gap-4">
          {!seriousMode && <Mochi mood="encouraging" size={84} className="" />}
          <div className="flex-1">
            <p className="font-heading font-bold text-lg leading-relaxed">“{current}”</p>
            <div className="flex flex-wrap gap-2 mt-3">
              <button className="btn-secondary !py-1.5 text-xs" onClick={() => setQuote(randomQuote())}>
                🎲 Random quote
              </button>
              <button
                className={cx('btn-ghost !py-1.5 text-xs', favorite && 'btn-secondary')}
                onClick={() => toggleQuoteFavorite(current)}
              >
                {favorite ? '♥ Saved' : '♡ Save'}
              </button>
              <button
                className="btn-ghost !py-1.5 text-xs"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(current);
                    setCopied(true);
                    setTimeout(() => setCopied(false), 1500);
                  } catch {
                    /* clipboard unavailable */
                  }
                }}
              >
                {copied ? '✓ Copied' : '⧉ Copy'}
              </button>
              <button className="btn-ghost !py-1.5 text-xs" onClick={() => void exportCard()}>
                🖼 Share as image
              </button>
            </div>
            {quoteFavorites.length > 0 && (
              <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
                {quoteFavorites.length} saved quote{quoteFavorites.length === 1 ? '' : 's'} in your collection.
              </p>
            )}
          </div>
        </div>
        {wrapText(current, 26).length > 0 && (
          <p className="text-[11px] mt-2" style={{ color: 'var(--muted)' }}>
            Original, unattributed encouragement — written for this app.
          </p>
        )}
      </div>

      <div className="card p-5">
        <SectionTitle icon="💌">Little Notes for You</SectionTitle>
        {note ? (
          <div className="sticky-note">
            <p className="text-sm italic leading-relaxed">“{note.text}”</p>
            {note.favorite && <p className="text-xs mt-1">♥ favorite</p>}
          </div>
        ) : (
          <p className="text-sm" style={{ color: 'var(--muted)' }}>
            Someone who believes in you can leave little notes here. Add them in
            Settings → Personal Touch — they stay private on your device.
          </p>
        )}
      </div>
    </div>
  );
}
