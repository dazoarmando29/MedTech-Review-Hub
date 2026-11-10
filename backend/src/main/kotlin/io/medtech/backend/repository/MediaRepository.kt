package io.medtech.backend.repository

import io.medtech.backend.db.DiaryEntries
import io.medtech.backend.db.GeneratedAiContent
import io.medtech.backend.db.HabitLogs
import io.medtech.backend.db.Habits
import io.medtech.backend.db.LearningAnalytics
import io.medtech.backend.db.PdfAnnotations
import io.medtech.backend.db.PdfDocuments
import io.medtech.backend.db.Profiles
import io.medtech.backend.db.newId
import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.db.tx
import io.medtech.backend.domain.Annotation
import io.medtech.backend.domain.AnnotationColor
import io.medtech.backend.domain.AnnotationInput
import io.medtech.backend.domain.AnnotationType
import io.medtech.backend.domain.DiaryEntry
import io.medtech.backend.domain.Habit
import io.medtech.backend.domain.HabitKind
import io.medtech.backend.domain.HabitLog
import io.medtech.backend.domain.PdfDocumentView
import io.medtech.backend.domain.Rect
import io.medtech.backend.domain.TopicMastery
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PdfRepository {

    suspend fun insert(userId: String, filename: String, sizeBytes: Long, storagePath: String): PdfDocumentView = tx {
        val id = newId()
        PdfDocuments.insert {
            it[PdfDocuments.id] = id
            it[PdfDocuments.userId] = userId
            it[PdfDocuments.filename] = filename
            it[PdfDocuments.contentType] = "application/pdf"
            it[PdfDocuments.sizeBytes] = sizeBytes
            it[PdfDocuments.storagePath] = storagePath
            it[uploadedAt] = nowMillis()
        }
        PdfDocumentView(
            id = id,
            filename = filename,
            contentType = "application/pdf",
            sizeBytes = sizeBytes,
            uploadedAt = nowMillis().toInstant(),
            annotationCount = 0,
        )
    }

    suspend fun list(userId: String): List<PdfDocumentView> = tx {
        PdfDocuments.selectAll().where { PdfDocuments.userId eq userId }
            .orderBy(PdfDocuments.uploadedAt to SortOrder.DESC)
            .map { row ->
                val count = PdfAnnotations.selectAll().where { PdfAnnotations.pdfId eq row[PdfDocuments.id] }.count().toInt()
                PdfDocumentView(
                    id = row[PdfDocuments.id],
                    filename = row[PdfDocuments.filename],
                    contentType = row[PdfDocuments.contentType],
                    sizeBytes = row[PdfDocuments.sizeBytes],
                    uploadedAt = row[PdfDocuments.uploadedAt].toInstant(),
                    annotationCount = count,
                )
            }
    }

    suspend fun owned(userId: String, pdfId: String): OwnedPdf? = tx {
        PdfDocuments.selectAll().where { (PdfDocuments.id eq pdfId) and (PdfDocuments.userId eq userId) }
            .singleOrNull()
            ?.let {
                OwnedPdf(
                    id = it[PdfDocuments.id],
                    filename = it[PdfDocuments.filename],
                    storagePath = it[PdfDocuments.storagePath],
                    sizeBytes = it[PdfDocuments.sizeBytes],
                    uploadedAt = it[PdfDocuments.uploadedAt].toInstant(),
                )
            }
    }

    data class OwnedPdf(val id: String, val filename: String, val storagePath: String, val sizeBytes: Long, val uploadedAt: Instant)

    suspend fun delete(userId: String, pdfId: String): Boolean = tx {
        PdfAnnotations.deleteWhere { (PdfAnnotations.pdfId eq pdfId) and (PdfAnnotations.userId eq userId) }
        PdfDocuments.deleteWhere { (PdfDocuments.id eq pdfId) and (PdfDocuments.userId eq userId) } > 0
    }

    suspend fun annotations(userId: String, pdfId: String): List<Annotation> = tx {
        PdfAnnotations.selectAll()
            .where { (PdfAnnotations.pdfId eq pdfId) and (PdfAnnotations.userId eq userId) }
            .orderBy(PdfAnnotations.pageNumber to SortOrder.ASC, PdfAnnotations.createdAt to SortOrder.ASC)
            .map { it.toAnnotation() }
    }

    suspend fun replaceAnnotations(userId: String, pdfId: String, annotations: List<AnnotationInput>): List<Annotation> = tx {
        PdfAnnotations.deleteWhere { (PdfAnnotations.pdfId eq pdfId) and (PdfAnnotations.userId eq userId) }
        val inserted = mutableListOf<Annotation>()
        for (ann in annotations) {
            val id = ann.id ?: newId()
            val createdMillis = nowMillis()
            PdfAnnotations.insert {
                it[PdfAnnotations.id] = id
                it[PdfAnnotations.userId] = userId
                it[PdfAnnotations.pdfId] = pdfId
                it[pageNumber] = ann.page
                it[annType] = ann.type.name
                it[color] = ann.color.name
                it[selectedText] = ann.selectedText
                it[noteText] = ann.note
                it[rectJson] = encodeJson(ann.rect)
                it[createdAt] = createdMillis
            }
            inserted += Annotation(
                id = id,
                pdfId = pdfId,
                page = ann.page,
                type = ann.type,
                color = ann.color,
                selectedText = ann.selectedText,
                note = ann.note,
                rect = ann.rect,
                createdAt = createdMillis.toInstant(),
            )
        }
        inserted
    }

    suspend fun deleteForUser(userId: String) = tx {
        PdfAnnotations.deleteWhere { PdfAnnotations.userId eq userId }
        PdfDocuments.deleteWhere { PdfDocuments.userId eq userId }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toAnnotation(): Annotation {
        val rect = runCatching { decodeJson<Rect?>(this[PdfAnnotations.rectJson]) }.getOrNull()
        return Annotation(
            id = this[PdfAnnotations.id],
            pdfId = this[PdfAnnotations.pdfId],
            page = this[PdfAnnotations.pageNumber],
            type = runCatching { AnnotationType.valueOf(this[PdfAnnotations.annType]) }.getOrDefault(AnnotationType.HIGHLIGHT),
            color = runCatching { AnnotationColor.valueOf(this[PdfAnnotations.color]) }.getOrDefault(AnnotationColor.YELLOW),
            selectedText = this[PdfAnnotations.selectedText],
            note = this[PdfAnnotations.noteText],
            rect = rect,
            createdAt = this[PdfAnnotations.createdAt].toInstant(),
        )
    }
}

class HabitDiaryRepository {

    suspend fun habits(userId: String): List<Habit> = tx {
        Habits.selectAll().where { Habits.userId eq userId }.orderBy(Habits.createdAt to SortOrder.ASC).map {
            Habit(
                id = it[Habits.id],
                name = it[Habits.name],
                targetPerDay = it[Habits.targetPerDay],
                kind = runCatching { HabitKind.valueOf(it[Habits.habitKind]) }.getOrDefault(HabitKind.COUNT),
                createdAt = it[Habits.createdAt].toInstant(),
            )
        }
    }

    suspend fun insertHabit(userId: String, name: String, targetPerDay: Double, kind: HabitKind): Habit = tx {
        val id = newId()
        val createdMillis = nowMillis()
        Habits.insert {
            it[Habits.id] = id
            it[Habits.userId] = userId
            it[Habits.name] = name
            it[Habits.targetPerDay] = targetPerDay
            it[habitKind] = kind.name
            it[createdAt] = createdMillis
        }
        Habit(id, name, targetPerDay, kind, createdMillis.toInstant())
    }

    suspend fun deleteHabit(userId: String, habitId: String): Boolean = tx {
        HabitLogs.deleteWhere { (HabitLogs.habitId eq habitId) and (HabitLogs.userId eq userId) }
        Habits.deleteWhere { (Habits.id eq habitId) and (Habits.userId eq userId) } > 0
    }

    suspend fun logHabit(userId: String, habitId: String, date: LocalDate, amount: Double): HabitLog = tx {
        val id = newId()
        val createdMillis = nowMillis()
        val existing = HabitLogs.selectAll()
            .where { (HabitLogs.habitId eq habitId) and (HabitLogs.logDate eq date.toString()) and (HabitLogs.userId eq userId) }
            .singleOrNull()
        if (existing != null) {
            HabitLogs.update({ HabitLogs.id eq existing[HabitLogs.id] }) { it[HabitLogs.amount] = amount }
            HabitLog(existing[HabitLogs.id], habitId, date, amount, existing[HabitLogs.createdAt].toInstant())
        } else {
            HabitLogs.insert {
                it[HabitLogs.id] = id
                it[HabitLogs.userId] = userId
                it[HabitLogs.habitId] = habitId
                it[logDate] = date.toString()
                it[HabitLogs.amount] = amount
                it[createdAt] = createdMillis
            }
            HabitLog(id, habitId, date, amount, createdMillis.toInstant())
        }
    }

    suspend fun habitLogDates(userId: String): List<String> = tx {
        HabitLogs.selectAll().where { HabitLogs.userId eq userId }.map { it[HabitLogs.logDate] }
    }

    suspend fun diary(userId: String): List<DiaryEntry> = tx {
        DiaryEntries.selectAll().where { DiaryEntries.userId eq userId }
            .orderBy(DiaryEntries.entryDate to SortOrder.DESC, DiaryEntries.createdAt to SortOrder.DESC)
            .map {
                DiaryEntry(
                    id = it[DiaryEntries.id],
                    date = LocalDate.parse(it[DiaryEntries.entryDate]),
                    mood = it[DiaryEntries.mood],
                    text = it[DiaryEntries.entryText],
                    tags = decodeJson(it[DiaryEntries.tagsJson]),
                    createdAt = it[DiaryEntries.createdAt].toInstant(),
                    updatedAt = it[DiaryEntries.updatedAt].toInstant(),
                )
            }
    }

    suspend fun insertDiary(userId: String, date: LocalDate, mood: String, text: String, tags: List<String>): DiaryEntry = tx {
        val id = newId()
        val createdMillis = nowMillis()
        DiaryEntries.insert {
            it[DiaryEntries.id] = id
            it[DiaryEntries.userId] = userId
            it[entryDate] = date.toString()
            it[DiaryEntries.mood] = mood
            it[entryText] = text
            it[tagsJson] = encodeJson(tags)
            it[createdAt] = createdMillis
            it[updatedAt] = createdMillis
        }
        DiaryEntry(id, date, mood, text, tags, createdMillis.toInstant(), createdMillis.toInstant())
    }

    suspend fun diaryTexts(userId: String, limit: Int): List<DiaryEntry> = diary(userId).take(limit)

    suspend fun deleteForUser(userId: String) = tx {
        HabitLogs.deleteWhere { HabitLogs.userId eq userId }
        Habits.deleteWhere { Habits.userId eq userId }
        DiaryEntries.deleteWhere { DiaryEntries.userId eq userId }
    }
}

class AiContentRepository {

    suspend fun insert(userId: String, task: String, requestHash: String, responseJson: String, inputTokens: Int, outputTokens: Int) = tx {
        GeneratedAiContent.insert {
            it[GeneratedAiContent.id] = newId()
            it[GeneratedAiContent.userId] = userId
            it[GeneratedAiContent.task] = task
            it[GeneratedAiContent.requestHash] = requestHash
            it[GeneratedAiContent.responseJson] = responseJson
            it[GeneratedAiContent.inputTokens] = inputTokens
            it[GeneratedAiContent.outputTokens] = outputTokens
            it[GeneratedAiContent.createdAt] = nowMillis()
        }
    }

    suspend fun tokensUsedToday(userId: String, dayStartMillis: Long): Int = tx {
        GeneratedAiContent.selectAll()
            .where { (GeneratedAiContent.userId eq userId) and (GeneratedAiContent.createdAt greaterEq dayStartMillis) }
            .sumOf { it[GeneratedAiContent.inputTokens] + it[GeneratedAiContent.outputTokens] }
            .toInt()
    }

    data class AiContentRow(
        val id: String,
        val task: String,
        val responseJson: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val createdAtMillis: Long,
    )

    suspend fun allForUser(userId: String): List<AiContentRow> = tx {
        GeneratedAiContent.selectAll().where { GeneratedAiContent.userId eq userId }.map {
            AiContentRow(
                id = it[GeneratedAiContent.id],
                task = it[GeneratedAiContent.task],
                responseJson = it[GeneratedAiContent.responseJson],
                inputTokens = it[GeneratedAiContent.inputTokens],
                outputTokens = it[GeneratedAiContent.outputTokens],
                createdAtMillis = it[GeneratedAiContent.createdAt],
            )
        }
    }

    suspend fun deleteForUser(userId: String) = tx {
        GeneratedAiContent.deleteWhere { GeneratedAiContent.userId eq userId }
    }
}

class AnalyticsRepository {

    suspend fun recordAnswers(userId: String, subjectId: String, topicId: String, correctDelta: Int, answeredDelta: Int) = tx {
        val existing = LearningAnalytics.selectAll()
            .where {
                (LearningAnalytics.userId eq userId) and
                    (LearningAnalytics.subjectId eq subjectId) and
                    (LearningAnalytics.topicId eq topicId)
            }.singleOrNull()
        if (existing == null) {
            LearningAnalytics.insert {
                it[LearningAnalytics.id] = newId()
                it[LearningAnalytics.userId] = userId
                it[LearningAnalytics.subjectId] = subjectId
                it[LearningAnalytics.topicId] = topicId
                it[questionsAnswered] = answeredDelta
                it[questionsCorrect] = correctDelta
                it[mastery] = if (answeredDelta > 0) correctDelta.toDouble() / answeredDelta else 0.0
                it[updatedAt] = nowMillis()
            }
        } else {
            val answered = existing[LearningAnalytics.questionsAnswered] + answeredDelta
            val correct = existing[LearningAnalytics.questionsCorrect] + correctDelta
            LearningAnalytics.update({ LearningAnalytics.id eq existing[LearningAnalytics.id] }) {
                it[questionsAnswered] = answered
                it[questionsCorrect] = correct
                it[mastery] = if (answered > 0) correct.toDouble() / answered else 0.0
                it[updatedAt] = nowMillis()
            }
        }
    }

    suspend fun masteryFor(userId: String): List<TopicMastery> = tx {
        LearningAnalytics.selectAll().where { LearningAnalytics.userId eq userId }.map {
            TopicMastery(
                topicId = it[LearningAnalytics.topicId],
                subjectId = it[LearningAnalytics.subjectId],
                mastery = it[LearningAnalytics.mastery],
            )
        }
    }

    suspend fun deleteForUser(userId: String) = tx {
        LearningAnalytics.deleteWhere { LearningAnalytics.userId eq userId }
    }
}

class AccountDeletionRepository {
    private val refreshTokens = RefreshTokenRepository()
    private val profileRepo = ProfileRepository()
    private val prefsRepo = PreferencesRepository()
    private val planRepo = PlanRepository()
    private val sessionRepo = SessionRepository()
    private val flashcardRepo = FlashcardRepository()
    private val quizRepo = QuizRepository()
    private val pdfRepo = PdfRepository()
    private val habitDiaryRepo = HabitDiaryRepository()
    private val aiRepo = AiContentRepository()
    private val analyticsRepo = AnalyticsRepository()
    private val contentRepo = ContentRepository()

    /**
     * Removes every row that references the user, across all private tables,
     * then the user row itself. PDF files on disk are removed by PdfService.
     */
    suspend fun deleteAllForUser(userId: String) {
        refreshTokens.deleteAllForUser(userId)
        profileRepo.deleteForUser(userId)
        prefsRepo.deleteForUser(userId)
        planRepo.deleteForUser(userId)
        sessionRepo.deleteForUser(userId)
        flashcardRepo.deleteForUser(userId)
        quizRepo.deleteForUser(userId)
        pdfRepo.deleteForUser(userId)
        habitDiaryRepo.deleteForUser(userId)
        aiRepo.deleteForUser(userId)
        analyticsRepo.deleteForUser(userId)
        contentRepo.deleteForUser(userId)
        tx {
            io.medtech.backend.db.Users.deleteWhere { io.medtech.backend.db.Users.id eq userId }
        }
    }
}
