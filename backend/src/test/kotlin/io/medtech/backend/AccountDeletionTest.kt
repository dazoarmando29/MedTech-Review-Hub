package io.medtech.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.login
import io.medtech.backend.TestApi.multipartPdf
import io.medtech.backend.TestApi.pdfBytes
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.db.AuditLog
import io.medtech.backend.db.DiaryEntries
import io.medtech.backend.db.FlashcardDecks
import io.medtech.backend.db.FlashcardReviews
import io.medtech.backend.db.Flashcards
import io.medtech.backend.db.FocusSessions
import io.medtech.backend.db.GeneratedAiContent
import io.medtech.backend.db.HabitLogs
import io.medtech.backend.db.Habits
import io.medtech.backend.db.LearningAnalytics
import io.medtech.backend.db.MascotPreferences
import io.medtech.backend.db.NotificationPreferences
import io.medtech.backend.db.PdfAnnotations
import io.medtech.backend.db.PdfDocuments
import io.medtech.backend.db.Profiles
import io.medtech.backend.db.QuizAnswers
import io.medtech.backend.db.QuizAttempts
import io.medtech.backend.db.RefreshTokens
import io.medtech.backend.db.StudyPlanItems
import io.medtech.backend.db.StudyPlans
import io.medtech.backend.db.StudySessions
import io.medtech.backend.db.Users
import io.medtech.backend.db.tx
import io.medtech.backend.domain.DiaryCreateRequest
import io.medtech.backend.domain.FlashcardCreateRequest
import io.medtech.backend.domain.HabitCreateRequest
import io.medtech.backend.domain.HabitKind
import io.medtech.backend.domain.HabitLogRequest
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.QuizStartRequest
import io.medtech.backend.domain.SessionCreateRequest
import io.medtech.backend.domain.SessionKind
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDeletionTest {

    @Test
    fun `account deletion removes every row for the user`() = withApp {
        val user = register("goner@example.com")
        val token = user.accessToken
        val userId = user.user.id

        // Populate every private table that the API can touch.
        client.post("/api/v1/flashcards") {
            bearer(token)
            jsonBody(encodeJson(FlashcardCreateRequest(front = "f", back = "b")))
        }
        val habitResp = client.post("/api/v1/habits") {
            bearer(token)
            jsonBody(encodeJson(HabitCreateRequest(name = "Study", targetPerDay = 30.0, kind = HabitKind.MINUTES)))
        }
        val habit: io.medtech.backend.domain.Habit = decodeJson(habitResp.bodyAsText())
        val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        client.post("/api/v1/habits/log") {
            bearer(token)
            jsonBody(encodeJson(HabitLogRequest(habitId = habit.id, date = today, amount = 30.0)))
        }
        client.post("/api/v1/diary") {
            bearer(token)
            jsonBody(encodeJson(DiaryCreateRequest(date = today, mood = "OK", text = "bye", tags = listOf("t"))))
        }
        val plan = client.get("/api/v1/plan") { bearer(token) }
        assertEquals(HttpStatusCode.OK, plan.status, "plan generation must work")
        client.post("/api/v1/sessions") {
            bearer(token)
            jsonBody(
                encodeJson(
                    SessionCreateRequest(
                        startedAt = Clock.System.now(),
                        endedAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + 30 * 60 * 1000L),
                        kind = SessionKind.FOCUS,
                        subjectId = "hematology",
                        itemsCompleted = 1,
                    ),
                ),
            )
        }
        val quiz = client.post("/api/v1/quizzes/start") {
            bearer(token)
            jsonBody(encodeJson(QuizStartRequest(questionIds = listOf("bb-q-001"))))
        }
        val attempt: io.medtech.backend.domain.QuizAttemptView = decodeJson(quiz.bodyAsText())
        client.post("/api/v1/quizzes/${attempt.id}/answer") {
            bearer(token)
            jsonBody(encodeJson(io.medtech.backend.domain.QuizAnswerRequest(questionId = "bb-q-001", choiceId = "C")))
        }
        client.post("/api/v1/quizzes/${attempt.id}/finish") { bearer(token) }
        val upload = client.post("/api/v1/pdfs") {
            bearer(token)
            setBody(multipartPdf("doc.pdf", pdfBytes()))
        }
        val pdf: io.medtech.backend.domain.PdfDocumentView = decodeJson(upload.bodyAsText())
        client.put("/api/v1/pdfs/${pdf.id}/annotations") {
            bearer(token)
            jsonBody(
                encodeJson(
                    io.medtech.backend.domain.AnnotationReplaceRequest(
                        annotations = listOf(
                            io.medtech.backend.domain.AnnotationInput(page = 1, selectedText = "x"),
                        ),
                    ),
                ),
            )
        }
        client.post("/api/v1/questions/bb-q-002/report") {
            bearer(token)
            jsonBody(encodeJson(mapOf("reason" to "OTHER", "details" to "test")))
        }
        client.put("/api/v1/me/profile") {
            bearer(token)
            jsonBody(encodeJson(Profile(minutesPerDay = 90)))
        }

        // Sanity: rows exist before deletion.
        val before = countRowsForUser(userId)
        assertTrue(before["users"] == 1L, "user row exists")
        assertTrue((before["flashcards"] ?: 0) >= 1, "flashcard row exists")
        assertTrue((before["diary_entries"] ?: 0) >= 1, "diary row exists")
        assertTrue((before["pdf_documents"] ?: 0) >= 1, "pdf row exists")
        assertTrue((before["quiz_attempts"] ?: 0) >= 1, "quiz attempt row exists")
        assertTrue((before["study_sessions"] ?: 0) >= 1, "session row exists")
        assertTrue((before["habit_logs"] ?: 0) >= 1, "habit log row exists")

        // Delete the account.
        val delete = client.delete("/api/v1/auth/me") { bearer(token) }
        assertEquals(HttpStatusCode.OK, delete.status)

        val after = countRowsForUser(userId)
        for ((table, count) in after) {
            assertEquals(0L, count, "table $table must contain no rows for deleted user")
        }

        // Login fails after deletion.
        val loginAfter = client.post("/api/v1/auth/login") {
            jsonBody(encodeJson(mapOf("email" to "goner@example.com", "password" to TestApi.GOOD_PASSWORD)))
        }
        assertEquals(HttpStatusCode.Unauthorized, loginAfter.status)
    }

    private suspend fun countRowsForUser(userId: String): Map<String, Long> = tx {
        mapOf(
            "users" to Users.selectAll().where { Users.id eq userId }.count(),
            "refresh_tokens" to RefreshTokens.selectAll().where { RefreshTokens.userId eq userId }.count(),
            "profiles" to Profiles.selectAll().where { Profiles.userId eq userId }.count(),
            "study_plans" to StudyPlans.selectAll().where { StudyPlans.userId eq userId }.count(),
            "study_plan_items" to StudyPlanItems.selectAll().where { StudyPlanItems.userId eq userId }.count(),
            "study_sessions" to StudySessions.selectAll().where { StudySessions.userId eq userId }.count(),
            "pdf_documents" to PdfDocuments.selectAll().where { PdfDocuments.userId eq userId }.count(),
            "pdf_annotations" to PdfAnnotations.selectAll().where { PdfAnnotations.userId eq userId }.count(),
            "flashcard_decks" to FlashcardDecks.selectAll().where { FlashcardDecks.userId eq userId }.count(),
            "flashcards" to Flashcards.selectAll().where { Flashcards.userId eq userId }.count(),
            "flashcard_reviews" to FlashcardReviews.selectAll().where { FlashcardReviews.userId eq userId }.count(),
            "quiz_attempts" to QuizAttempts.selectAll().where { QuizAttempts.userId eq userId }.count(),
            "quiz_answers" to QuizAnswers.selectAll().where { QuizAnswers.userId eq userId }.count(),
            "learning_analytics" to LearningAnalytics.selectAll().where { LearningAnalytics.userId eq userId }.count(),
            "habits" to Habits.selectAll().where { Habits.userId eq userId }.count(),
            "habit_logs" to HabitLogs.selectAll().where { HabitLogs.userId eq userId }.count(),
            "diary_entries" to DiaryEntries.selectAll().where { DiaryEntries.userId eq userId }.count(),
            "mascot_preferences" to MascotPreferences.selectAll().where { MascotPreferences.userId eq userId }.count(),
            "focus_sessions" to FocusSessions.selectAll().where { FocusSessions.userId eq userId }.count(),
            "notification_preferences" to NotificationPreferences.selectAll().where { NotificationPreferences.userId eq userId }.count(),
            "generated_ai_content" to GeneratedAiContent.selectAll().where { GeneratedAiContent.userId eq userId }.count(),
            "content_reports" to io.medtech.backend.db.ContentReports.selectAll().where { io.medtech.backend.db.ContentReports.userId eq userId }.count(),
            "audit_log" to AuditLog.selectAll().where { AuditLog.actorUserId eq userId }.count(),
        )
    }
}
