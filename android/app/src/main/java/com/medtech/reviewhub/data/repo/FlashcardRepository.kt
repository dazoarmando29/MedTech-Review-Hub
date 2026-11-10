package com.medtech.reviewhub.data.repo

import com.medtech.reviewhub.data.db.FlashcardDao
import com.medtech.reviewhub.data.db.FlashcardEntity
import com.medtech.reviewhub.data.db.ReviewLogEntity
import com.medtech.reviewhub.domain.CardState
import com.medtech.reviewhub.domain.Rating
import com.medtech.reviewhub.domain.ReviewLogEntry
import com.medtech.reviewhub.domain.SpacedRepetition
import com.medtech.reviewhub.domain.model.CardSchedule
import com.medtech.reviewhub.domain.model.ContentFlashcard
import com.medtech.reviewhub.domain.model.FlashcardRecord
import java.util.UUID

/**
 * Flashcard storage + FSRS-4.5 scheduling (shared/FSRS.md) + CSV import/export.
 * All operations are local (Room); sync to the backend is best-effort elsewhere.
 */
class FlashcardRepository(private val dao: FlashcardDao) {

    fun observeCards() = dao.all()

    suspend fun card(id: String): FlashcardRecord? = dao.byId(id)?.toRecord()

    suspend fun allCards(): List<FlashcardRecord> = dao.allOnce().map { it.toRecord() }

    suspend fun deckCards(deckId: String): List<FlashcardRecord> = dao.byDeck(deckId).map { it.toRecord() }

    suspend fun favoriteCards(): List<FlashcardRecord> = dao.favorites().map { it.toRecord() }

    suspend fun cardsForReview(): List<FlashcardRecord> = dao.needsReview().map { it.toRecord() }

    suspend fun subjectCards(subjectId: String): List<FlashcardRecord> = dao.bySubject(subjectId).map { it.toRecord() }

    data class Queue(
        val newCards: List<FlashcardRecord>,
        val learning: List<FlashcardRecord>,
        val review: List<FlashcardRecord>
    ) {
        val total: Int get() = newCards.size + learning.size + review.size
    }

    /**
     * Today's queue: due <= now ordered by ascending dueAt, split by state and
     * capped by the daily new / review limits.
     */
    suspend fun queue(nowMs: Long = System.currentTimeMillis(), newLimit: Int = 20, reviewLimit: Int = 200): Queue {
        val due = dao.dueNow(nowMs).map { it.toRecord() }
        val newCards = due.filter { it.schedule.state == CardState.NEW }.take(newLimit)
        val learning = due.filter { it.schedule.state == CardState.LEARNING || it.schedule.state == CardState.RELEARNING }
        val review = due.filter { it.schedule.state == CardState.REVIEW }.take(reviewLimit)
        return Queue(newCards, learning, review)
    }

    suspend fun counts(nowMs: Long = System.currentTimeMillis()): Triple<Int, Int, Int> =
        Triple(dao.countNewDue(nowMs), dao.countLearningDue(nowMs), dao.countReviewDue(nowMs))

    suspend fun totalCards(): Int = dao.countAll()

    /** Applies one FSRS review rating. Returns the new schedule + interval. */
    suspend fun review(cardId: String, rating: Rating, nowMs: Long = System.currentTimeMillis()): CardSchedule? {
        val entity = dao.byId(cardId) ?: return null
        val before = entity.toRecord()
        val outcome = SpacedRepetition.review(before.schedule, rating, nowMs)
        if (outcome.ignored) return before.schedule
        val history = before.history + ReviewLogEntry(nowMs, rating, outcome.intervalDays)
        dao.upsert(entity.copy(
            state = outcome.schedule.state.name,
            stability = outcome.schedule.stability,
            fsrsDifficulty = outcome.schedule.difficulty,
            dueAtMs = outcome.schedule.dueAtMs,
            lastReviewAtMs = outcome.schedule.lastReviewAtMs,
            reps = outcome.schedule.reps,
            lapses = outcome.schedule.lapses,
            stepIndex = outcome.schedule.stepIndex,
            historyJson = encodeHistory(history),
            updatedAtMs = nowMs
        ))
        dao.insertLog(ReviewLogEntity(cardId = cardId, atMs = nowMs, rating = rating.name, intervalDays = outcome.intervalDays))
        return outcome.schedule
    }

    suspend fun reviewsTodayCount(todayStartMs: Long): Int = dao.reviewsSince(todayStartMs)

    suspend fun save(record: FlashcardRecord) = dao.upsert(record.toEntity())

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(favorite = favorite, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun setNote(id: String, note: String) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(note = note, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun history(cardId: String): List<ReviewLogEntry> =
        dao.logsFor(cardId).map { ReviewLogEntry(it.atMs, Rating.fromName(it.rating), it.intervalDays) }

    suspend fun createManual(front: String, back: String, deckId: String = "manual", tags: List<String> = emptyList(),
                            subjectId: String? = null, topicId: String? = null, type: String = "BASIC", note: String = ""): FlashcardRecord {
        val now = System.currentTimeMillis()
        val record = FlashcardRecord(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            front = front,
            back = back,
            type = type,
            tags = tags,
            subjectId = subjectId,
            topicId = topicId,
            note = note,
            sourceKind = "MANUAL",
            schedule = CardSchedule(state = CardState.NEW, dueAtMs = now),
            createdAtMs = now,
            updatedAtMs = now
        )
        dao.upsert(record.toEntity())
        return record
    }

    /** Cards generated from missed quiz questions (source.kind = QUIZ_MISS). */
    suspend fun createFromMissedQuestion(questionId: String, stem: String, answer: String,
                                         subjectId: String?, topicId: String?, explanation: String): FlashcardRecord {
        val now = System.currentTimeMillis()
        val record = FlashcardRecord(
            id = UUID.randomUUID().toString(),
            deckId = "quiz-missed",
            front = stem,
            back = answer + if (explanation.isNotBlank()) "\n\n$explanation" else "",
            type = "BASIC",
            tags = listOf("quiz-miss"),
            subjectId = subjectId,
            topicId = topicId,
            sourceKind = "QUIZ_MISS",
            sourceNeedsReview = true,
            schedule = CardSchedule(state = CardState.NEW, dueAtMs = now),
            createdAtMs = now,
            updatedAtMs = now
        )
        dao.upsert(record.toEntity())
        return record
    }

    suspend fun createFromContent(card: ContentFlashcard, deckId: String, subjectId: String?, topicId: String?) {
        val now = System.currentTimeMillis()
        val record = FlashcardRecord(
            id = card.id,
            deckId = deckId,
            front = card.front,
            back = card.back,
            type = card.type,
            tags = card.tags,
            subjectId = subjectId,
            topicId = topicId,
            difficulty = card.difficulty,
            note = card.note,
            sourceKind = card.source?.kind ?: "MANUAL",
            sourceLessonId = card.source?.lessonId,
            schedule = CardSchedule(state = CardState.NEW, dueAtMs = now),
            createdAtMs = now,
            updatedAtMs = now
        )
        dao.upsert(record.toEntity())
    }

    suspend fun importMissingContentCards(cards: List<ContentFlashcard>, deckId: String, subjectId: String?, topicId: String?) {
        for (card in cards) {
            if (dao.byId(card.id) == null) createFromContent(card, deckId, subjectId, topicId)
        }
    }

    // ---- CSV import/export (Anki-style: front,back,tags) ----------------

    fun exportCsv(records: List<FlashcardRecord>): String {
        val sb = StringBuilder("front,back,tags,type,note\n")
        for (r in records) {
            sb.append(csv(r.front)).append(',').append(csv(r.back)).append(',')
                .append(csv(r.tags.joinToString(" "))).append(',')
                .append(csv(r.type)).append(',').append(csv(r.note)).append('\n')
        }
        return sb.toString()
    }

    suspend fun importCsv(csvText: String, deckId: String = "imported"): Int {
        val lines = csvText.lineSequence().filter { it.isNotBlank() }.toList()
        var count = 0
        for (line in lines) {
            if (line.startsWith("front,back", ignoreCase = true)) continue
            val cols = parseCsvLine(line)
            if (cols.size < 2) continue
            val front = cols[0].trim()
            val back = cols[1].trim()
            if (front.isEmpty() || back.isEmpty()) continue
            val tags = cols.getOrElse(2) { "" }.split(' ', ',', ';').filter { it.isNotBlank() }
            createManual(
                front = front,
                back = back,
                deckId = deckId,
                tags = tags,
                type = cols.getOrElse(3) { "BASIC" }.ifBlank { "BASIC" },
                note = cols.getOrElse(4) { "" }
            )
            count++
        }
        return count
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    companion object {
        private val historyJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun encodeHistory(history: List<ReviewLogEntry>): String =
            historyJson.encodeToString(ListSerializerHolder.serializer, history)

        fun decodeHistory(raw: String): List<ReviewLogEntry> =
            if (raw.isBlank()) emptyList()
            else runCatching { historyJson.decodeFromString(ListSerializerHolder.serializer, raw) }.getOrDefault(emptyList())
    }
}

private object ListSerializerHolder {
    val serializer = kotlinx.serialization.builtins.ListSerializer(ReviewLogEntry.serializer())
}

// ---- Entity <-> domain mapping ----------------------------------------

fun FlashcardEntity.toRecord(): FlashcardRecord = FlashcardRecord(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    type = type,
    tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    subjectId = subjectId,
    topicId = topicId,
    difficulty = difficulty,
    note = note,
    favorite = favorite,
    imageRef = imageRef,
    sourceKind = sourceKind,
    sourceLessonId = sourceLessonId,
    sourcePdfId = sourcePdfId,
    sourcePage = sourcePage,
    sourceNeedsReview = sourceNeedsReview,
    schedule = CardSchedule(
        state = runCatching { CardState.valueOf(state) }.getOrDefault(CardState.NEW),
        stability = stability,
        difficulty = fsrsDifficulty,
        dueAtMs = dueAtMs,
        lastReviewAtMs = lastReviewAtMs,
        reps = reps,
        lapses = lapses,
        stepIndex = stepIndex
    ),
    history = FlashcardRepository.decodeHistory(historyJson),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

fun FlashcardRecord.toEntity(): FlashcardEntity = FlashcardEntity(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    type = type,
    tagsCsv = tags.joinToString(","),
    subjectId = subjectId,
    topicId = topicId,
    difficulty = difficulty,
    note = note,
    favorite = favorite,
    imageRef = imageRef,
    sourceKind = sourceKind,
    sourceLessonId = sourceLessonId,
    sourcePdfId = sourcePdfId,
    sourcePage = sourcePage,
    sourceNeedsReview = sourceNeedsReview,
    state = schedule.state.name,
    stability = schedule.stability,
    fsrsDifficulty = schedule.difficulty,
    dueAtMs = schedule.dueAtMs,
    lastReviewAtMs = schedule.lastReviewAtMs,
    reps = schedule.reps,
    lapses = schedule.lapses,
    stepIndex = schedule.stepIndex,
    historyJson = FlashcardRepository.encodeHistory(history),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)
