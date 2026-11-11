// Helpers for whole-document AI generation: page text collection is done by the
// caller (needs the live pdf.js handle); chunking is pure and unit-tested.

export function chunkText(text: string, maxChars = 7500): string[] {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return [];
  // Split at sentence boundaries without regex lookbehind (older iPad Safari
  // cannot parse lookbehind and would crash the whole PDF page at load).
  const sentences: string[] = [];
  let current = '';
  for (const word of clean.split(' ')) {
    current = current ? `${current} ${word}` : word;
    if (/[.!?]$/.test(word)) {
      sentences.push(current);
      current = '';
    }
  }
  if (current) sentences.push(current);

  const chunks: string[] = [];
  let buf = '';
  for (const sentence of sentences) {
    if ((buf + ' ' + sentence).length > maxChars && buf) {
      chunks.push(buf.trim());
      buf = sentence;
    } else {
      buf = buf ? `${buf} ${sentence}` : sentence;
    }
  }
  if (buf.trim()) chunks.push(buf.trim());
  return chunks;
}

/** Removes obvious front-matter so generation focuses on review content. */
export function stripFrontMatter(text: string): string {
  return text
    .replace(/copyright.{0,120}/gi, ' ')
    .replace(/all rights reserved.{0,120}/gi, ' ')
    .replace(/isbn.{0,80}/gi, ' ')
    .replace(/published by.{0,100}/gi, ' ')
    .replace(/table of contents.{0,160}/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export const MAX_AI_PARTS = 10; // bound cost/latency for whole-document jobs

/** Evenly-spaced subset — used to cover a whole book with a bounded budget. */
export function pickSpread<T>(items: T[], max: number): T[] {
  if (items.length <= max) return items;
  const out: T[] = [];
  const step = (items.length - 1) / (max - 1);
  for (let i = 0; i < max; i++) out.push(items[Math.round(i * step)]);
  return [...new Set(out)];
}

/**
 * Pages to read for "whole document" mode: skip front matter, and on big books
 * sample evenly across ALL chapters (600-page books stay fast and covered).
 */
export function samplePages(total: number, maxPages = 120, skipFrontMatter = 10): number[] {
  const all: number[] = [];
  for (let n = Math.min(skipFrontMatter + 1, total); n <= total; n++) all.push(n);
  return pickSpread(all, maxPages);
}
