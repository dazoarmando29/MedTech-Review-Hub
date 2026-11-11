// Question resolution: content bank first, then user/AI-generated questions
// (from PDFs, review notes, etc.) so generated quizzes behave like bank quizzes.
import { getQuestion as getBankQuestion } from './content';
import { getState } from './store';
import type { QuizQuestion } from './types';
import type { ParsedQuestion } from './aiParse';

export function resolveQuestion(id: string): QuizQuestion | undefined {
  return getState().customQuestions.find((q) => q.id === id) ?? getBankQuestion(id);
}

/** Converts an AI-parsed question into a full QuizQuestion for the engine. */
export function toQuizQuestion(parsed: ParsedQuestion, sourceLabel: string, seed: string): QuizQuestion {
  return {
    id: `aiq-${seed}-${Math.random().toString(36).slice(2, 7)}`,
    subjectId: 'custom',
    topicId: 'from-your-material',
    type: 'MCQ',
    vignette: parsed.vignette,
    stem: parsed.stem,
    choices: parsed.choices,
    correctChoiceId: parsed.correctChoiceId,
    difficulty: 'MEDIUM',
    explanation:
      parsed.rationale ||
      `Correct answer: ${parsed.choices.find((c) => c.id === parsed.correctChoiceId)?.text ?? ''}. ${parsed.quote ? `Source: “${parsed.quote}”` : ''}`,
    rationales: Object.fromEntries(
      parsed.choices.map((c) => [
        c.id,
        c.id === parsed.correctChoiceId
          ? parsed.rationale || 'This is the best answer supported by the source text.'
          : parsed.misconception
            ? `Not the best answer here. ${parsed.misconception}`
            : 'Not the best answer supported by the source text.',
      ]),
    ),
    misconception: parsed.misconception || 'Compare each choice against the source text — the wording is the clue.',
    reference: `${sourceLabel} (AI-generated from your document — verify before memorizing)`,
    lessonId: null,
  };
}
