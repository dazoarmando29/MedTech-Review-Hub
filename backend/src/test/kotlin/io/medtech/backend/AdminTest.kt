package io.medtech.backend

import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.promoteToAdmin
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import io.medtech.backend.service.AdminLessonUpsert
import io.medtech.backend.service.AdminLessonView
import io.medtech.backend.service.AdminSubjectUpsert
import io.medtech.backend.service.AdminSubjectView
import io.medtech.backend.service.AuditView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminTest {

    @Test
    fun `admin endpoints reject non-admin with 403`() = withApp {
        val user = register("pleb@example.com")
        val getAudit = client.get("/api/v1/admin/audit") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, getAudit.status)
        assertTrue(getAudit.text().contains("FORBIDDEN"), getAudit.text())

        val createSubject = client.post("/api/v1/admin/subjects") {
            bearer(user.accessToken)
            jsonBody(encodeJson(AdminSubjectUpsert(id = "x", name = "Nope")))
        }
        assertEquals(HttpStatusCode.Forbidden, createSubject.status)

        val reports = client.get("/api/v1/admin/reports") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, reports.status)

        val publish = client.post("/api/v1/admin/lessons/whatever/publish") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, publish.status)

        // Anonymous access is rejected as well.
        val anon = client.get("/api/v1/admin/audit")
        assertEquals(HttpStatusCode.Unauthorized, anon.status)
    }

    @Test
    fun `admin can manage editorial content with enforced transitions`() = withApp {
        val admin = register("admin@example.com")
        promoteToAdmin(admin.user.id)

        // Create subject (DRAFT).
        val subjectResp = client.post("/api/v1/admin/subjects") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(AdminSubjectUpsert(id = "editorial-subject", name = "Editorial Subject")))
        }
        assertEquals(HttpStatusCode.Created, subjectResp.status)
        val subject: AdminSubjectView = decodeJson(subjectResp.text())
        assertEquals("DRAFT", subject.editorialStatus)

        // Create a lesson as DRAFT with needsVerification (AI-style content).
        val lessonContent = Lesson(
            id = "editorial-lesson",
            subjectId = "editorial-subject",
            topicId = "editorial-topic",
            title = "Editorial Lesson",
            summary = "Draft summary",
        )
        val lessonResp = client.post("/api/v1/admin/lessons") {
            bearer(admin.accessToken)
            jsonBody(
                encodeJson(
                    AdminLessonUpsert(
                        id = "editorial-lesson",
                        subjectId = "editorial-subject",
                        topicId = "editorial-topic",
                        title = "Editorial Lesson",
                        content = lessonContent,
                        needsVerification = true,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, lessonResp.status)
        val lesson: AdminLessonView = decodeJson(lessonResp.text())
        assertEquals("DRAFT", lesson.editorialStatus)
        assertTrue(lesson.needsVerification)

        // DRAFT is not visible in public content.
        val publicBefore = client.get("/api/v1/content/lessons")
        assertTrue(!publicBefore.bodyAsText().contains("editorial-lesson"), "DRAFT lesson must not be public")

        // Invalid transition DRAFT -> PUBLISHED via PATCH is rejected.
        val badTransition = client.patch("/api/v1/admin/lessons/editorial-lesson") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(mapOf("editorialStatus" to "PUBLISHED")))
        }
        assertEquals(HttpStatusCode.BadRequest, badTransition.status)
        assertTrue(badTransition.text().contains("not allowed"), badTransition.text())

        // DRAFT -> IN_REVIEW is allowed.
        val inReview = client.patch("/api/v1/admin/lessons/editorial-lesson") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(mapOf("editorialStatus" to "IN_REVIEW")))
        }
        assertEquals(HttpStatusCode.OK, inReview.status)

        // Publish (IN_REVIEW -> PUBLISHED) creates a revision and exposes the lesson.
        val published = client.post("/api/v1/admin/lessons/editorial-lesson/publish") { bearer(admin.accessToken) }
        assertEquals(HttpStatusCode.OK, published.status)
        val publishedLesson: AdminLessonView = decodeJson(published.text())
        assertEquals("PUBLISHED", publishedLesson.editorialStatus)

        val publicAfter = client.get("/api/v1/content/lessons")
        assertTrue(publicAfter.bodyAsText().contains("editorial-lesson"), "PUBLISHED lesson must be public")

        // PUBLISHED -> ARCHIVED allowed; further transitions rejected.
        val archived = client.patch("/api/v1/admin/lessons/editorial-lesson") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(mapOf("editorialStatus" to "ARCHIVED")))
        }
        assertEquals(HttpStatusCode.OK, archived.status)
        val afterArchive = client.patch("/api/v1/admin/lessons/editorial-lesson") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(mapOf("editorialStatus" to "DRAFT")))
        }
        assertEquals(HttpStatusCode.BadRequest, afterArchive.status)

        // Audit trail records every mutation.
        val auditResp = client.get("/api/v1/admin/audit") { bearer(admin.accessToken) }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val audit: List<AuditView> = decodeJson(auditResp.text())
        assertTrue(audit.any { it.action == "CREATE" && it.entityType == "lesson" && it.entityId == "editorial-lesson" })
        assertTrue(audit.any { it.action == "PUBLISH" && it.entityId == "editorial-lesson" })
        assertTrue(audit.all { it.actorUserId == admin.user.id }, "audit entries must record the actor")

        // Content reports queue works for user reports.
        val report = client.post("/api/v1/questions/bb-q-001/report") {
            bearer(admin.accessToken)
            jsonBody(encodeJson(mapOf("reason" to "INCORRECT_ANSWER", "details" to "Choice B looks right to me")))
        }
        assertEquals(HttpStatusCode.Created, report.status)
        val reportsResp = client.get("/api/v1/admin/reports") { bearer(admin.accessToken) }
        assertEquals(HttpStatusCode.OK, reportsResp.status)
        assertTrue(reportsResp.bodyAsText().contains("bb-q-001"))
    }
}
