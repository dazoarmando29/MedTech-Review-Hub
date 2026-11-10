// Access to the shared content library (synced from shared/content at build time).
import { contentFiles } from '@/data/content';
import type {
  Deck, Formula, GlossaryTerm, Lesson, MemeEntry, MotivationBank, MusicCatalog,
  QuizQuestion, ResourcesDoc, Subject, VisualIdItem,
} from './types';

function pick<T>(predicate: (path: string) => boolean): T[] {
  const out: T[] = [];
  for (const f of contentFiles) {
    if (!predicate(f.path)) continue;
    const d = f.data as T | T[];
    if (Array.isArray(d)) out.push(...d);
    else out.push(d);
  }
  return out;
}

export const subjects: Subject[] = pick<Subject>((p) => p.startsWith('./subjects/'));
export const lessons: Lesson[] = pick<Lesson>((p) => p.startsWith('./lessons/'));
export const questionBank: QuizQuestion[] = pick<QuizQuestion>((p) => p.startsWith('./questions/'));
export const decks: Deck[] = pick<Deck>((p) => p.startsWith('./flashcards/'));
export const formulas: Formula[] = pick<Formula>((p) => p === './formulas.json');
export const memes: MemeEntry[] = pick<MemeEntry>((p) => p === './memes.json');
export const glossarySeed: GlossaryTerm[] = pick<GlossaryTerm>((p) => p === './glossary.json');
export const visualIdBank: VisualIdItem[] = pick<VisualIdItem>((p) => p === './visual-id.json');

export const resourcesDoc: ResourcesDoc =
  pick<ResourcesDoc>((p) => p === './resources.json')[0] ??
  { reviewCenters: [], officialLinks: [], readingList: [], examInfo: [], disclaimer: '' };

export const motivation: MotivationBank =
  pick<MotivationBank>((p) => p === './motivation.json')[0] ??
  { dailyQuotes: [], contextQuotes: {}, mascotLines: {} };

export const musicCatalog: MusicCatalog =
  pick<MusicCatalog>((p) => p === './music/catalog.json')[0] ??
  { note: '', categories: [], tracks: [], suggestedPlaylists: [] };

export function getSubject(id: string): Subject | undefined {
  return subjects.find((s) => s.id === id);
}

export function getLesson(id: string): Lesson | undefined {
  return lessons.find((l) => l.id === id);
}

export function lessonsForSubject(subjectId: string): Lesson[] {
  return lessons.filter((l) => l.subjectId === subjectId);
}

export function questionsForSubject(subjectId: string): QuizQuestion[] {
  return questionBank.filter((q) => q.subjectId === subjectId);
}

export function getQuestion(id: string): QuizQuestion | undefined {
  return questionBank.find((q) => q.id === id);
}

export function allTags(): string[] {
  const set = new Set<string>();
  decks.forEach((d) => d.cards.forEach((c) => c.tags.forEach((t) => set.add(t))));
  return [...set].sort();
}

/** Approximate content-outline weights used by the study planner (documented as approximate). */
export const SUBJECT_WEIGHTS_ASCPI: Record<string, number> = {
  'blood-banking': 0.09,
  'clinical-chemistry': 0.17,
  hematology: 0.16,
  microbiology: 0.18,
  immunology: 0.1,
  urinalysis: 0.1,
  'lab-operations': 0.12,
};

export const SUBJECT_WEIGHTS_PH: Record<string, number> = {
  'blood-banking': 0.1,
  'clinical-chemistry': 0.15,
  hematology: 0.15,
  microbiology: 0.2,
  immunology: 0.1,
  urinalysis: 0.15,
  'lab-operations': 0.15,
};
