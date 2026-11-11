import { describe, expect, it } from 'vitest';
import { chunkText, MAX_AI_PARTS, pickSpread, samplePages, stripFrontMatter } from '../pdfText';

describe('whole-document generation helpers', () => {
  it('chunks long text at sentence boundaries', () => {
    const text = Array.from({ length: 40 }, (_, i) => `Sentence number ${i} about laboratory science.`).join(' ');
    const chunks = chunkText(text, 200);
    expect(chunks.length).toBeGreaterThan(1);
    for (const ch of chunks) expect(ch.length).toBeLessThanOrEqual(250);
    expect(chunks.join(' ')).toContain('Sentence number 0');
  });

  it('returns empty for blank input', () => {
    expect(chunkText('')).toEqual([]);
    expect(chunkText('   ')).toEqual([]);
  });

  it('strips publisher front matter so generation focuses on review content', () => {
    const raw = 'Copyright 2020 by Some Publisher. All rights reserved. ISBN 978-0-00. Published by Books Inc. Table of Contents Chapter 1 ... The anion gap equals Na minus Cl plus HCO3.';
    const cleaned = stripFrontMatter(raw);
    expect(cleaned).toContain('anion gap');
    expect(cleaned.toLowerCase()).not.toContain('isbn');
    expect(cleaned.toLowerCase()).not.toContain('all rights reserved');
  });

  it('bounds whole-document jobs to a maximum number of parts', () => {
    expect(MAX_AI_PARTS).toBeLessThanOrEqual(12);
  });

  it('pickSpread covers the whole document with a bounded budget', () => {
    const items = Array.from({ length: 60 }, (_, i) => i);
    const picked = pickSpread(items, 10);
    expect(picked.length).toBe(10);
    expect(picked[0]).toBe(0);
    expect(picked[picked.length - 1]).toBe(59); // first AND last are covered
    expect(pickSpread([1, 2, 3], 10)).toEqual([1, 2, 3]); // small lists pass through
  });

  it('samplePages skips front matter and spreads across big books', () => {
    const pages = samplePages(604, 120, 10);
    expect(Math.max(...pages)).toBe(604); // reaches the end of the book
    expect(Math.min(...pages)).toBeGreaterThan(10); // front matter skipped
    expect(pages.length).toBeLessThanOrEqual(120);
    const small = samplePages(20, 120, 10);
    expect(Math.max(...small)).toBe(20);
  });
});
