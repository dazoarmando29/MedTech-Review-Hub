// Helpers for whole-document AI generation: page text collection is done by the
// caller (needs the live pdf.js handle); chunking is pure and unit-tested.

export function chunkText(text: string, maxChars = 5500): string[] {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return [];
  const chunks: string[] = [];
  let current = '';
  for (const sentence of clean.split(/(?<=[.!?])\s+/)) {
    if ((current + ' ' + sentence).length > maxChars && current) {
      chunks.push(current.trim());
      current = sentence;
    } else {
      current = current ? `${current} ${sentence}` : sentence;
    }
  }
  if (current.trim()) chunks.push(current.trim());
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

export const MAX_AI_PARTS = 8; // bound cost/latency for whole-document jobs
