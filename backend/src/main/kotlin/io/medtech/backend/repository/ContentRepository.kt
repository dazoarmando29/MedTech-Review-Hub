package io.medtech.backend.repository

import io.medtech.backend.db.AuditLog
import io.medtech.backend.db.ContentReports
import io.medtech.backend.db.GlossaryTerms
import io.medtech.backend.db.LessonRevisions
import io.medtech.backend.db.Lessons
import io.medtech.backend.db.QuizQuestions
import io.medtech.backend.db.SourceReferences
import io.medtech.backend.db.Subjects
import io.medtech.backend.db.Topics
import io.medtech.backend.db.newId
import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.db.tx
import io.medtech.backend.domain.EditorialStatus
import io.medtech.backend.domain.GlossaryTerm
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

data class AdminSubjectRow(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val blueprintNote: String,
    val colorToken: String,
    val editorialStatus: String,
    val needsVerification: Boolean,
    val updatedAt: Instant,
)

data class AdminTopicRow(
    val id: String,
    val subjectId: String,
    val name: String,
    val editorialStatus: String,
    val needsVerification: Boolean,
    val updatedAt: Instant,
)

data class AdminLessonRow(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val summary: String,
    val readingMinutes: Int,
    val difficulty: String,
    val keywords: List<String>,
    val content: Lesson,
    val editorialStatus: String,
    val needsVerification: Boolean,
    val updatedAt: Instant,
)

data class AdminQuestionRow(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val difficulty: String,
    val question: QuizQuestion,
    val editorialStatus: String,
    val needsVerification: Boolean,
    val updatedAt: Instant,
)

data class AuditRow(
    val id: String,
    val actorUserId: String?,
    val action: String,
    val entityType: String,
    val entityId: String,
    val detail: String,
    val createdAt: Instant,
)

data class ReportRow(
    val id: String,
    val userId: String,
    val questionId: String,
    val reason: String,
    val details: String,
    val status: String,
    val createdAt: Instant,
)

class ContentRepository {

    suspend fun listSubjects(): List<AdminSubjectRow> = tx {
        Subjects.selectAll().orderBy(Subjects.id to SortOrder.ASC).map { it.toSubjectRow() }
    }

    suspend fun getSubject(id: String): AdminSubjectRow? = tx {
        Subjects.selectAll().where { Subjects.id eq id }.singleOrNull()?.toSubjectRow()
    }

    suspend fun upsertSubject(row: AdminSubjectRow) = tx {
        val exists = Subjects.selectAll().where { Subjects.id eq row.id }.singleOrNull() != null
        if (exists) {
            Subjects.update({ Subjects.id eq row.id }) {
                it[name] = row.name
                it[shortName] = row.shortName
                it[description] = row.description
                it[blueprintNote] = row.blueprintNote
                it[colorToken] = row.colorToken
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        } else {
            Subjects.insert {
                it[Subjects.id] = row.id
                it[Subjects.name] = row.name
                it[shortName] = row.shortName
                it[description] = row.description
                it[blueprintNote] = row.blueprintNote
                it[colorToken] = row.colorToken
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        }
    }

    suspend fun listTopics(subjectId: String?): List<AdminTopicRow> = tx {
        Topics.selectAll().where { if (subjectId != null) Topics.subjectId eq subjectId else Op.TRUE }
            .orderBy(Topics.id to SortOrder.ASC).map { it.toTopicRow() }
    }

    suspend fun getTopic(id: String): AdminTopicRow? = tx {
        Topics.selectAll().where { Topics.id eq id }.singleOrNull()?.toTopicRow()
    }

    suspend fun upsertTopic(row: AdminTopicRow) = tx {
        val exists = Topics.selectAll().where { Topics.id eq row.id }.singleOrNull() != null
        if (exists) {
            Topics.update({ Topics.id eq row.id }) {
                it[subjectId] = row.subjectId
                it[name] = row.name
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        } else {
            Topics.insert {
                it[Topics.id] = row.id
                it[subjectId] = row.subjectId
                it[name] = row.name
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        }
    }

    suspend fun listLessons(subjectId: String?, topicId: String?, publishedOnly: Boolean): List<AdminLessonRow> = tx {
        Lessons.selectAll().where {
            (if (subjectId != null) Lessons.subjectId eq subjectId else Op.TRUE) and
                (if (topicId != null) Lessons.topicId eq topicId else Op.TRUE) and
                (if (publishedOnly) Lessons.editorialStatus eq EditorialStatus.PUBLISHED.name else Op.TRUE)
        }.orderBy(Lessons.id to SortOrder.ASC).map { it.toLessonRow() }
    }

    suspend fun getLesson(id: String): AdminLessonRow? = tx {
        Lessons.selectAll().where { Lessons.id eq id }.singleOrNull()?.toLessonRow()
    }

    suspend fun upsertLesson(row: AdminLessonRow) = tx {
        val exists = Lessons.selectAll().where { Lessons.id eq row.id }.singleOrNull() != null
        if (exists) {
            Lessons.update({ Lessons.id eq row.id }) {
                it[subjectId] = row.subjectId
                it[topicId] = row.topicId
                it[title] = row.title
                it[summary] = row.summary
                it[readingMinutes] = row.readingMinutes
                it[difficulty] = row.difficulty
                it[keywordsJson] = encodeJson(row.keywords)
                it[contentJson] = encodeJson(row.content)
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        } else {
            Lessons.insert {
                it[Lessons.id] = row.id
                it[subjectId] = row.subjectId
                it[topicId] = row.topicId
                it[title] = row.title
                it[summary] = row.summary
                it[readingMinutes] = row.readingMinutes
                it[difficulty] = row.difficulty
                it[keywordsJson] = encodeJson(row.keywords)
                it[contentJson] = encodeJson(row.content)
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        }
    }

    suspend fun insertLessonRevision(lessonId: String, content: Lesson, createdBy: String?, note: String) = tx {
        val count = LessonRevisions.selectAll().where { LessonRevisions.lessonId eq lessonId }.count().toInt()
        LessonRevisions.insert {
            it[LessonRevisions.id] = newId()
            it[LessonRevisions.lessonId] = lessonId
            it[revisionNumber] = count + 1
            it[contentJson] = encodeJson(content)
            it[LessonRevisions.createdBy] = createdBy
            it[LessonRevisions.note] = note
            it[createdAt] = nowMillis()
        }
    }

    suspend fun listQuestions(subjectId: String?, topicId: String?, publishedOnly: Boolean): List<AdminQuestionRow> = tx {
        QuizQuestions.selectAll().where {
            (if (subjectId != null) QuizQuestions.subjectId eq subjectId else Op.TRUE) and
                (if (topicId != null) QuizQuestions.topicId eq topicId else Op.TRUE) and
                (if (publishedOnly) QuizQuestions.editorialStatus eq EditorialStatus.PUBLISHED.name else Op.TRUE)
        }.orderBy(QuizQuestions.id to SortOrder.ASC).map { it.toQuestionRow() }
    }

    suspend fun getQuestion(id: String): AdminQuestionRow? = tx {
        QuizQuestions.selectAll().where { QuizQuestions.id eq id }.singleOrNull()?.toQuestionRow()
    }

    suspend fun upsertQuestion(row: AdminQuestionRow) = tx {
        val exists = QuizQuestions.selectAll().where { QuizQuestions.id eq row.id }.singleOrNull() != null
        if (exists) {
            QuizQuestions.update({ QuizQuestions.id eq row.id }) {
                it[subjectId] = row.subjectId
                it[topicId] = row.topicId
                it[difficulty] = row.difficulty
                it[contentJson] = encodeJson(row.question)
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        } else {
            QuizQuestions.insert {
                it[QuizQuestions.id] = row.id
                it[QuizQuestions.userId] = null
                it[subjectId] = row.subjectId
                it[topicId] = row.topicId
                it[difficulty] = row.difficulty
                it[contentJson] = encodeJson(row.question)
                it[editorialStatus] = row.editorialStatus
                it[needsVerification] = row.needsVerification
                it[updatedAt] = row.updatedAt.toEpochMilliseconds()
            }
        }
    }

    suspend fun insertAudit(actorUserId: String?, action: String, entityType: String, entityId: String, detail: String) = tx {
        AuditLog.insert {
            it[AuditLog.id] = newId()
            it[AuditLog.actorUserId] = actorUserId
            it[AuditLog.action] = action
            it[AuditLog.entityType] = entityType
            it[AuditLog.entityId] = entityId
            it[detailJson] = detail
            it[createdAt] = nowMillis()
        }
    }

    suspend fun listAudit(limit: Int = 200): List<AuditRow> = tx {
        AuditLog.selectAll().orderBy(AuditLog.createdAt to SortOrder.DESC).limit(limit).map {
            AuditRow(
                id = it[AuditLog.id],
                actorUserId = it[AuditLog.actorUserId],
                action = it[AuditLog.action],
                entityType = it[AuditLog.entityType],
                entityId = it[AuditLog.entityId],
                detail = it[AuditLog.detailJson],
                createdAt = it[AuditLog.createdAt].toInstant(),
            )
        }
    }

    suspend fun insertReport(userId: String, questionId: String, reason: String, details: String): String = tx {
        val id = newId()
        ContentReports.insert {
            it[ContentReports.id] = id
            it[ContentReports.userId] = userId
            it[ContentReports.questionId] = questionId
            it[ContentReports.reason] = reason
            it[ContentReports.details] = details
            it[createdAt] = nowMillis()
        }
        id
    }

    suspend fun listReports(): List<ReportRow> = tx {
        ContentReports.selectAll().orderBy(ContentReports.createdAt to SortOrder.DESC).map {
            ReportRow(
                id = it[ContentReports.id],
                userId = it[ContentReports.userId],
                questionId = it[ContentReports.questionId],
                reason = it[ContentReports.reason],
                details = it[ContentReports.details],
                status = it[ContentReports.reportStatus],
                createdAt = it[ContentReports.createdAt].toInstant(),
            )
        }
    }

    suspend fun seedGlossary(terms: List<GlossaryTerm>) = tx {
        if (GlossaryTerms.selectAll().count() > 0) return@tx
        for (term in terms) {
            GlossaryTerms.insert {
                it[GlossaryTerms.id] = term.id.ifBlank { newId() }
                it[GlossaryTerms.term] = term.term
                it[GlossaryTerms.definition] = term.definition
                it[GlossaryTerms.subjectId] = term.subjectId
                it[GlossaryTerms.sourceLabel] = term.source
                it[createdAt] = nowMillis()
            }
        }
    }

    suspend fun seedSourceReferences(refs: List<Triple<String, String, String>>) = tx {
        if (SourceReferences.selectAll().count() > 0) return@tx
        for ((lessonId, title, detail) in refs) {
            SourceReferences.insert {
                it[SourceReferences.id] = newId()
                it[SourceReferences.lessonId] = lessonId
                it[SourceReferences.userId] = null
                it[SourceReferences.title] = title
                it[SourceReferences.detail] = detail
                it[createdAt] = nowMillis()
            }
        }
    }

    suspend fun deleteForUser(userId: String) = tx {
        ContentReports.deleteWhere { ContentReports.userId eq userId }
        AuditLog.deleteWhere { AuditLog.actorUserId eq userId }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toSubjectRow() = AdminSubjectRow(
        id = this[Subjects.id],
        name = this[Subjects.name],
        shortName = this[Subjects.shortName],
        description = this[Subjects.description],
        blueprintNote = this[Subjects.blueprintNote],
        colorToken = this[Subjects.colorToken],
        editorialStatus = this[Subjects.editorialStatus],
        needsVerification = this[Subjects.needsVerification],
        updatedAt = this[Subjects.updatedAt].toInstant(),
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toTopicRow() = AdminTopicRow(
        id = this[Topics.id],
        subjectId = this[Topics.subjectId],
        name = this[Topics.name],
        editorialStatus = this[Topics.editorialStatus],
        needsVerification = this[Topics.needsVerification],
        updatedAt = this[Topics.updatedAt].toInstant(),
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toLessonRow(): AdminLessonRow {
        val keywords = decodeJson<List<String>>(this[Lessons.keywordsJson])
        val content = decodeJson<Lesson>(this[Lessons.contentJson])
        return AdminLessonRow(
            id = this[Lessons.id],
            subjectId = this[Lessons.subjectId],
            topicId = this[Lessons.topicId],
            title = this[Lessons.title],
            summary = this[Lessons.summary],
            readingMinutes = this[Lessons.readingMinutes],
            difficulty = this[Lessons.difficulty],
            keywords = keywords,
            content = content,
            editorialStatus = this[Lessons.editorialStatus],
            needsVerification = this[Lessons.needsVerification],
            updatedAt = this[Lessons.updatedAt].toInstant(),
        )
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toQuestionRow(): AdminQuestionRow {
        val question = decodeJson<QuizQuestion>(this[QuizQuestions.contentJson])
        return AdminQuestionRow(
            id = this[QuizQuestions.id],
            subjectId = this[QuizQuestions.subjectId],
            topicId = this[QuizQuestions.topicId],
            difficulty = this[QuizQuestions.difficulty],
            question = question,
            editorialStatus = this[QuizQuestions.editorialStatus],
            needsVerification = this[QuizQuestions.needsVerification],
            updatedAt = this[QuizQuestions.updatedAt].toInstant(),
        )
    }
}
