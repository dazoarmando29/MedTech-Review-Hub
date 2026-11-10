package com.medtech.reviewhub.domain.model

import com.medtech.reviewhub.domain.CardState
import com.medtech.reviewhub.domain.ReviewLogEntry
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Content models (shared/content JSON — see shared/CONTENT_SCHEMA.md).
// Annotated with kotlinx.serialization so the bundled assets parse directly
// into the same shapes the website and backend use.
// ---------------------------------------------------------------------------

@Serializable
data class ContentManifest(
    val subjects: List<SubjectRef> = emptyList(),
    val version: String = "1"
)

@Serializable
data class SubjectRef(val id: String, val name: String)

@Serializable
data class Subject(
    val id: String,
    val name: String,
    val shortName: String = name,
    val description: String = "",
    val blueprintNote: String = "",
    val colorToken: String = "blue",
    val topics: List<Topic> = emptyList()
)

@Serializable
data class Topic(
    val id: String,
    val name: String,
    val lessonIds: List<String> = emptyList()
)

@Serializable
data class Lesson(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val summary: String = "",
    val readingMinutes: Int = 10,
    val difficulty: String = "INTERMEDIATE",
    val keywords: List<String> = emptyList(),
    val objectives: List<String> = emptyList(),
    val quickReview: QuickReview = QuickReview(),
    val deepStudy: DeepStudy = DeepStudy(),
    val memoryHooks: List<MemoryHook> = emptyList(),
    val practiceQuestionIds: List<String> = emptyList(),
    val relatedFlashcardIds: List<String> = emptyList(),
    val followUp: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val editorialStatus: String = "PUBLISHED",
    val reviewedAt: String? = null,
    val reviewerNote: String? = null
)

@Serializable
data class QuickReview(
    val highYield: List<String> = emptyList(),
    val mustKnowTable: Table? = null,
    val mnemonic: String = ""
)

@Serializable
data class DeepStudy(
    val introduction: String = "",
    val sections: List<LessonSection> = emptyList(),
    val clinicalSignificance: String = "",
    val labInterpretation: String = "",
    val comparisonTables: List<Table> = emptyList(),
    val examTraps: List<String> = emptyList(),
    val workedExample: String? = null
)

@Serializable
data class LessonSection(val heading: String, val body: String, val diagram: String? = null)

@Serializable
data class Table(val title: String = "", val columns: List<String> = emptyList(), val rows: List<List<String>> = emptyList())

@Serializable
data class MemoryHook(val type: String = "MNEMONIC", val text: String = "", val funny: String = "")

@Serializable
data class Reference(val title: String, val detail: String = "", val url: String? = null)

// ---------------------------------------------------------------------------
// Quiz questions (shared/content/questions/<subject>.json)
// ---------------------------------------------------------------------------

@Serializable
data class QuizQuestion(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val type: String = "MCQ",
    val vignette: String? = null,
    val stem: String,
    val choices: List<Choice> = emptyList(),
    val correctChoiceId: String,
    val difficulty: String = "MEDIUM",
    val explanation: String = "",
    val rationales: Map<String, String> = emptyMap(),
    val misconception: String = "",
    val reference: String = "",
    val lessonId: String? = null,
    val examTraps: List<String> = emptyList()
)

@Serializable
data class Choice(val id: String, val text: String)

// ---------------------------------------------------------------------------
// Flashcards (shared/content/flashcards/<deck>.json)
// ---------------------------------------------------------------------------

@Serializable
data class FlashcardDeckFile(
    val id: String,
    val title: String,
    val subjectId: String? = null,
    val cards: List<ContentFlashcard> = emptyList()
)

@Serializable
data class ContentFlashcard(
    val id: String,
    val type: String = "BASIC",
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    val difficulty: Int = 1,
    val note: String = "",
    val source: CardSource? = null
)

@Serializable
data class CardSource(
    val kind: String = "MANUAL",
    val lessonId: String? = null,
    val pdfId: String? = null,
    val page: Int? = null,
    val needsReview: Boolean = false
)

// ---------------------------------------------------------------------------
// Formulas / memes / glossary
// ---------------------------------------------------------------------------

@Serializable
data class FormulaEntry(
    val id: String,
    val title: String,
    val formula: String,
    val variables: List<FormulaVariable> = emptyList(),
    val example: String = "",
    val caveat: String = "",
    val reference: String = ""
)

@Serializable
data class FormulaVariable(val name: String, val unit: String = "")

@Serializable
data class MemeEntry(
    val id: String,
    val type: String,
    val subjectId: String? = null,
    val title: String,
    val body: String = "",
    val punchline: String = "",
    val scientificFact: String = "",
    val lessonId: String? = null
)

@Serializable
data class GlossaryEntry(val id: String = "", val term: String, val definition: String, val subjectId: String? = null)

@Serializable
data class GlossaryFile(val entries: List<GlossaryEntry> = emptyList())

@Serializable
data class FormulaFile(val entries: List<FormulaEntry> = emptyList())

@Serializable
data class MemeFile(val entries: List<MemeEntry> = emptyList())

// ---------------------------------------------------------------------------
// Flashcard schedule (matches shared/api/API.md Flashcard.schedule).
// [stepIndex] is an extra local field for learning steps; unknown to the API
// (the backend ignores unknown fields).
// ---------------------------------------------------------------------------

@Serializable
data class CardSchedule(
    val state: CardState = CardState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val dueAtMs: Long = 0L,
    val lastReviewAtMs: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val stepIndex: Int = 0
)

// ---------------------------------------------------------------------------
// Study plan (matches shared/api/API.md StudyPlan)
// ---------------------------------------------------------------------------

enum class PlanItemType { READ, FLASHCARDS, QUIZ, REVIEW_WRONG, FORMULAS }

@Serializable
data class PlanItem(
    val id: String,
    val type: PlanItemType,
    val subjectId: String?,
    val topicId: String?,
    val lessonId: String?,
    val targetMinutes: Int,
    val completed: Boolean = false
)

@Serializable
data class PlanDay(val date: String, val items: List<PlanItem>)

@Serializable
data class StudyPlan(val id: String, val generatedAt: String, val days: List<PlanDay>)

// ---------------------------------------------------------------------------
// User profile (matches shared/api/API.md Profile)
// ---------------------------------------------------------------------------

enum class ExamTarget { MLS_ASCPI, PH_BOARD, CUSTOM }
enum class KnowledgeLevel { BEGINNER, INTERMEDIATE, ADVANCED }
enum class ExplanationStyle { FORMAL_ENGLISH, SIMPLE_ENGLISH, TAGLISH }

@Serializable
data class UserProfile(
    val examTarget: ExamTarget = ExamTarget.MLS_ASCPI,
    val targetExamDate: String? = null,
    val knowledgeLevel: KnowledgeLevel = KnowledgeLevel.BEGINNER,
    val strongestSubjects: List<String> = emptyList(),
    val weakestSubjects: List<String> = emptyList(),
    val minutesPerDay: Int = 60,
    val preferredSchedule: Map<String, List<List<Int>>> = emptyMap(),
    val learningPreferences: List<String> = emptyList(),
    val explanationStyle: ExplanationStyle = ExplanationStyle.FORMAL_ENGLISH,
    val notificationsEnabled: Boolean = true,
    val focusFeatureEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)

// ---------------------------------------------------------------------------
// Habits / diary / streak
// ---------------------------------------------------------------------------

enum class HabitKind { MINUTES, COUNT }

enum class Mood(val label: String) {
    GREAT("Great"),
    GOOD("Good"),
    OKAY("Okay"),
    LOW("Low"),
    TIRED("Tired")
}

data class Habit(val id: String, val name: String, val targetPerDay: Int, val kind: HabitKind, val archived: Boolean = false)

data class HabitLog(val habitId: String, val date: String, val amount: Int)

data class DiaryEntry(val id: String, val date: String, val mood: Mood, val text: String, val tags: List<String> = emptyList())

data class StreakInfo(val current: Int, val best: Int, val restDayMessage: String?)

// ---------------------------------------------------------------------------
// PDF documents & annotations (matches shared/api/API.md Annotation)
// ---------------------------------------------------------------------------

enum class AnnotationType { HIGHLIGHT, NOTE, BOOKMARK }
enum class HighlightColor { YELLOW, RED, BLUE, GREEN, PURPLE }

data class NormalizedRect(val x: Float, val y: Float, val w: Float, val h: Float)

@Serializable
data class FlashcardRecord(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val type: String = "BASIC",
    val tags: List<String> = emptyList(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: Int = 1,
    val note: String = "",
    val favorite: Boolean = false,
    val imageRef: String? = null,
    val sourceKind: String = "MANUAL",
    val sourceLessonId: String? = null,
    val sourcePdfId: String? = null,
    val sourcePage: Int? = null,
    val sourceNeedsReview: Boolean = false,
    val schedule: CardSchedule = CardSchedule(),
    val history: List<ReviewLogEntry> = emptyList(),
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class PdfDocumentMeta(
    val id: String,
    val displayName: String,
    val localPath: String,
    val folder: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastOpenedAt: Long?
)

data class PdfAnnotation(
    val id: String,
    val pdfId: String,
    val page: Int,
    val type: AnnotationType,
    val color: HighlightColor,
    val selectedText: String,
    val note: String,
    val rect: NormalizedRect?,
    val createdAt: Long
)

// ---------------------------------------------------------------------------
// Quiz session results (matches shared/api/API.md QuizResult)
// ---------------------------------------------------------------------------

data class SubjectScore(val subjectId: String, val correct: Int, val total: Int)

data class WeakTopic(val topicId: String, val accuracy: Double)

data class QuizResult(
    val score: Double,
    val correct: Int,
    val total: Int,
    val timeSeconds: Int,
    val bySubject: List<SubjectScore>,
    val weakTopics: List<WeakTopic>,
    val incorrectQuestionIds: List<String>
)

// ---------------------------------------------------------------------------
// Focus sessions (Android-only)
// ---------------------------------------------------------------------------

enum class PomodoroPreset(val workMinutes: Int, val breakMinutes: Int, val label: String) {
    POMODORO_25(25, 5, "25 / 5"),
    POMODORO_50(50, 10, "50 / 10"),
    CUSTOM(25, 5, "Custom")
}

data class FocusSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val presetLabel: String,
    val interruptions: Int
)

data class InstalledAppInfo(val packageName: String, val label: String)
