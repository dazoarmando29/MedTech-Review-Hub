import { describe, expect, it } from 'vitest';
import { chunkText, MAX_AI_PARTS, stripFrontMatter } from '../pdfText';

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
});
