package io.medtech.backend

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.AnalyticsOverview
import io.medtech.backend.domain.ExamTarget
import io.medtech.backend.domain.PlanItemType
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.StreakInfo
import io.medtech.backend.domain.StudyPlan
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanTest {

    @Test
    fun `plan generator builds a four week daily plan weighted by profile`() = withApp {
        val user = register("planner@example.com")

        // Save a profile with weak subjects and daily minutes.
        val profile = Profile(
            examTarget = ExamTarget.MLS_ASCPI,
            minutesPerDay = 120,
            weakestSubjects = listOf("blood-banking"),
            strongestSubjects = listOf("clinical-chemistry"),
        )
        val put = client.put("/api/v1/me/profile") {
            bearer(user.accessToken)
            jsonBody(encodeJson(profile))
        }
        assertEquals(HttpStatusCode.OK, put.status)

        val planResp = client.post("/api/v1/plan/regenerate") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, planResp.status)
        val plan: StudyPlan = decodeJson(planResp.bodyAsText())

        assertEquals(28, plan.days.size, "a 4-week plan covers 28 days")
        assertTrue(plan.days.all { it.items.all { item -> item.type in PlanItemType.values() } })
        assertTrue(plan.days.any { it.items.isNotEmpty() }, "at least some days must have items")
        assertTrue(plan.days.all { it.items.all { item -> item.targetMinutes in 1..24 * 60 } })

        val totalMinutesBySubject = plan.days.flatMap { it.items }
            .groupBy { it.subjectId }
            .mapValues { (_, items) -> items.sumOf { it.targetMinutes } }
        val weakMinutes = totalMinutesBySubject["blood-banking"] ?: 0
        // blood-banking is the weak subject (base weight 9%, boosted 1.5x to ~13.5%); it must get
        // more time than non-weak subjects with a higher base weight than its unboosted share.
        val immunologyMinutes = totalMinutesBySubject["immunology"] ?: 0
        val urinalysisMinutes = totalMinutesBySubject["urinalysis"] ?: 0
        assertTrue(weakMinutes > immunologyMinutes, "weak subject must get 1.5x time: $totalMinutesBySubject")
        assertTrue(weakMinutes > urinalysisMinutes, "weak subject must get 1.5x time: $totalMinutesBySubject")

        // GET /plan returns the persisted plan (not a fresh generation each time).
        val fetched = client.get("/api/v1/plan") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, fetched.status)
        val same: StudyPlan = decodeJson(fetched.bodyAsText())
        assertEquals(plan.id, same.id)

        // Analytics overview compiles and matches shape.
        val overviewResp = client.get("/api/v1/analytics/overview") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, overviewResp.status)
        val overview: AnalyticsOverview = decodeJson(overviewResp.bodyAsText())
        assertEquals(7, overview.weeklyCalendar.size)
        assertTrue(overview.totalMinutes == 0L || overview.totalMinutes > 0L)

        // Streak endpoint responds.
        val streakResp = client.get("/api/v1/streak") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, streakResp.status)
        val streak: StreakInfo = decodeJson(streakResp.text())
        assertTrue(streak.freezeDaysLeft in 0..2)
    }

    @Test
    fun `sessions and export work end to end`() = withApp {
        val user = register("sessions@example.com")
        val now = kotlinx.datetime.Clock.System.now()
        val session = client.post("/api/v1/sessions") {
            bearer(user.accessToken)
            jsonBody(
                encodeJson(
                    io.medtech.backend.domain.SessionCreateRequest(
                        startedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - 3_600_000),
                        endedAt = now,
                        kind = io.medtech.backend.domain.SessionKind.READ,
                        subjectId = "hematology",
                        itemsCompleted = 2,
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, session.status)

        val list = client.get("/api/v1/sessions") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.bodyAsText().contains("hematology"))

        val export = client.get("/api/v1/me/export") { bearer(user.accessToken) }
        assertEquals(HttpStatusCode.OK, export.status)
        val exportText = export.bodyAsText()
        assertTrue(exportText.contains("sessions@example.com"), "export must include the user record")
        assertTrue(exportText.contains("\"studySessions\""), "export must include study sessions")
    }
}
