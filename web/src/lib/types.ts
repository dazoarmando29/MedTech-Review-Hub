// Domain + content types. Content shapes follow shared/CONTENT_SCHEMA.md;
// API shapes follow shared/api/API.md.

export type ExamTarget = 'MLS_ASCPI' | 'PH_BOARD' | 'CUSTOM';
export type KnowledgeLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
export type ExplanationStyle = 'FORMAL_ENGLISH' | 'SIMPLE_ENGLISH' | 'TAGLISH';
export type LearningPref = 'reading' | 'flashcards' | 'quizzes' | 'diagrams' | 'mixed';

export interface Profile {
  examTarget: ExamTarget;
  targetExamDate: string | null;
  knowledgeLevel: KnowledgeLevel;
  strongestSubjects: string[];
  weakestSubjects: string[];
  minutesPerDay: number;
  preferredSchedule: Record<string, [number, number][]>;
  learningPreferences: LearningPref[];
  explanationStyle: ExplanationStyle;
  notificationsEnabled: boolean;
  focusFeatureEnabled: boolean;
  onboardingCompleted: boolean;
}

export type SubjectColor = 'blue' | 'teal' | 'violet' | 'amber' | 'rose' | 'cyan' | 'green';

export interface Topic {
  id: string;
  name: string;
  lessonIds: string[];
}

export interface Subject {
  id: string;
  name: string;
  shortName: string;
  description: string;
  blueprintNote: string;
  colorToken: SubjectColor;
  topics: Topic[];
}

export interface TableData {
  columns: string[];
  rows: string[][];
}

export interface LessonSection {
  heading: string;
  body: string;
  diagram?: string | null;
}

export interface MemoryHook {
  type: string;
  text: string;
  funny?: string;
}

export interface LessonReference {
  title: string;
  detail?: string | null;
  url?: string | null;
}

export interface Lesson {
  id: string;
  subjectId: string;
  topicId: string;
  title: string;
  summary: string;
  readingMinutes: number;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  keywords: string[];
  objectives: string[];
  quickReview: {
    highYield: string[];
    mustKnowTable: TableData;
    mnemonic: string;
  };
  deepStudy: {
    introduction: string;
    sections: LessonSection[];
    clinicalSignificance: string;
    labInterpretation: string;
    comparisonTables: TableData[];
    examTraps: string[];
    workedExample?: string | null;
  };
  memoryHooks: MemoryHook[];
  practiceQuestionIds: string[];
  relatedFlashcardIds: string[];
  followUp: string[];
  references: LessonReference[];
  editorialStatus: 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
  reviewedAt: string;
  reviewerNote: string;
}

export interface Choice {
  id: string;
  text: string;
}

export interface QuizQuestion {
  id: string;
  subjectId: string;
  topicId: string;
  type: 'MCQ' | 'IMAGE' | 'SCENARIO';
  vignette: string | null;
  stem: string;
  choices: Choice[];
  correctChoiceId: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  explanation: string;
  rationales: Record<string, string>;
  misconception: string;
  reference: string;
  lessonId: string | null;
  examTraps?: string[];
}

export type FlashcardType = 'BASIC' | 'CLOZE' | 'IMAGE' | 'KEYWORD_RECALL';

export interface FlashcardSource {
  kind: 'MANUAL' | 'PDF_HIGHLIGHT' | 'QUIZ_MISS' | 'AI' | 'MEME';
  lessonId?: string | null;
  pdfId?: string | null;
  page?: number | null;
  needsReview?: boolean;
}

export interface FlashcardSeed {
  id: string;
  type: FlashcardType;
  front: string;
  back: string;
  tags: string[];
  difficulty: 1 | 2 | 3;
  note?: string;
  source: FlashcardSource;
}

export interface Deck {
  id: string;
  title: string;
  subjectId: string;
  cards: FlashcardSeed[];
}

export interface Formula {
  id: string;
  title: string;
  formula: string;
  variables: { name: string; unit?: string }[];
  example: string;
  caveat: string;
  reference: string;
}

export interface MemeEntry {
  id: string;
  type: 'MEME_OF_THE_LESSON' | 'MEMORY_HOOK' | 'KEYWORD_CHALLENGE' | 'GUESS_THE_DIAGNOSIS' |
        'LABORATORY_MYSTERY' | 'IMPOSTOR' | 'BOSS_BATTLE' | 'MISTAKE_OF_THE_DAY';
  subjectId: string;
  title: string;
  body: string;
  punchline: string;
  scientificFact: string;
  lessonId: string | null;
}

export interface GlossaryTerm {
  id: string;
  term: string;
  acronym?: string;
  definition: string;
  subjectId: string;
  example?: string;
  source: string;
}

// ---------- study state ----------

export type SrsState = 'NEW' | 'LEARNING' | 'REVIEW' | 'RELEARNING';
export type Rating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';

export interface SrsSchedule {
  state: SrsState;
  stability: number;
  difficulty: number;
  dueAt: string;
  lastReviewAt: string | null;
  reps: number;
  lapses: number;
}

export interface ReviewRecord {
  at: string;
  rating: Rating;
  intervalDays: number;
}

export interface UserCard {
  id: string;
  deckId: string;
  front: string;
  back: string;
  type: FlashcardType;
  tags: string[];
  subjectId: string;
  topicId: string | null;
  difficulty: 1 | 2 | 3;
  note: string;
  favorite: boolean;
  imageRef: string | null;
  source: FlashcardSource;
  schedule: SrsSchedule;
  history: ReviewRecord[];
}

export interface QuizAnswerRecord {
  questionId: string;
  chosenId: string;
  correct: boolean;
  seconds: number;
}

export interface QuizAttempt {
  id: string;
  startedAt: string;
  finishedAt: string | null;
  mode: 'STUDY' | 'EXAM' | 'ADAPTIVE' | 'MOCK';
  filters: { subjectId?: string | null; topicId?: string | null; label: string };
  questionIds: string[];
  answers: QuizAnswerRecord[];
  timeLimitSeconds: number | null;
  finished: boolean;
}

export interface StudySession {
  id: string;
  startedAt: string;
  kind: 'READ' | 'FLASHCARDS' | 'QUIZ' | 'FOCUS' | 'PDF';
  subjectId: string | null;
  minutes: number;
  itemsCompleted: number;
}

export interface Habit {
  id: string;
  name: string;
  kind: 'MINUTES' | 'COUNT';
  targetPerDay: number;
  createdAt: string;
  archived: boolean;
}

export interface DiaryEntry {
  id: string;
  date: string;
  mood: 'great' | 'good' | 'okay' | 'tired' | 'struggling';
  text: string;
  tags: string[];
  createdAt: string;
}

export interface PlanItem {
  id: string;
  type: 'READ' | 'FLASHCARDS' | 'QUIZ' | 'REVIEW_WRONG' | 'FORMULAS';
  subjectId: string;
  topicId: string | null;
  lessonId: string | null;
  targetMinutes: number;
  completed: boolean;
}

export interface PlanDay {
  date: string;
  items: PlanItem[];
}

export interface StudyPlan {
  generatedAt: string;
  examTarget: ExamTarget;
  days: PlanDay[];
}

export type HighlightColor = 'YELLOW' | 'RED' | 'BLUE' | 'GREEN' | 'PURPLE';

export interface PdfAnnotation {
  id: string;
  pdfId: string;
  page: number;
  type: 'HIGHLIGHT' | 'NOTE' | 'BOOKMARK';
  color: HighlightColor;
  selectedText: string;
  note: string;
  createdAt: string;
}

export interface PdfDocMeta {
  id: string;
  name: string;
  subjectId: string | null;
  pageCount: number;
  lastPage: number;
  addedAt: string;
  sizeBytes: number;
}

export interface HighlightEntry {
  id: string;
  lessonId: string | null;
  text: string;
  color: HighlightColor;
  note: string;
  createdAt: string;
}

export interface LessonProgress {
  lastSection: number;
  percent: number;
  bookmarked: boolean;
  completedAt: string | null;
}

export interface FocusSession {
  id: string;
  startedAt: string;
  minutes: number;
  completed: boolean;
  kind: 'POMODORO';
}

export interface Settings {
  theme: 'system' | 'light' | 'dark';
  textScale: 100 | 115 | 130 | 150;
  seriousMode: boolean;
  reduceMotion: boolean;
  mascot: 'cat' | 'dog';
  mascotCommentary: boolean;
  explanationStyle: ExplanationStyle;
  ttsEnabled: boolean;
  apiBaseUrl: string;
  dailyNewCards: number;
  dailyReviewLimit: number;
  targetRetention: number;
  highlightColors: Record<HighlightColor, string>;
}

export interface ContentIssueReport {
  id: string;
  targetKind: 'QUESTION' | 'LESSON' | 'FLASHCARD' | 'OTHER';
  targetId: string;
  reason: string;
  detail: string;
  createdAt: string;
  status: 'OPEN' | 'RESOLVED';
}
