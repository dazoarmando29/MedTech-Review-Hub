package io.medtech.backend.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ExamTarget { MLS_ASCPI, PH_BOARD, CUSTOM }

@Serializable
enum class KnowledgeLevel { BEGINNER, INTERMEDIATE, ADVANCED }

@Serializable
enum class ExplanationStyle { FORMAL_ENGLISH, SIMPLE_ENGLISH, TAGLISH }

@Serializable
enum class PlanItemType { READ, FLASHCARDS, QUIZ, REVIEW_WRONG, FORMULAS }

@Serializable
enum class SessionKind { READ, FLASHCARDS, QUIZ, FOCUS, PDF }

@Serializable
enum class FlashcardType { BASIC, CLOZE, IMAGE, KEYWORD_RECALL }

@Serializable
enum class FlashcardSourceKind { MANUAL, PDF_HIGHLIGHT, QUIZ_MISS, AI, MEME }

@Serializable
enum class ScheduleState { NEW, LEARNING, REVIEW, RELEARNING }

@Serializable
enum class Rating { AGAIN, HARD, GOOD, EASY }

@Serializable
enum class QuizDifficulty { EASY, MEDIUM, HARD }

@Serializable
enum class QuizType { MCQ, IMAGE, SCENARIO }

@Serializable
enum class QuizMode { STUDY, EXAM, ADAPTIVE }

@Serializable
enum class QuizStatus { IN_PROGRESS, COMPLETED }

@Serializable
enum class AnnotationType { HIGHLIGHT, NOTE, BOOKMARK }

@Serializable
enum class AnnotationColor { YELLOW, RED, BLUE, GREEN, PURPLE }

@Serializable
enum class HabitKind { MINUTES, COUNT }

@Serializable
enum class EditorialStatus { DRAFT, IN_REVIEW, PUBLISHED, ARCHIVED }

@Serializable
enum class AiMode { PAGE, PASSAGE, CHAPTER }

@Serializable
enum class AiStyle { SIMPLE_ENGLISH, TAGLISH }

@Serializable
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)

@Serializable
data class Profile(
    val examTarget: ExamTarget = ExamTarget.CUSTOM,
    val targetExamDate: LocalDate? = null,
    val knowledgeLevel: KnowledgeLevel = KnowledgeLevel.BEGINNER,
    val strongestSubjects: List<String> = emptyList(),
    val weakestSubjects: List<String> = emptyList(),
    val minutesPerDay: Int = 60,
    @Serializable(with = ScheduleWindowsSerializer::class)
    val preferredSchedule: Map<String, List<List<Int>>> = emptyMap(),
    val learningPreferences: List<String> = emptyList(),
    val explanationStyle: ExplanationStyle = ExplanationStyle.FORMAL_ENGLISH,
    val notificationsEnabled: Boolean = true,
    val focusFeatureEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
)

@Serializable
data class Preferences(
    val avatarMascot: String = "cat",
    val locale: String = "en",
    val theme: String = "system",
    val seriousMode: Boolean = false,
)

@Serializable
data class UserResponse(
    val user: User,
    val profile: Profile? = null,
    val preferences: Preferences? = null,
)

@Serializable
data class AuthResponse(
    val user: User,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 3600,
)

@Serializable
data class StatusResponse(val status: String = "ok")

@Serializable
data class PlanItem(
    val id: String,
    val type: PlanItemType,
    val subjectId: String? = null,
    val topicId: String? = null,
    val lessonId: String? = null,
    val targetMinutes: Int = 30,
    val completed: Boolean = false,
)

@Serializable
data class PlanDay(
    val date: LocalDate,
    val items: List<PlanItem> = emptyList(),
)

@Serializable
data class StudyPlan(
    val id: String,
    val generatedAt: Instant,
    val days: List<PlanDay> = emptyList(),
)

@Serializable
data class PlanReplaceRequest(val days: List<PlanDay>)

@Serializable
data class StudySession(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val kind: SessionKind,
    val subjectId: String? = null,
    val minutes: Int = 0,
    val itemsCompleted: Int = 0,
)

@Serializable
data class SessionCreateRequest(
    val startedAt: Instant,
    val endedAt: Instant,
    val kind: SessionKind,
    val subjectId: String? = null,
    val itemsCompleted: Int = 0,
)

@Serializable
data class DayMinutes(val date: LocalDate, val minutes: Int)

@Serializable
data class TopicMastery(val topicId: String, val subjectId: String, val mastery: Double)

@Serializable
data class AnalyticsOverview(
    val streakDays: Int = 0,
    val totalMinutes: Long = 0,
    val minutesToday: Int = 0,
    val flashcardsDueToday: Int = 0,
    val questionsAnsweredToday: Int = 0,
    val recentQuizAccuracy: Double = 0.0,
    val weakestSubjectId: String? = null,
    val examCountdownDays: Int? = null,
    val weeklyCalendar: List<DayMinutes> = emptyList(),
    val mastery: List<TopicMastery> = emptyList(),
)

@Serializable
data class FlashcardSource(
    val kind: FlashcardSourceKind = FlashcardSourceKind.MANUAL,
    val lessonId: String? = null,
    val pdfId: String? = null,
    val page: Int? = null,
    val needsReview: Boolean = false,
)

@Serializable
data class FlashcardSchedule(
    val state: ScheduleState = ScheduleState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val dueAt: Instant,
    val lastReviewAt: Instant? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
)

@Serializable
data class FlashcardHistoryEntry(
    val at: Instant,
    val rating: Rating,
    val intervalDays: Double,
)

@Serializable
data class Flashcard(
    val id: String,
    val deckId: String? = null,
    val front: String,
    val back: String,
    val type: FlashcardType = FlashcardType.BASIC,
    val tags: List<String> = emptyList(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: Int = 1,
    val note: String = "",
    val favorite: Boolean = false,
    val imageRef: String? = null,
    val source: FlashcardSource = FlashcardSource(),
    val schedule: FlashcardSchedule,
    val history: List<FlashcardHistoryEntry> = emptyList(),
)

@Serializable
data class FlashcardCreateRequest(
    val deckId: String? = null,
    val deckTitle: String? = null,
    val front: String,
    val back: String,
    val type: FlashcardType = FlashcardType.BASIC,
    val tags: List<String> = emptyList(),
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: Int = 1,
    val note: String = "",
    val favorite: Boolean = false,
    val imageRef: String? = null,
    val source: FlashcardSource = FlashcardSource(),
)

@Serializable
data class FlashcardUpdateRequest(
    val deckId: String? = null,
    val front: String? = null,
    val back: String? = null,
    val type: FlashcardType? = null,
    val tags: List<String>? = null,
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: Int? = null,
    val note: String? = null,
    val favorite: Boolean? = null,
    val imageRef: String? = null,
    val source: FlashcardSource? = null,
)

@Serializable
data class ReviewRequest(
    val rating: Rating,
    val reviewedAt: Instant? = null,
)

@Serializable
data class ReviewResponse(val schedule: FlashcardSchedule)

@Serializable
data class FlashcardQueue(
    val new: List<Flashcard> = emptyList(),
    val learning: List<Flashcard> = emptyList(),
    val review: List<Flashcard> = emptyList(),
)

@Serializable
data class ImportRow(
    val front: String,
    val back: String,
    val deckId: String? = null,
    val deckTitle: String? = null,
    val tags: List<String> = emptyList(),
    val subjectId: String? = null,
    val topicId: String? = null,
)

@Serializable
data class ImportRequest(val rows: List<ImportRow>)

@Serializable
data class ImportResult(val imported: Int)

@Serializable
data class QuizChoice(val id: String, val text: String)

@Serializable
data class QuizQuestion(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val vignette: String? = null,
    val stem: String,
    val choices: List<QuizChoice>,
    val correctChoiceId: String? = null,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    val explanation: String? = null,
    val rationales: Map<String, String>? = null,
    val misconception: String? = null,
    val reference: String? = null,
    val lessonId: String? = null,
    val type: QuizType = QuizType.MCQ,
)

@Serializable
data class BySubjectStat(val subjectId: String, val correct: Int, val total: Int)

@Serializable
data class WeakTopic(val topicId: String, val accuracy: Double)

@Serializable
data class QuizResult(
    val score: Double,
    val correct: Int,
    val total: Int,
    val timeSeconds: Long,
    val bySubject: List<BySubjectStat> = emptyList(),
    val weakTopics: List<WeakTopic> = emptyList(),
    val incorrectQuestionIds: List<String> = emptyList(),
)

@Serializable
data class QuizStartRequest(
    val mode: QuizMode = QuizMode.STUDY,
    val questionCount: Int? = null,
    val subjectIds: List<String>? = null,
    val topicIds: List<String>? = null,
    val questionIds: List<String>? = null,
)

@Serializable
data class QuizAttemptView(
    val id: String,
    val mode: QuizMode,
    val status: QuizStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val answeredCount: Int = 0,
    val totalQuestions: Int = 0,
    val result: QuizResult? = null,
)

@Serializable
data class QuizAttemptSummary(
    val id: String,
    val mode: QuizMode,
    val status: QuizStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val score: Double? = null,
    val correct: Int = 0,
    val total: Int = 0,
)

@Serializable
data class QuizAnswerRequest(val questionId: String, val choiceId: String)

@Serializable
data class QuizAnswerFeedback(
    val accepted: Boolean = true,
    val correct: Boolean? = null,
    val correctChoiceId: String? = null,
    val explanation: String? = null,
    val rationales: Map<String, String>? = null,
    val misconception: String? = null,
)

@Serializable
data class ReportRequest(val reason: String = "OTHER", val details: String = "")

@Serializable
data class ReportResponse(val id: String, val status: String = "OPEN")

@Serializable
data class Rect(val x: Double, val y: Double, val w: Double, val h: Double)

@Serializable
data class Annotation(
    val id: String,
    val pdfId: String,
    val page: Int,
    val type: AnnotationType = AnnotationType.HIGHLIGHT,
    val color: AnnotationColor = AnnotationColor.YELLOW,
    val selectedText: String = "",
    val note: String = "",
    val rect: Rect? = null,
    val createdAt: Instant,
)

@Serializable
data class AnnotationInput(
    val id: String? = null,
    val page: Int,
    val type: AnnotationType = AnnotationType.HIGHLIGHT,
    val color: AnnotationColor = AnnotationColor.YELLOW,
    val selectedText: String = "",
    val note: String = "",
    val rect: Rect? = null,
)

@Serializable
data class AnnotationReplaceRequest(val annotations: List<AnnotationInput>)

@Serializable
data class PdfDocumentView(
    val id: String,
    val filename: String,
    val contentType: String = "application/pdf",
    val sizeBytes: Long,
    val uploadedAt: Instant,
    val annotationCount: Int = 0,
)

@Serializable
data class AiCitation(val pdfId: String? = null, val page: Int? = null, val quote: String = "")

@Serializable
data class AiUsage(val cached: Boolean, val tokensUsed: Int = 0)

@Serializable
data class AiEnvelope(
    val result: JsonObject,
    val citations: List<AiCitation> = emptyList(),
    val warnings: List<String> = emptyList(),
    val usage: AiUsage,
)

@Serializable
data class AiSummarizeRequest(
    val text: String,
    val mode: AiMode = AiMode.PASSAGE,
    val pdfId: String? = null,
    val page: Int? = null,
)

@Serializable
data class AiTextRequest(val text: String)

@Serializable
data class AiExplainRequest(val text: String, val style: AiStyle = AiStyle.SIMPLE_ENGLISH)

@Serializable
data class AiCountRequest(val text: String, val count: Int = 5)

@Serializable
data class DiarySummaryRequest(val days: Int = 14)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val targetPerDay: Double = 1.0,
    val kind: HabitKind = HabitKind.COUNT,
    val createdAt: Instant,
)

@Serializable
data class HabitCreateRequest(
    val name: String,
    val targetPerDay: Double = 1.0,
    val kind: HabitKind = HabitKind.COUNT,
)

@Serializable
data class HabitLog(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val amount: Double = 1.0,
    val createdAt: Instant,
)

@Serializable
data class HabitLogRequest(val habitId: String, val date: LocalDate, val amount: Double = 1.0)

@Serializable
data class DiaryEntry(
    val id: String,
    val date: LocalDate,
    val mood: String = "NEUTRAL",
    val text: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class DiaryCreateRequest(
    val date: LocalDate,
    val mood: String = "NEUTRAL",
    val text: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class StreakInfo(val current: Int, val best: Int, val freezeDaysLeft: Int)

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val version: String = "1.0.0",
    val database: String = "ok",
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ForgotPasswordResponse(
    val status: String = "ok",
    val devToken: String? = null,
)

@Serializable
data class ResetPasswordRequest(val token: String, val password: String)

@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val avatarMascot: String? = null,
    val locale: String? = null,
    val theme: String? = null,
    val seriousMode: Boolean? = null,
)
