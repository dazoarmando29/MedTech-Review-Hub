import { describe, expect, it } from 'vitest';
import { parseFlashcardsFromAi, parseQuestionsFromAi } from '../aiParse';
import { resolveQuestion, toQuizQuestion } from '../questions';
import { addCustomQuestions, getState } from '../store';

describe('AI flashcard parsing', () => {
  it('parses strict JSON output', () => {
    const text = JSON.stringify({
      flashcards: [
        { front: 'Which antigen is on group A cells?', back: 'A antigen', quote: 'Group A red cells carry the A antigen' },
        { front: 'What is in group A plasma?', back: 'anti-B' },
      ],
    });
    const cards = parseFlashcardsFromAi(text);
    expect(cards.length).toBe(2);
    expect(cards[0].front).toContain('group A');
    expect(cards[0].quote).toContain('A antigen');
  });

  it('parses JSON wrapped in code fences', () => {
    const text = '```json\n{"flashcards":[{"front":"Q1","back":"A1"}]}\n```';
    expect(parseFlashcardsFromAi(text).length).toBe(1);
  });

  it('falls back to Q:/A: plain text', () => {
    const text = 'Q: What stains green?\nA: Malachite green.\nQ: Second?\nA: Answer two.';
    const cards = parseFlashcardsFromAi(text);
    expect(cards.length).toBe(2);
    expect(cards[0].back).toBe('Malachite green.');
  });

  it('returns empty for unusable text instead of crashing', () => {
    expect(parseFlashcardsFromAi('just some prose')).toEqual([]);
    expect(parseFlashcardsFromAi('')).toEqual([]);
  });
});

describe('AI quiz question parsing', () => {
  it('parses strict JSON with per-letter choices and builds full questions', () => {
    const text = JSON.stringify({
      questions: [
        {
          stem: 'Group B cells carry which antigen?',
          vignette: null,
          choices: { A: 'A antigen', B: 'B antigen', C: 'Both', D: 'Neither' },
          correct: 'B',
          rationale: 'Group B red cells carry B antigen.',
          misconception: 'Students swap the plasma antibodies with the cell antigens.',
          quote: 'Group B cells carry the B antigen',
        },
      ],
    });
    const qs = parseQuestionsFromAi(text);
    expect(qs.length).toBe(1);
    expect(qs[0].choices.length).toBe(4);
    expect(qs[0].correctChoiceId).toBe('B');

    const full = toQuizQuestion(qs[0], 'my PDF', 't1');
    expect(full.choices.map((c) => c.id)).toEqual(['A', 'B', 'C', 'D']);
    expect(full.correctChoiceId).toBe('B');
    expect(full.rationales.A).toContain('plasma antibodies');
    expect(full.reference).toContain('AI-generated');
  });

  it('accepts array-style choices and correct-by-text', () => {
    const text = JSON.stringify({
      questions: [
        { stem: 'Stem?', choices: ['one', 'two', 'three', 'four'], correct: 'three', rationale: 'r', misconception: 'm' },
      ],
    });
    const qs = parseQuestionsFromAi(text);
    expect(qs[0].correctChoiceId).toBe('C');
    expect(qs[0].choices[1]).toEqual({ id: 'B', text: 'two' });
  });

  it('drops malformed questions and never crashes', () => {
    expect(parseQuestionsFromAi('no json here')).toEqual([]);
    expect(parseQuestionsFromAi('{"questions":[{"stem":"","choices":{},"correct":"A"}]}')).toEqual([]);
  });
});

describe('custom question resolution (Anki-style generated quizzes)', () => {
  it('resolves generated questions alongside the bank and reuses them', () => {
    const parsed = parseQuestionsFromAi(JSON.stringify({
      questions: [{ stem: 'Custom stem?', choices: { A: 'x', B: 'y', C: 'z', D: 'w' }, correct: 'A', rationale: 'because', misconception: '' }],
    }));
    const q = toQuizQuestion(parsed[0], 'test', 'abc');
    addCustomQuestions([q]);

    expect(resolveQuestion(q.id)?.stem).toBe('Custom stem?');
    // bank question still resolves
    expect(resolveQuestion('bb-q-001')).toBeTruthy();
    // duplicates are not re-added
    const before = getState().customQuestions.length;
    addCustomQuestions([q]);
    expect(getState().customQuestions.length).toBe(before);
  });
});
