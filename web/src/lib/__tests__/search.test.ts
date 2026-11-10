import { describe, expect, it } from 'vitest';
import { globalSearch } from '../search';
import { stripMarkdown } from '../tts';

describe('global search', () => {
  it('finds lessons by keyword', () => {
    const hits = globalSearch('coagulase');
    expect(hits.length).toBeGreaterThan(0);
    expect(hits.some((h) => h.kind === 'lesson' || h.kind === 'flashcard' || h.kind === 'question')).toBe(true);
  });

  it('finds glossary terms and formulas', () => {
    expect(globalSearch('anion gap').length).toBeGreaterThan(0);
  });

  it('returns nothing for very short queries', () => {
    expect(globalSearch('a')).toEqual([]);
  });

  it('searches user highlights when state is provided', () => {
    const state = {
      highlights: [
        {
          id: 'h1',
          lessonId: 'hematology/cbc-indices',
          text: 'Platelet satellitism is a rare finding',
          color: 'RED' as const,
          note: '',
          createdAt: '2026-01-01',
        },
      ],
    };
    const hits = globalSearch('satellitism', state as never);
    expect(hits.some((h) => h.kind === 'highlight')).toBe(true);
  });
});

describe('tts text cleanup', () => {
  it('strips markdown formatting', () => {
    expect(stripMarkdown('**bold** and `code`')).toBe('bold and code');
    expect(stripMarkdown('# Heading\n\ntext')).toBe('Heading text');
    expect(stripMarkdown('[link](https://x.com)')).toBe('link');
  });
});
