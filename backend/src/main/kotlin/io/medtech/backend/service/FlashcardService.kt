package io.medtech.backend.service

import io.medtech.backend.db.nowMillis
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.Flashcard
import io.medtech.backend.domain.FlashcardCreateRequest
import io.medtech.backend.domain.FlashcardHistoryEntry
import io.medtech.backend.domain.FlashcardQueue
import io.medtech.backend.domain.FlashcardUpdateRequest
import io.medtech.backend.domain.ImportRequest
import io.medtech.backend.domain.ImportResult
import io.medtech.backend.domain.ReviewRequest
import io.medtech.backend.domain.ReviewResponse
import io.medtech.backend.domain.ScheduleState
import io.medtech.backend.repository.FlashcardRepository
import io.medtech.backend.security.Sanitizer

class FlashcardService(
    private val flashcards: FlashcardRepository = FlashcardRepository(),
) {

    suspend fun list(userId: String, deck: String?, tag: String?, dueOnly: Boolean): List<Flashcard> =
        flashcards.list(userId, deck, tag, dueOnly)

    suspend fun create(userId: String, request: FlashcardCreateRequest): Flashcard {
        val front = Sanitizer.clean(request.front, 5000)
        val back = Sanitizer.clean(request.back, 20000)
        if (front.isBlank()) throw ApiException.validation("front must not be blank")
        if (back.isBlank()) throw ApiException.validation("back must not be blank")
        if (request.difficulty !in 1..3) throw ApiException.validation("difficulty must be 1, 2 or 3")
        val deck = flashcards.findOrCreateDeck(userId, request.deckId, request.deckTitle, request.subjectId)
        val id = flashcards.insert(
            userId = userId,
            deckId = deck.id,
            front = front,
            back = back,
            type = request.type,
            tags = request.tags.take(32).map { Sanitizer.cleanToken(it, 64) },
            subjectId = request.subjectId,
            topicId = request.topicId,
            difficulty = request.difficulty,
            note = Sanitizer.clean(request.note, 5000),
            favorite = request.favorite,
            imageRef = request.imageRef,
            source = request.source,
            dueAtMillis = nowMillis(),
        )
        return flashcards.findById(userId, id)!!
    }

    suspend fun update(userId: String, id: String, request: FlashcardUpdateRequest): Flashcard {
        val existing = flashcards.findById(userId, id) ?: throw ApiException.notFound("Flashcard not found")
        val deckId = when {
            request.deckId != null -> flashcards.findOrCreateDeck(userId, request.deckId, null, existing.subjectId).id
            else -> existing.deckId
        }
        val difficulty = request.difficulty ?: existing.difficulty
        if (difficulty !in 1..3) throw ApiException.validation("difficulty must be 1, 2 or 3")
        flashcards.updateCard(
            userId = userId,
            id = id,
            deckId = deckId,
            front = request.front?.let { Sanitizer.clean(it, 5000) } ?: existing.front,
            back = request.back?.let { Sanitizer.clean(it, 20000) } ?: existing.back,
            type = request.type ?: existing.type,
            tags = request.tags?.take(32)?.map { Sanitizer.cleanToken(it, 64) } ?: existing.tags,
            subjectId = request.subjectId ?: existing.subjectId,
            topicId = request.topicId ?: existing.topicId,
            difficulty = difficulty,
            note = request.note?.let { Sanitizer.clean(it, 5000) } ?: existing.note,
            favorite = request.favorite ?: existing.favorite,
            imageRef = request.imageRef ?: existing.imageRef,
            source = request.source ?: existing.source,
        )
        return flashcards.findById(userId, id)!!
    }

    suspend fun delete(userId: String, id: String) {
        if (!flashcards.delete(userId, id)) throw ApiException.notFound("Flashcard not found")
    }

    suspend fun review(userId: String, id: String, request: ReviewRequest): ReviewResponse {
        val cardState = flashcards.stateOf(userId, id) ?: throw ApiException.notFound("Flashcard not found")
        val nowMillis = request.reviewedAt?.toEpochMilliseconds() ?: nowMillis()
        val outcome = Fsrs.review(
            card = Fsrs.CardState(
                state = runCatching { ScheduleState.valueOf(cardState.state) }.getOrDefault(ScheduleState.NEW),
                stability = cardState.stability,
                difficulty = cardState.difficulty,
                dueAtMillis = cardState.dueAtMillis,
                lastReviewAtMillis = cardState.lastReviewAtMillis,
                reps = cardState.reps,
                lapses = cardState.lapses,
                learningStep = cardState.learningStep,
            ),
            rating = request.rating,
            nowMillis = nowMillis,
        )
        if (outcome.changed) {
            val history = flashcards.findById(userId, id)!!.history + outcome.history
            flashcards.updateSchedule(
                userId = userId,
                id = id,
                state = outcome.schedule.state,
                stability = outcome.schedule.stability,
                difficulty = outcome.schedule.difficulty,
                dueAtMillis = outcome.schedule.dueAt.toEpochMilliseconds(),
                lastReviewAtMillis = outcome.schedule.lastReviewAt?.toEpochMilliseconds(),
                reps = outcome.schedule.reps,
                lapses = outcome.schedule.lapses,
                learningStep = outcome.learningStep,
                history = history,
            )
            flashcards.insertReview(
                userId = userId,
                flashcardId = id,
                rating = request.rating.name,
                intervalDays = outcome.history.intervalDays,
                reviewedAtMillis = nowMillis,
            )
        }
        return ReviewResponse(schedule = outcome.schedule)
    }

    suspend fun queue(userId: String): FlashcardQueue {
        val all = flashcards.list(userId, deckId = null, tag = null, dueOnly = true)
        return FlashcardQueue(
            new = all.filter { it.schedule.state == ScheduleState.NEW },
            learning = all.filter { it.schedule.state == ScheduleState.LEARNING || it.schedule.state == ScheduleState.RELEARNING },
            review = all.filter { it.schedule.state == ScheduleState.REVIEW },
        )
    }

    suspend fun importRows(userId: String, request: ImportRequest): ImportResult {
        if (request.rows.size > 2000) throw ApiException.validation("Import is limited to 2000 rows per request")
        var imported = 0
        for (row in request.rows) {
            if (row.front.isBlank() || row.back.isBlank()) continue
            create(
                userId,
                FlashcardCreateRequest(
                    deckId = row.deckId,
                    deckTitle = row.deckTitle,
                    front = row.front,
                    back = row.back,
                    tags = row.tags,
                    subjectId = row.subjectId,
                    topicId = row.topicId,
                ),
            )
            imported++
        }
        return ImportResult(imported = imported)
    }

    suspend fun exportCsv(userId: String): String {
        val cards = flashcards.exportAll(userId)
        val header = "id,deckId,front,back,type,tags,subjectId,topicId,difficulty,state,dueAt,reps,lapses"
        val rows = cards.joinToString("\n") { card ->
            listOf(
                card.id,
                card.deckId.orEmpty(),
                csvEscape(card.front),
                csvEscape(card.back),
                card.type.name,
                csvEscape(card.tags.joinToString(";")),
                card.subjectId.orEmpty(),
                card.topicId.orEmpty(),
                card.difficulty.toString(),
                card.schedule.state.name,
                card.schedule.dueAt.toString(),
                card.schedule.reps.toString(),
                card.schedule.lapses.toString(),
            ).joinToString(",")
        }
        return header + "\n" + rows
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) "\"$escaped\"" else escaped
    }
}
