package com.medtech.reviewhub.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Room entities — offline-first storage. All app features work with zero
// network; the backend is only used for optional sync / AI / update checks.
// ---------------------------------------------------------------------------

@Entity(tableName = "flashcards", indices = [Index("dueAtMs"), Index("deckId"), Index("subjectId")])
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val type: String,
    val tagsCsv: String,
    val subjectId: String?,
    val topicId: String?,
    val difficulty: Int,
    val note: String,
    val favorite: Boolean,
    val imageRef: String?,
    val sourceKind: String,
    val sourceLessonId: String?,
    val sourcePdfId: String?,
    val sourcePage: Int?,
    val sourceNeedsReview: Boolean,
    // -- FSRS schedule (shared/FSRS.md stored fields) --
    val state: String,
    val stability: Double,
    val fsrsDifficulty: Double,
    val dueAtMs: Long,
    val lastReviewAtMs: Long?,
    val reps: Int,
    val lapses: Int,
    val stepIndex: Int,
    val historyJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "review_log", indices = [Index("cardId")])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val atMs: Long,
    val rating: String,
    val intervalDays: Double
)

@Entity(tableName = "study_sessions", indices = [Index("startedAtMs")])
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val kind: String,
    val subjectId: String?,
    val minutes: Int,
    val itemsCompleted: Int
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val questionIdsCsv: String,
    val answersJson: String,
    val incorrectIdsCsv: String,
    val correct: Int,
    val total: Int,
    val timeSeconds: Int,
    val subjectId: String?,
    val topicId: String?,
    val difficultyBand: String?
)

@Entity(tableName = "study_plan")
data class PlanEntity(
    @PrimaryKey val id: String,
    val generatedAt: String,
    val daysJson: String
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetPerDay: Int,
    val kind: String,
    val archived: Boolean
)

@Entity(tableName = "habit_logs", indices = [Index(value = ["habitId", "date"], unique = true)])
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: String,
    val date: String,
    val amount: Int
)

@Entity(tableName = "diary", indices = [Index("date")])
data class DiaryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val mood: String,
    val text: String,
    val tagsCsv: String,
    val createdAtMs: Long
)

@Entity(tableName = "pdfs")
data class PdfEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val localPath: String,
    val folder: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val addedAtMs: Long,
    val lastOpenedAtMs: Long?
)

@Entity(tableName = "pdf_annotations", indices = [Index("pdfId"), Index(value = ["pdfId", "page"])])
data class PdfAnnotationEntity(
    @PrimaryKey val id: String,
    val pdfId: String,
    val page: Int,
    val type: String,
    val color: String,
    val selectedText: String,
    val note: String,
    val rectX: Float,
    val rectY: Float,
    val rectW: Float,
    val rectH: Float,
    val hasRect: Boolean,
    val createdAtMs: Long
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val key: String,
    val progressPercent: Float,
    val lastOffset: Int,
    val bookmarked: Boolean,
    val updatedAtMs: Long
)

@Entity(tableName = "focus_sessions", indices = [Index("startedAtMs")])
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val presetLabel: String,
    val interruptions: Int
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String
)

/**
 * "Little Notes for You" - private personal notes. NEVER synced publicly;
 * included in the local data export only. The app never auto-generates notes
 * pretending to be from a person.
 */
@Entity(tableName = "love_notes", indices = [Index("showOnDate"), Index("updatedAtMs")])
data class LoveNoteEntity(
    @PrimaryKey val id: String,
    val text: String,
    val showOnDate: String?,
    val favorite: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long
)
