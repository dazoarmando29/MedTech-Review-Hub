package io.medtech.backend.service

import io.medtech.backend.db.now
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.EditorialStatus
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.repository.AdminLessonRow
import io.medtech.backend.repository.AdminQuestionRow
import io.medtech.backend.repository.AdminSubjectRow
import io.medtech.backend.repository.AdminTopicRow
import io.medtech.backend.repository.AuditRow
import io.medtech.backend.repository.ContentRepository
import io.medtech.backend.repository.ReportRow
import io.medtech.backend.repository.UserRepository
import io.medtech.backend.security.Sanitizer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AdminSubjectView(
    val id: String,
    val name: String,
    val shortName: String = "",
    val description: String = "",
    val blueprintNote: String = "",
    val colorToken: String = "gray",
    val editorialStatus: String = "DRAFT",
    val needsVerification: Boolean = false,
    val updatedAt: Instant,
)

@Serializable
data class AdminTopicView(
    val id: String,
    val subjectId: String,
    val name: String,
    val editorialStatus: String = "DRAFT",
    val needsVerification: Boolean = false,
    val updatedAt: Instant,
)

@Serializable
data class AdminLessonView(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val summary: String = "",
    val readingMinutes: Int = 10,
    val difficulty: String = "INTERMEDIATE",
    val keywords: List<String> = emptyList(),
    val content: Lesson,
    val editorialStatus: String = "DRAFT",
    val needsVerification: Boolean = false,
    val updatedAt: Instant,
)

@Serializable
data class AdminQuestionView(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val difficulty: String = "MEDIUM",
    val question: QuizQuestion,
    val editorialStatus: String = "DRAFT",
    val needsVerification: Boolean = false,
    val updatedAt: Instant,
)

@Serializable
data class AuditView(
    val id: String,
    val actorUserId: String? = null,
    val action: String,
    val entityType: String,
    val entityId: String = "",
    val detail: String = "",
    val createdAt: Instant,
)

@Serializable
data class AdminReportView(
    val id: String,
    val userId: String,
    val questionId: String,
    val reason: String,
    val details: String = "",
    val status: String = "OPEN",
    val createdAt: Instant,
)

@Serializable
data class AdminSubjectUpsert(
    val id: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val description: String? = null,
    val blueprintNote: String? = null,
    val colorToken: String? = null,
    val editorialStatus: String? = null,
    val needsVerification: Boolean? = null,
)

@Serializable
data class AdminTopicUpsert(
    val id: String? = null,
    val subjectId: String? = null,
    val name: String? = null,
    val editorialStatus: String? = null,
    val needsVerification: Boolean? = null,
)

@Serializable
data class AdminLessonUpsert(
    val id: String? = null,
    val subjectId: String? = null,
    val topicId: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val readingMinutes: Int? = null,
    val difficulty: String? = null,
    val keywords: List<String>? = null,
    val content: Lesson? = null,
    val editorialStatus: String? = null,
    val needsVerification: Boolean? = null,
)

@Serializable
data class AdminQuestionUpsert(
    val id: String? = null,
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: String? = null,
    val question: QuizQuestion? = null,
    val editorialStatus: String? = null,
    val needsVerification: Boolean? = null,
)

class AdminService(
    private val contentRepo: ContentRepository = ContentRepository(),
    private val users: UserRepository = UserRepository(),
) {

    companion object {
        private val ALLOWED_TRANSITIONS = mapOf(
            EditorialStatus.DRAFT.name to setOf(EditorialStatus.IN_REVIEW.name),
            EditorialStatus.IN_REVIEW.name to setOf(EditorialStatus.PUBLISHED.name),
            EditorialStatus.PUBLISHED.name to setOf(EditorialStatus.ARCHIVED.name),
            EditorialStatus.ARCHIVED.name to emptySet<String>(),
        )
    }

    suspend fun requireAdmin(userId: String) {
        val user = users.findById(userId) ?: throw ApiException.unauthorized()
        if (user.role != "ADMIN") throw ApiException.forbidden("Admin role required")
    }

    private fun transitionOrThrow(current: String, next: String) {
        if (next == current) return
        val allowed = ALLOWED_TRANSITIONS[current] ?: emptySet()
        if (next !in allowed) {
            throw ApiException.validation(
                "editorialStatus transition $current -> $next is not allowed",
                mapOf("editorialStatus" to "allowed next: ${allowed.joinToString(", ").ifEmpty { "none" }}"),
            )
        }
    }

    // ---- subjects ----

    suspend fun listSubjects(): List<AdminSubjectView> =
        contentRepo.listSubjects().map { it.toView() }

    suspend fun createSubject(actorId: String, request: AdminSubjectUpsert): AdminSubjectView {
        val id = request.id?.let { Sanitizer.cleanToken(it, 64) }
            ?: throw ApiException.validation("id is required for create")
        val name = request.name?.let { Sanitizer.clean(it, 160) }
            ?: throw ApiException.validation("name is required for create")
        val status = request.editorialStatus ?: EditorialStatus.DRAFT.name
        transitionOrThrow(EditorialStatus.DRAFT.name, status)
        contentRepo.upsertSubject(
            AdminSubjectRow(
                id = id,
                name = name,
                shortName = Sanitizer.clean(request.shortName ?: name, 80),
                description = Sanitizer.clean(request.description ?: "", 8000),
                blueprintNote = Sanitizer.clean(request.blueprintNote ?: "", 8000),
                colorToken = Sanitizer.cleanToken(request.colorToken ?: "gray", 32),
                editorialStatus = status,
                needsVerification = request.needsVerification ?: false,
                updatedAt = now(),
            )
        )
        audit(actorId, "CREATE", "subject", id)
        return contentRepo.getSubject(id)!!.toView()
    }

    suspend fun updateSubject(actorId: String, id: String, request: AdminSubjectUpsert): AdminSubjectView {
        val existing = contentRepo.getSubject(id) ?: throw ApiException.notFound("Subject not found")
        request.editorialStatus?.let { transitionOrThrow(existing.editorialStatus, it) }
        contentRepo.upsertSubject(
            existing.copy(
                name = request.name?.let { n -> Sanitizer.clean(n, 160) } ?: existing.name,
                shortName = request.shortName?.let { n -> Sanitizer.clean(n, 80) } ?: existing.shortName,
                description = request.description?.let { n -> Sanitizer.clean(n, 8000) } ?: existing.description,
                blueprintNote = request.blueprintNote?.let { n -> Sanitizer.clean(n, 8000) } ?: existing.blueprintNote,
                colorToken = request.colorToken?.let { n -> Sanitizer.cleanToken(n, 32) } ?: existing.colorToken,
                editorialStatus = request.editorialStatus ?: existing.editorialStatus,
                needsVerification = request.needsVerification ?: existing.needsVerification,
                updatedAt = now(),
            )
        )
        audit(actorId, "UPDATE", "subject", id)
        return contentRepo.getSubject(id)!!.toView()
    }

    // ---- topics ----

    suspend fun listTopics(subjectId: String?): List<AdminTopicView> =
        contentRepo.listTopics(subjectId).map { it.toView() }

    suspend fun createTopic(actorId: String, request: AdminTopicUpsert): AdminTopicView {
        val id = request.id?.let { Sanitizer.cleanToken(it, 64) }
            ?: throw ApiException.validation("id is required for create")
        val subjectId = request.subjectId ?: throw ApiException.validation("subjectId is required for create")
        val name = request.name?.let { Sanitizer.clean(it, 160) }
            ?: throw ApiException.validation("name is required for create")
        val status = request.editorialStatus ?: EditorialStatus.DRAFT.name
        transitionOrThrow(EditorialStatus.DRAFT.name, status)
        contentRepo.upsertTopic(
            AdminTopicRow(
                id = id,
                subjectId = subjectId,
                name = name,
                editorialStatus = status,
                needsVerification = request.needsVerification ?: false,
                updatedAt = now(),
            )
        )
        audit(actorId, "CREATE", "topic", id)
        return contentRepo.getTopic(id)!!.toView()
    }

    suspend fun updateTopic(actorId: String, id: String, request: AdminTopicUpsert): AdminTopicView {
        val existing = contentRepo.getTopic(id) ?: throw ApiException.notFound("Topic not found")
        request.editorialStatus?.let { transitionOrThrow(existing.editorialStatus, it) }
        contentRepo.upsertTopic(
            existing.copy(
                subjectId = request.subjectId ?: existing.subjectId,
                name = request.name?.let { n -> Sanitizer.clean(n, 160) } ?: existing.name,
                editorialStatus = request.editorialStatus ?: existing.editorialStatus,
                needsVerification = request.needsVerification ?: existing.needsVerification,
                updatedAt = now(),
            )
        )
        audit(actorId, "UPDATE", "topic", id)
        return contentRepo.getTopic(id)!!.toView()
    }

    // ---- lessons ----

    suspend fun listLessons(subjectId: String?, topicId: String?): List<AdminLessonView> =
        contentRepo.listLessons(subjectId, topicId, publishedOnly = false).map { it.toView() }

    suspend fun createLesson(actorId: String, request: AdminLessonUpsert): AdminLessonView {
        val id = request.id?.let { Sanitizer.cleanToken(it, 128) }
            ?: throw ApiException.validation("id is required for create")
        val subjectId = request.subjectId ?: throw ApiException.validation("subjectId is required for create")
        val topicId = request.topicId ?: throw ApiException.validation("topicId is required for create")
        val title = request.title?.let { Sanitizer.clean(it, 240) }
            ?: throw ApiException.validation("title is required for create")
        val content = request.content ?: throw ApiException.validation("content is required for create")
        val status = request.editorialStatus ?: EditorialStatus.DRAFT.name
        transitionOrThrow(EditorialStatus.DRAFT.name, status)
        contentRepo.upsertLesson(
            AdminLessonRow(
                id = id,
                subjectId = subjectId,
                topicId = topicId,
                title = title,
                summary = Sanitizer.clean(request.summary ?: content.summary, 8000),
                readingMinutes = request.readingMinutes ?: content.readingMinutes,
                difficulty = request.difficulty ?: content.difficulty,
                keywords = request.keywords ?: content.keywords,
                content = content.copy(id = id, subjectId = subjectId, topicId = topicId, title = title),
                editorialStatus = status,
                needsVerification = request.needsVerification ?: false,
                updatedAt = now(),
            )
        )
        audit(actorId, "CREATE", "lesson", id)
        return contentRepo.getLesson(id)!!.toView()
    }

    suspend fun updateLesson(actorId: String, id: String, request: AdminLessonUpsert): AdminLessonView {
        val existing = contentRepo.getLesson(id) ?: throw ApiException.notFound("Lesson not found")
        request.editorialStatus?.let { transitionOrThrow(existing.editorialStatus, it) }
        val content = request.content ?: existing.content
        contentRepo.upsertLesson(
            existing.copy(
                subjectId = request.subjectId ?: existing.subjectId,
                topicId = request.topicId ?: existing.topicId,
                title = request.title?.let { n -> Sanitizer.clean(n, 240) } ?: existing.title,
                summary = request.summary?.let { n -> Sanitizer.clean(n, 8000) } ?: existing.summary,
                readingMinutes = request.readingMinutes ?: existing.readingMinutes,
                difficulty = request.difficulty ?: existing.difficulty,
                keywords = request.keywords ?: existing.keywords,
                content = content,
                editorialStatus = request.editorialStatus ?: existing.editorialStatus,
                needsVerification = request.needsVerification ?: existing.needsVerification,
                updatedAt = now(),
            )
        )
        audit(actorId, "UPDATE", "lesson", id)
        return contentRepo.getLesson(id)!!.toView()
    }

    suspend fun publishLesson(actorId: String, id: String): AdminLessonView {
        val existing = contentRepo.getLesson(id) ?: throw ApiException.notFound("Lesson not found")
        transitionOrThrow(existing.editorialStatus, EditorialStatus.PUBLISHED.name)
        contentRepo.insertLessonRevision(existing.id, existing.content, actorId, "Published draft")
        contentRepo.upsertLesson(
            existing.copy(
                editorialStatus = EditorialStatus.PUBLISHED.name,
                needsVerification = false,
                updatedAt = now(),
            )
        )
        audit(actorId, "PUBLISH", "lesson", id)
        return contentRepo.getLesson(id)!!.toView()
    }

    // ---- questions ----

    suspend fun listQuestions(subjectId: String?, topicId: String?): List<AdminQuestionView> =
        contentRepo.listQuestions(subjectId, topicId, publishedOnly = false).map { it.toView() }

    suspend fun createQuestion(actorId: String, request: AdminQuestionUpsert): AdminQuestionView {
        val id = request.id?.let { Sanitizer.cleanToken(it, 128) }
            ?: throw ApiException.validation("id is required for create")
        val question = request.question ?: throw ApiException.validation("question is required for create")
        val status = request.editorialStatus ?: EditorialStatus.DRAFT.name
        transitionOrThrow(EditorialStatus.DRAFT.name, status)
        contentRepo.upsertQuestion(
            AdminQuestionRow(
                id = id,
                subjectId = request.subjectId ?: question.subjectId,
                topicId = request.topicId ?: question.topicId,
                difficulty = request.difficulty ?: question.difficulty.name,
                question = question.copy(id = id),
                editorialStatus = status,
                needsVerification = request.needsVerification ?: false,
                updatedAt = now(),
            )
        )
        audit(actorId, "CREATE", "question", id)
        return contentRepo.getQuestion(id)!!.toView()
    }

    suspend fun updateQuestion(actorId: String, id: String, request: AdminQuestionUpsert): AdminQuestionView {
        val existing = contentRepo.getQuestion(id) ?: throw ApiException.notFound("Question not found")
        request.editorialStatus?.let { transitionOrThrow(existing.editorialStatus, it) }
        contentRepo.upsertQuestion(
            existing.copy(
                subjectId = request.subjectId ?: existing.subjectId,
                topicId = request.topicId ?: existing.topicId,
                difficulty = request.difficulty ?: existing.difficulty,
                question = request.question ?: existing.question,
                editorialStatus = request.editorialStatus ?: existing.editorialStatus,
                needsVerification = request.needsVerification ?: existing.needsVerification,
                updatedAt = now(),
            )
        )
        audit(actorId, "UPDATE", "question", id)
        return contentRepo.getQuestion(id)!!.toView()
    }

    suspend fun reports(): List<AdminReportView> = contentRepo.listReports().map {
        AdminReportView(
            id = it.id,
            userId = it.userId,
            questionId = it.questionId,
            reason = it.reason,
            details = it.details,
            status = it.status,
            createdAt = it.createdAt,
        )
    }

    suspend fun auditTrail(): List<AuditView> = contentRepo.listAudit().map {
        AuditView(
            id = it.id,
            actorUserId = it.actorUserId,
            action = it.action,
            entityType = it.entityType,
            entityId = it.entityId,
            detail = it.detail,
            createdAt = it.createdAt,
        )
    }

    private suspend fun audit(actorId: String, action: String, entityType: String, entityId: String) {
        contentRepo.insertAudit(actorId, action, entityType, entityId, "{}")
    }

    private fun AdminSubjectRow.toView() = AdminSubjectView(id, name, shortName, description, blueprintNote, colorToken, editorialStatus, needsVerification, updatedAt)
    private fun AdminTopicRow.toView() = AdminTopicView(id, subjectId, name, editorialStatus, needsVerification, updatedAt)
    private fun AdminLessonRow.toView() = AdminLessonView(id, subjectId, topicId, title, summary, readingMinutes, difficulty, keywords, content, editorialStatus, needsVerification, updatedAt)
    private fun AdminQuestionRow.toView() = AdminQuestionView(id, subjectId, topicId, difficulty, question, editorialStatus, needsVerification, updatedAt)
}
