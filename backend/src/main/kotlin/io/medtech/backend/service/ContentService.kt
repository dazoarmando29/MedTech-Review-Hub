package io.medtech.backend.service

import io.medtech.backend.content.ContentStore
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.LessonSummary
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.SubjectDto
import io.medtech.backend.domain.TopicDto
import io.medtech.backend.repository.ContentRepository

/**
 * Public read-only content view: the static review library (shared/content/ or the
 * bundled fallback sample) merged with admin-managed rows that reached PUBLISHED.
 */
class ContentService(
    val store: ContentStore,
    private val contentRepo: ContentRepository = ContentRepository(),
) {

    suspend fun seed() {
        contentRepo.seedGlossary(store.glossary)
        val refs = store.lessons.values.flatMap { lesson ->
            lesson.references.map { ref -> Triple(lesson.id, ref.title, ref.detail) }
        }
        contentRepo.seedSourceReferences(refs)
    }

    suspend fun subjects(): List<SubjectDto> {
        val static = store.subjects().associateBy { it.id }
        val publishedDb = contentRepo.listSubjects().filter { it.editorialStatus == "PUBLISHED" }
        val merged = static.toMutableMap()
        for (row in publishedDb) {
            val topics = contentRepo.listTopics(row.id).filter { it.editorialStatus == "PUBLISHED" }
                .map { TopicDto(id = it.id, name = it.name) }
            merged[row.id] = SubjectDto(
                id = row.id,
                name = row.name,
                shortName = row.shortName,
                description = row.description,
                blueprintNote = row.blueprintNote,
                colorToken = row.colorToken,
                topics = topics,
            )
        }
        return merged.values.sortedBy { it.id }
    }

    suspend fun lesson(id: String): Lesson? {
        val db = contentRepo.getLesson(id)
        if (db != null && db.editorialStatus == "PUBLISHED") return db.content
        return store.lesson(id)
    }

    suspend fun lessonSummaries(subject: String?, topic: String?, query: String?): List<LessonSummary> {
        val dbLessons = contentRepo.listLessons(subject, topic, publishedOnly = true).map { it.content.toSummary() }
        val static = store.lessonSummaries().map { it.toSummary() }
        val merged = (dbLessons + static).associateBy { it.id }
        return merged.values
            .filter { subject == null || it.subjectId == subject }
            .filter { topic == null || it.topicId == topic }
            .filter {
                query.isNullOrBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.summary.contains(query, ignoreCase = true) ||
                    it.keywords.any { kw -> kw.contains(query, ignoreCase = true) }
            }
            .sortedBy { it.id }
    }

    suspend fun questions(subject: String?, topic: String?, limit: Int?): List<QuizQuestion> {
        val dbQuestions = contentRepo.listQuestions(subject, topic, publishedOnly = true).map { it.question }
        val static = store.questions(subject, topic)
        val merged = (dbQuestions + static).associateBy { it.id }.values
            .filter { subject == null || it.subjectId == subject }
            .filter { topic == null || it.topicId == topic }
            .sortedBy { it.id }
        return if (limit != null && limit > 0) merged.take(limit) else merged
    }

    suspend fun allQuestions(): List<QuizQuestion> = questions(null, null, null)

    fun formulas() = store.formulas

    fun memes() = store.memes

    fun glossary() = store.glossary

    fun decks() = store.decks
}
