package com.medtech.reviewhub.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Upsert suspend fun upsert(card: FlashcardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(cards: List<FlashcardEntity>)
    @Update suspend fun update(card: FlashcardEntity)
    @Delete suspend fun delete(card: FlashcardEntity)
    @Query("DELETE FROM flashcards WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM flashcards WHERE id = :id") suspend fun byId(id: String): FlashcardEntity?
    @Query("SELECT * FROM flashcards ORDER BY updatedAtMs DESC") fun all(): Flow<List<FlashcardEntity>>
    @Query("SELECT * FROM flashcards") suspend fun allOnce(): List<FlashcardEntity>
    @Query("SELECT * FROM flashcards WHERE dueAtMs <= :nowMs ORDER BY dueAtMs ASC") suspend fun dueNow(nowMs: Long): List<FlashcardEntity>
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY dueAtMs ASC") suspend fun byDeck(deckId: String): List<FlashcardEntity>
    @Query("SELECT * FROM flashcards WHERE favorite = 1 ORDER BY updatedAtMs DESC") suspend fun favorites(): List<FlashcardEntity>
    @Query("SELECT * FROM flashcards WHERE subjectId = :subjectId ORDER BY dueAtMs ASC") suspend fun bySubject(subjectId: String): List<FlashcardEntity>
    @Query("SELECT * FROM flashcards WHERE sourceNeedsReview = 1 ORDER BY updatedAtMs DESC") suspend fun needsReview(): List<FlashcardEntity>

    @Query("SELECT COUNT(*) FROM flashcards WHERE dueAtMs <= :nowMs AND state = 'NEW'") suspend fun countNewDue(nowMs: Long): Int
    @Query("SELECT COUNT(*) FROM flashcards WHERE dueAtMs <= :nowMs AND state IN ('LEARNING','RELEARNING')") suspend fun countLearningDue(nowMs: Long): Int
    @Query("SELECT COUNT(*) FROM flashcards WHERE dueAtMs <= :nowMs AND state = 'REVIEW'") suspend fun countReviewDue(nowMs: Long): Int
    @Query("SELECT COUNT(*) FROM flashcards") suspend fun countAll(): Int

    @Insert suspend fun insertLog(log: ReviewLogEntity)
    @Query("SELECT * FROM review_log WHERE cardId = :cardId ORDER BY atMs DESC") suspend fun logsFor(cardId: String): List<ReviewLogEntity>
    @Query("SELECT COUNT(*) FROM review_log WHERE atMs >= :sinceMs") suspend fun reviewsSince(sinceMs: Long): Int
}

@Dao
interface StudyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(session: StudySessionEntity)
    @Query("SELECT * FROM study_sessions WHERE startedAtMs >= :fromMs AND startedAtMs < :toMs ORDER BY startedAtMs DESC")
    suspend fun sessionsBetween(fromMs: Long, toMs: Long): List<StudySessionEntity>
    @Query("SELECT * FROM study_sessions ORDER BY startedAtMs DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int): List<StudySessionEntity>
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM study_sessions") suspend fun totalMinutes(): Int
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM study_sessions WHERE startedAtMs >= :fromMs AND startedAtMs < :toMs")
    suspend fun minutesBetween(fromMs: Long, toMs: Long): Int

    @Query("SELECT * FROM study_plan LIMIT 1") suspend fun plan(): PlanEntity?
    @Upsert suspend fun savePlan(plan: PlanEntity)
    @Query("DELETE FROM study_plan") suspend fun clearPlan()

    @Query("SELECT DISTINCT date(startedAtMs / 1000, 'unixepoch', 'localtime') FROM study_sessions ORDER BY 1 DESC LIMIT :limit")
    suspend fun activeDayStrings(limit: Int): List<String>

    @Upsert suspend fun upsertProgress(progress: ReadingProgressEntity)
    @Query("SELECT * FROM `reading_progress` WHERE `key` = :key") suspend fun progress(key: String): ReadingProgressEntity?
    @Query("SELECT * FROM `reading_progress`") suspend fun allProgress(): List<ReadingProgressEntity>
}

@Dao
interface LoveNoteDao {
    @Upsert suspend fun upsert(note: LoveNoteEntity)
    @Query("DELETE FROM love_notes WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM love_notes WHERE id = :id") suspend fun byId(id: String): LoveNoteEntity?
    @Query("SELECT * FROM love_notes ORDER BY updatedAtMs DESC") fun all(): kotlinx.coroutines.flow.Flow<List<LoveNoteEntity>>
    @Query("SELECT * FROM love_notes ORDER BY updatedAtMs DESC") suspend fun allOnce(): List<LoveNoteEntity>
    @Query("SELECT * FROM love_notes WHERE favorite = 1 ORDER BY updatedAtMs DESC") suspend fun favorites(): List<LoveNoteEntity>
}

@Dao
interface QuizDao {
    @Upsert suspend fun upsert(attempt: QuizAttemptEntity)
    @Query("SELECT * FROM quiz_attempts WHERE id = :id") suspend fun byId(id: String): QuizAttemptEntity?
    @Query("SELECT * FROM quiz_attempts ORDER BY startedAtMs DESC") fun all(): Flow<List<QuizAttemptEntity>>
    @Query("SELECT * FROM quiz_attempts ORDER BY startedAtMs DESC LIMIT :limit") suspend fun recent(limit: Int): List<QuizAttemptEntity>
    @Query("SELECT * FROM quiz_attempts WHERE finishedAtMs IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    suspend fun unfinished(): QuizAttemptEntity?
    @Query("SELECT * FROM quiz_attempts WHERE finishedAtMs IS NOT NULL ORDER BY finishedAtMs DESC LIMIT :limit")
    suspend fun recentFinished(limit: Int): List<QuizAttemptEntity>
}

@Dao
interface HabitDao {
    @Upsert suspend fun upsertHabit(habit: HabitEntity)
    @Delete suspend fun deleteHabit(habit: HabitEntity)
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY name") fun habits(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habits") suspend fun habitsOnce(): List<HabitEntity>

    @Upsert suspend fun upsertLog(log: HabitLogEntity)
    @Query("SELECT * FROM habit_logs WHERE date = :date") suspend fun logsForDate(date: String): List<HabitLogEntity>
    @Query("SELECT * FROM habit_logs WHERE date >= :fromDate AND date <= :toDate") suspend fun logsBetween(fromDate: String, toDate: String): List<HabitLogEntity>

    @Insert suspend fun insertDiary(entry: DiaryEntity)
    @Delete suspend fun deleteDiary(entry: DiaryEntity)
    @Query("SELECT * FROM diary ORDER BY date DESC, createdAtMs DESC") fun diary(): Flow<List<DiaryEntity>>
    @Query("SELECT * FROM diary ORDER BY date DESC, createdAtMs DESC LIMIT :limit") suspend fun recentDiary(limit: Int): List<DiaryEntity>
}

@Dao
interface PdfDao {
    @Upsert suspend fun upsertPdf(pdf: PdfEntity)
    @Delete suspend fun deletePdf(pdf: PdfEntity)
    @Query("SELECT * FROM pdfs ORDER BY lastOpenedAtMs DESC, addedAtMs DESC") fun pdfs(): Flow<List<PdfEntity>>
    @Query("SELECT * FROM pdfs") suspend fun pdfsOnce(): List<PdfEntity>
    @Query("SELECT * FROM pdfs WHERE id = :id") suspend fun pdfById(id: String): PdfEntity?

    @Upsert suspend fun upsertAnnotation(a: PdfAnnotationEntity)
    @Delete suspend fun deleteAnnotation(a: PdfAnnotationEntity)
    @Query("SELECT * FROM pdf_annotations WHERE pdfId = :pdfId ORDER BY page, createdAtMs") suspend fun annotationsFor(pdfId: String): List<PdfAnnotationEntity>
    @Query("SELECT * FROM pdf_annotations WHERE pdfId = :pdfId AND page = :page ORDER BY createdAtMs") suspend fun annotationsForPage(pdfId: String, page: Int): List<PdfAnnotationEntity>
    @Query("SELECT * FROM pdf_annotations WHERE selectedText LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%'")
    suspend fun searchAnnotations(query: String): List<PdfAnnotationEntity>

    @Upsert suspend fun upsertProgress(progress: ReadingProgressEntity)
    @Query("SELECT * FROM `reading_progress` WHERE `key` = :key") suspend fun progress(key: String): ReadingProgressEntity?
    @Query("SELECT * FROM `reading_progress`") suspend fun allProgress(): List<ReadingProgressEntity>
}

@Dao
interface FocusDao {
    @Insert suspend fun insertSession(session: FocusSessionEntity)
    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMs DESC LIMIT :limit") suspend fun recent(limit: Int): List<FocusSessionEntity>
    @Query("SELECT COALESCE(SUM(completedMinutes), 0) FROM focus_sessions WHERE startedAtMs >= :sinceMs") suspend fun minutesSince(sinceMs: Long): Int
    @Query("SELECT COUNT(*) FROM focus_sessions WHERE completedMinutes >= plannedMinutes") suspend fun completedCount(): Int

    @Query("SELECT * FROM blocked_apps ORDER BY label") fun blockedApps(): Flow<List<BlockedAppEntity>>
    @Query("SELECT * FROM blocked_apps") suspend fun blockedAppsOnce(): List<BlockedAppEntity>
    @Upsert suspend fun upsertBlockedApp(app: BlockedAppEntity)
    @Delete suspend fun deleteBlockedApp(app: BlockedAppEntity)
    @Query("DELETE FROM blocked_apps") suspend fun clearBlockedApps()
}
