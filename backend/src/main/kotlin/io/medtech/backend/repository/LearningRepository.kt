package io.medtech.backend.repository

import io.medtech.backend.db.FocusSessions
import io.medtech.backend.db.FlashcardDecks
import io.medtech.backend.db.FlashcardReviews
import io.medtech.backend.db.Flashcards
import io.medtech.backend.db.QuizAnswers
import io.medtech.backend.db.QuizAttempts
import io.medtech.backend.db.StudyPlanItems
import io.medtech.backend.db.StudyPlans
import io.medtech.backend.db.StudySessions
import io.medtech.backend.db.newId
import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.db.tx
import io.medtech.backend.domain.Flashcard
import io.medtech.backend.domain.FlashcardHistoryEntry
import io.medtech.backend.domain.FlashcardSchedule
import io.medtech.backend.domain.FlashcardSource
import io.medtech.backend.domain.FlashcardType
import io.medtech.backend.domain.PlanDay
import io.medtech.backend.domain.PlanItem
import io.medtech.backend.domain.PlanItemType
import io.medtech.backend.domain.QuizMode
import io.medtech.backend.domain.QuizStatus
import io.medtech.backend.domain.ScheduleState
import io.medtech.backend.domain.SessionCreateRequest
import io.medtech.backend.domain.SessionKind
import io.medtech.backend.domain.StudyPlan
import io.medtech.backend.domain.StudySession
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class PlanRepository {

    suspend fun latestForUser(userId: String): StudyPlan? = tx {
        val planRow = StudyPlans.selectAll().where { StudyPlans.userId eq userId }
            .orderBy(StudyPlans.generatedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull() ?: return@tx null
        val planId = planRow[StudyPlans.id]
        val rows = StudyPlanItems.selectAll().where { StudyPlanItems.planId eq planId }
            .orderBy(StudyPlanItems.dayDate to SortOrder.ASC, StudyPlanItems.position to SortOrder.ASC)
            .toList()
        val days = rows.groupBy { it[StudyPlanItems.dayDate] }.toList()
            .sortedBy { it.first }
            .map { (dateStr, itemRows) ->
                PlanDay(
                    date = LocalDate.parse(dateStr),
                    items = itemRows.map { row ->
                        PlanItem(
                            id = row[StudyPlanItems.id],
                            type = runCatching { PlanItemType.valueOf(row[StudyPlanItems.itemType]) }.getOrDefault(PlanItemType.READ),
                            subjectId = row[StudyPlanItems.subjectId],
                            topicId = row[StudyPlanItems.topicId],
                            lessonId = row[StudyPlanItems.lessonId],
                            targetMinutes = row[StudyPlanItems.targetMinutes],
                            completed = row[StudyPlanItems.completed],
                        )
                    },
                )
            }
        StudyPlan(
            id = planId,
            generatedAt = planRow[StudyPlans.generatedAt].toInstant(),
            days = days,
        )
    }

    suspend fun save(userId: String, days: List<PlanDay>, weightsJson: String, label: String): StudyPlan = tx {
        StudyPlanItems.deleteWhere { StudyPlanItems.userId eq userId }
        StudyPlans.deleteWhere { StudyPlans.userId eq userId }
        val planId = newId()
        val generatedMillis = nowMillis()
        StudyPlans.insert {
            it[StudyPlans.id] = planId
            it[StudyPlans.userId] = userId
            it[StudyPlans.generatedAt] = generatedMillis
            it[StudyPlans.weightsJson] = weightsJson
            it[StudyPlans.label] = label
        }
        for (day in days) {
            day.items.forEachIndexed { index, item ->
                StudyPlanItems.insert {
                    it[StudyPlanItems.id] = item.id.ifBlank { newId() }
                    it[StudyPlanItems.planId] = planId
                    it[StudyPlanItems.userId] = userId
                    it[StudyPlanItems.dayDate] = day.date.toString()
                    it[StudyPlanItems.position] = index
                    it[StudyPlanItems.itemType] = item.type.name
                    it[StudyPlanItems.subjectId] = item.subjectId
                    it[StudyPlanItems.topicId] = item.topicId
                    it[StudyPlanItems.lessonId] = item.lessonId
                    it[StudyPlanItems.targetMinutes] = item.targetMinutes
                    it[StudyPlanItems.completed] = item.completed
                }
            }
        }
        StudyPlan(id = planId, generatedAt = generatedMillis.toInstant(), days = days)
    }

    suspend fun deleteForUser(userId: String) = tx {
        StudyPlanItems.deleteWhere { StudyPlanItems.userId eq userId }
        StudyPlans.deleteWhere { StudyPlans.userId eq userId }
    }
}

class SessionRepository {

    suspend fun insert(userId: String, request: SessionCreateRequest): StudySession = tx {
        val id = newId()
        val startMillis = request.startedAt.toEpochMilliseconds()
        val endMillis = request.endedAt.toEpochMilliseconds()
        val minutesComputed = ((endMillis - startMillis) / 60000).toInt().coerceAtLeast(0)
        StudySessions.insert {
            it[StudySessions.id] = id
            it[StudySessions.userId] = userId
            it[StudySessions.startedAt] = startMillis
            it[StudySessions.endedAt] = endMillis
            it[StudySessions.sessionKind] = request.kind.name
            it[StudySessions.subjectId] = request.subjectId
            it[StudySessions.minutes] = minutesComputed
            it[StudySessions.itemsCompleted] = request.itemsCompleted
        }
        if (request.kind == SessionKind.FOCUS) {
            FocusSessions.insert {
                it[FocusSessions.id] = newId()
                it[FocusSessions.userId] = userId
                it[FocusSessions.startedAt] = startMillis
                it[FocusSessions.endedAt] = endMillis
                it[FocusSessions.minutes] = minutesComputed
            }
        }
        StudySession(
            id = id,
            startedAt = request.startedAt,
            endedAt = request.endedAt,
            kind = request.kind,
            subjectId = request.subjectId,
            minutes = minutesComputed,
            itemsCompleted = request.itemsCompleted,
        )
    }

    suspend fun list(userId: String, fromMillis: Long?, toMillis: Long?): List<StudySession> = tx {
        StudySessions.selectAll().where {
            (StudySessions.userId eq userId) and
                (if (fromMillis != null) StudySessions.startedAt greaterEq fromMillis else Op.TRUE) and
                (if (toMillis != null) StudySessions.startedAt lessEq toMillis else Op.TRUE)
        }.orderBy(StudySessions.startedAt to SortOrder.DESC).map { row ->
            StudySession(
                id = row[StudySessions.id],
                startedAt = row[StudySessions.startedAt].toInstant(),
                endedAt = row[StudySessions.endedAt].toInstant(),
                kind = runCatching { SessionKind.valueOf(row[StudySessions.sessionKind]) }.getOrDefault(SessionKind.READ),
                subjectId = row[StudySessions.subjectId],
                minutes = row[StudySessions.minutes],
                itemsCompleted = row[StudySessions.itemsCompleted],
            )
        }
    }

    suspend fun allForUser(userId: String): List<StudySession> = list(userId, null, null)

    suspend fun deleteForUser(userId: String) = tx {
        StudySessions.deleteWhere { StudySessions.userId eq userId }
        FocusSessions.deleteWhere { FocusSessions.userId eq userId }
    }
}

class FlashcardRepository {

    data class DeckRecord(val id: String, val title: String, val subjectId: String?)

    suspend fun findOrCreateDeck(userId: String, deckId: String?, deckTitle: String?, subjectId: String?): DeckRecord = tx {
        if (deckId != null) {
            val existing = FlashcardDecks.selectAll()
                .where { (FlashcardDecks.id eq deckId) and (FlashcardDecks.userId eq userId) }
                .singleOrNull()
            if (existing != null) return@tx DeckRecord(existing[FlashcardDecks.id], existing[FlashcardDecks.title], existing[FlashcardDecks.subjectId])
        }
        val id = deckId ?: newId()
        val title = deckTitle?.takeIf { it.isNotBlank() } ?: "My deck"
        FlashcardDecks.insert {
            it[FlashcardDecks.id] = id
            it[FlashcardDecks.userId] = userId
            it[FlashcardDecks.title] = title
            it[FlashcardDecks.subjectId] = subjectId
            it[FlashcardDecks.createdAt] = nowMillis()
            it[FlashcardDecks.updatedAt] = nowMillis()
        }
        DeckRecord(id, title, subjectId)
    }

    suspend fun insert(
        userId: String,
        deckId: String?,
        front: String,
        back: String,
        type: FlashcardType,
        tags: List<String>,
        subjectId: String?,
        topicId: String?,
        difficulty: Int,
        note: String,
        favorite: Boolean,
        imageRef: String?,
        source: FlashcardSource,
        dueAtMillis: Long,
    ): String = tx {
        val id = newId()
        val createdMillis = nowMillis()
        Flashcards.insert {
            it[Flashcards.id] = id
            it[Flashcards.userId] = userId
            it[Flashcards.deckId] = deckId
            it[Flashcards.cardFront] = front
            it[Flashcards.cardBack] = back
            it[Flashcards.cardType] = type.name
            it[Flashcards.tagsJson] = encodeJson(tags)
            it[Flashcards.subjectId] = subjectId
            it[Flashcards.topicId] = topicId
            it[Flashcards.difficulty] = difficulty
            it[Flashcards.noteText] = note
            it[Flashcards.favorite] = favorite
            it[Flashcards.imageRef] = imageRef
            it[Flashcards.sourceJson] = encodeJson(source)
            it[Flashcards.dueAt] = dueAtMillis
            it[Flashcards.createdAt] = createdMillis
            it[Flashcards.updatedAt] = createdMillis
        }
        id
    }

    suspend fun findById(userId: String, id: String): Flashcard? = tx {
        Flashcards.selectAll().where { (Flashcards.id eq id) and (Flashcards.userId eq userId) }
            .singleOrNull()?.toFlashcard()
    }

    suspend fun list(userId: String, deckId: String?, tag: String?, dueOnly: Boolean): List<Flashcard> = tx {
        Flashcards.selectAll().where {
            (Flashcards.userId eq userId) and
                (if (deckId != null) Flashcards.deckId eq deckId else Op.TRUE) and
                (if (dueOnly) Flashcards.dueAt lessEq nowMillis() else Op.TRUE)
        }.orderBy(Flashcards.dueAt to SortOrder.ASC).map { it.toFlashcard() }
            .let { cards -> if (tag == null) cards else cards.filter { card -> tag in card.tags } }
    }

    suspend fun updateCard(
        userId: String,
        id: String,
        deckId: String?,
        front: String,
        back: String,
        type: FlashcardType,
        tags: List<String>,
        subjectId: String?,
        topicId: String?,
        difficulty: Int,
        note: String,
        favorite: Boolean,
        imageRef: String?,
        source: FlashcardSource,
    ): Boolean = tx {
        Flashcards.update({ (Flashcards.id eq id) and (Flashcards.userId eq userId) }) {
            it[Flashcards.deckId] = deckId
            it[Flashcards.cardFront] = front
            it[Flashcards.cardBack] = back
            it[Flashcards.cardType] = type.name
            it[Flashcards.tagsJson] = encodeJson(tags)
            it[Flashcards.subjectId] = subjectId
            it[Flashcards.topicId] = topicId
            it[Flashcards.difficulty] = difficulty
            it[Flashcards.noteText] = note
            it[Flashcards.favorite] = favorite
            it[Flashcards.imageRef] = imageRef
            it[Flashcards.sourceJson] = encodeJson(source)
            it[Flashcards.updatedAt] = nowMillis()
        } > 0
    }

    suspend fun delete(userId: String, id: String): Boolean = tx {
        FlashcardReviews.deleteWhere { (FlashcardReviews.flashcardId eq id) and (FlashcardReviews.userId eq userId) }
        Flashcards.deleteWhere { (Flashcards.id eq id) and (Flashcards.userId eq userId) } > 0
    }

    suspend fun updateSchedule(
        userId: String,
        id: String,
        state: ScheduleState,
        stability: Double,
        difficulty: Double,
        dueAtMillis: Long,
        lastReviewAtMillis: Long?,
        reps: Int,
        lapses: Int,
        learningStep: Int,
        history: List<FlashcardHistoryEntry>,
    ) = tx {
        Flashcards.update({ (Flashcards.id eq id) and (Flashcards.userId eq userId) }) {
            it[Flashcards.schedState] = state.name
            it[Flashcards.stability] = stability
            it[Flashcards.difficultyFsrs] = difficulty
            it[Flashcards.dueAt] = dueAtMillis
            it[Flashcards.lastReviewAt] = lastReviewAtMillis
            it[Flashcards.reps] = reps
            it[Flashcards.lapses] = lapses
            it[Flashcards.learningStep] = learningStep
            it[Flashcards.historyJson] = encodeJson(history)
            it[Flashcards.updatedAt] = nowMillis()
        }
    }

    suspend fun insertReview(userId: String, flashcardId: String, rating: String, intervalDays: Double, reviewedAtMillis: Long) = tx {
        FlashcardReviews.insert {
            it[FlashcardReviews.id] = newId()
            it[FlashcardReviews.userId] = userId
            it[FlashcardReviews.flashcardId] = flashcardId
            it[FlashcardReviews.rating] = rating
            it[FlashcardReviews.intervalDays] = intervalDays
            it[FlashcardReviews.reviewedAt] = reviewedAtMillis
        }
    }

    data class CardStateRow(
        val state: String,
        val stability: Double,
        val difficulty: Double,
        val dueAtMillis: Long,
        val lastReviewAtMillis: Long?,
        val reps: Int,
        val lapses: Int,
        val learningStep: Int,
    )

    suspend fun stateOf(userId: String, id: String): CardStateRow? = tx {
        Flashcards.selectAll().where { (Flashcards.id eq id) and (Flashcards.userId eq userId) }
            .singleOrNull()
            ?.let {
                CardStateRow(
                    state = it[Flashcards.schedState],
                    stability = it[Flashcards.stability],
                    difficulty = it[Flashcards.difficultyFsrs],
                    dueAtMillis = it[Flashcards.dueAt],
                    lastReviewAtMillis = it[Flashcards.lastReviewAt],
                    reps = it[Flashcards.reps],
                    lapses = it[Flashcards.lapses],
                    learningStep = it[Flashcards.learningStep],
                )
            }
    }

    suspend fun countDue(userId: String, throughMillis: Long): Int = tx {
        Flashcards.selectAll().where { (Flashcards.userId eq userId) and (Flashcards.dueAt lessEq throughMillis) }.count().toInt()
    }

    suspend fun deleteForUser(userId: String) = tx {
        FlashcardReviews.deleteWhere { FlashcardReviews.userId eq userId }
        Flashcards.deleteWhere { Flashcards.userId eq userId }
        FlashcardDecks.deleteWhere { FlashcardDecks.userId eq userId }
    }

    suspend fun exportAll(userId: String): List<Flashcard> = tx {
        Flashcards.selectAll().where { Flashcards.userId eq userId }.map { it.toFlashcard() }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toFlashcard(): Flashcard {
        val schedule = FlashcardSchedule(
            state = runCatching { ScheduleState.valueOf(this[Flashcards.schedState]) }.getOrDefault(ScheduleState.NEW),
            stability = this[Flashcards.stability],
            difficulty = this[Flashcards.difficultyFsrs],
            dueAt = this[Flashcards.dueAt].toInstant(),
            lastReviewAt = this[Flashcards.lastReviewAt]?.toInstant(),
            reps = this[Flashcards.reps],
            lapses = this[Flashcards.lapses],
        )
        return Flashcard(
            id = this[Flashcards.id],
            deckId = this[Flashcards.deckId],
            front = this[Flashcards.cardFront],
            back = this[Flashcards.cardBack],
            type = runCatching { FlashcardType.valueOf(this[Flashcards.cardType]) }.getOrDefault(FlashcardType.BASIC),
            tags = decodeJson(this[Flashcards.tagsJson]),
            subjectId = this[Flashcards.subjectId],
            topicId = this[Flashcards.topicId],
            difficulty = this[Flashcards.difficulty],
            note = this[Flashcards.noteText],
            favorite = this[Flashcards.favorite],
            imageRef = this[Flashcards.imageRef],
            source = decodeJson(this[Flashcards.sourceJson]),
            schedule = schedule,
            history = decodeJson(this[Flashcards.historyJson]),
        )
    }
}

class QuizRepository {

    suspend fun createAttempt(userId: String, mode: QuizMode, questionIds: List<String>): String = tx {
        val id = newId()
        QuizAttempts.insert {
            it[QuizAttempts.id] = id
            it[QuizAttempts.userId] = userId
            it[QuizAttempts.quizMode] = mode.name
            it[QuizAttempts.status] = QuizStatus.IN_PROGRESS.name
            it[QuizAttempts.questionIds] = encodeJson(questionIds)
            it[QuizAttempts.startedAt] = nowMillis()
            it[QuizAttempts.totalCount] = questionIds.size
        }
        id
    }

    suspend fun attemptIds(userId: String, attemptId: String): List<String>? = tx {
        QuizAttempts.selectAll().where { (QuizAttempts.id eq attemptId) and (QuizAttempts.userId eq userId) }
            .singleOrNull()
            ?.let { decodeJson<List<String>>(it[QuizAttempts.questionIds]) }
    }

    suspend fun attemptRow(userId: String, attemptId: String): AttemptRow? = tx {
        QuizAttempts.selectAll().where { (QuizAttempts.id eq attemptId) and (QuizAttempts.userId eq userId) }
            .singleOrNull()
            ?.let {
                AttemptRow(
                    id = it[QuizAttempts.id],
                    mode = runCatching { QuizMode.valueOf(it[QuizAttempts.quizMode]) }.getOrDefault(QuizMode.STUDY),
                    status = runCatching { QuizStatus.valueOf(it[QuizAttempts.status]) }.getOrDefault(QuizStatus.IN_PROGRESS),
                    questionIds = decodeJson(it[QuizAttempts.questionIds]),
                    startedAtMillis = it[QuizAttempts.startedAt],
                    finishedAtMillis = it[QuizAttempts.finishedAt],
                    correctCount = it[QuizAttempts.correctCount],
                    totalCount = it[QuizAttempts.totalCount],
                    resultJson = it[QuizAttempts.resultJson],
                )
            }
    }

    data class AttemptRow(
        val id: String,
        val mode: QuizMode,
        val status: QuizStatus,
        val questionIds: List<String>,
        val startedAtMillis: Long,
        val finishedAtMillis: Long?,
        val correctCount: Int,
        val totalCount: Int,
        val resultJson: String?,
    )

    suspend fun insertAnswer(userId: String, attemptId: String, questionId: String, choiceId: String, isCorrect: Boolean): Boolean = tx {
        val existing = QuizAnswers.selectAll().where {
            (QuizAnswers.attemptId eq attemptId) and (QuizAnswers.questionId eq questionId) and (QuizAnswers.userId eq userId)
        }.singleOrNull()
        if (existing != null) return@tx false
        QuizAnswers.insert {
            it[QuizAnswers.id] = newId()
            it[QuizAnswers.userId] = userId
            it[QuizAnswers.attemptId] = attemptId
            it[QuizAnswers.questionId] = questionId
            it[QuizAnswers.choiceId] = choiceId
            it[QuizAnswers.isCorrect] = isCorrect
            it[QuizAnswers.answeredAt] = nowMillis()
        }
        true
    }

    suspend fun answers(userId: String, attemptId: String): List<AnswerRow> = tx {
        QuizAnswers.selectAll().where { (QuizAnswers.attemptId eq attemptId) and (QuizAnswers.userId eq userId) }
            .map {
                AnswerRow(
                    questionId = it[QuizAnswers.questionId],
                    choiceId = it[QuizAnswers.choiceId],
                    isCorrect = it[QuizAnswers.isCorrect],
                    answeredAtMillis = it[QuizAnswers.answeredAt],
                )
            }
    }

    data class AnswerRow(val questionId: String, val choiceId: String, val isCorrect: Boolean, val answeredAtMillis: Long)

    suspend fun finishAttempt(userId: String, attemptId: String, result: io.medtech.backend.domain.QuizResult) = tx {
        QuizAttempts.update({ (QuizAttempts.id eq attemptId) and (QuizAttempts.userId eq userId) }) {
            it[QuizAttempts.status] = QuizStatus.COMPLETED.name
            it[QuizAttempts.finishedAt] = nowMillis()
            it[QuizAttempts.score] = result.score
            it[QuizAttempts.correctCount] = result.correct
            it[QuizAttempts.totalCount] = result.total
            it[QuizAttempts.resultJson] = encodeJson(result)
        }
    }

    suspend fun listAttempts(userId: String, status: QuizStatus?): List<AttemptRow> = tx {
        QuizAttempts.selectAll().where {
            (QuizAttempts.userId eq userId) and
                (if (status != null) QuizAttempts.status eq status.name else Op.TRUE)
        }.orderBy(QuizAttempts.startedAt to SortOrder.DESC).map {
            AttemptRow(
                id = it[QuizAttempts.id],
                mode = runCatching { QuizMode.valueOf(it[QuizAttempts.quizMode]) }.getOrDefault(QuizMode.STUDY),
                status = runCatching { QuizStatus.valueOf(it[QuizAttempts.status]) }.getOrDefault(QuizStatus.IN_PROGRESS),
                questionIds = decodeJson(it[QuizAttempts.questionIds]),
                startedAtMillis = it[QuizAttempts.startedAt],
                finishedAtMillis = it[QuizAttempts.finishedAt],
                correctCount = it[QuizAttempts.correctCount],
                totalCount = it[QuizAttempts.totalCount],
                resultJson = it[QuizAttempts.resultJson],
            )
        }
    }

    suspend fun answersTodayCount(userId: String, dayStartMillis: Long): Int = tx {
        QuizAnswers.selectAll().where { (QuizAnswers.userId eq userId) and (QuizAnswers.answeredAt greaterEq dayStartMillis) }.count().toInt()
    }

    suspend fun allAnswers(userId: String): List<AnswerRow> = tx {
        QuizAnswers.selectAll().where { QuizAnswers.userId eq userId }.map {
            AnswerRow(it[QuizAnswers.questionId], it[QuizAnswers.choiceId], it[QuizAnswers.isCorrect], it[QuizAnswers.answeredAt])
        }
    }

    suspend fun recentAttempts(userId: String, limit: Int): List<AttemptRow> =
        listAttempts(userId, QuizStatus.COMPLETED).take(limit)

    suspend fun deleteForUser(userId: String) = tx {
        QuizAnswers.deleteWhere { QuizAnswers.userId eq userId }
        QuizAttempts.deleteWhere { QuizAttempts.userId eq userId }
    }
}
