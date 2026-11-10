package com.medtech.reviewhub.data.repo

import com.medtech.reviewhub.data.db.PlanEntity
import com.medtech.reviewhub.data.db.StudyDao
import com.medtech.reviewhub.data.db.StudySessionEntity
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.domain.StudyPlanGenerator
import com.medtech.reviewhub.domain.model.PlanDay
import com.medtech.reviewhub.domain.model.PlanItem
import com.medtech.reviewhub.domain.model.StudyPlan
import com.medtech.reviewhub.domain.model.Topic
import com.medtech.reviewhub.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Study plan + study sessions + dashboard analytics. Everything is computed
 * locally from Room data (offline-first).
 */
class StudyRepository(
    private val studyDao: StudyDao,
    private val settings: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- Plan -----------------------------------------------------------

    suspend fun plan(): StudyPlan? = studyDao.plan()?.let { decode(it) }

    suspend fun planOrGenerate(subjectTopics: Map<String, List<Topic>>, profile: UserProfile? = null): StudyPlan {
        plan()?.let { return it }
        val p = profile ?: settings.currentProfile()
        val generated = StudyPlanGenerator.generate(p, subjectTopics)
        savePlan(generated)
        return generated
    }

    suspend fun regenerate(subjectTopics: Map<String, List<Topic>>, profile: UserProfile? = null): StudyPlan {
        val p = profile ?: settings.currentProfile()
        val generated = StudyPlanGenerator.generate(p, subjectTopics)
        savePlan(generated)
        return generated
    }

    suspend fun savePlan(plan: StudyPlan) {
        studyDao.savePlan(
            PlanEntity(
                id = plan.id,
                generatedAt = plan.generatedAt,
                daysJson = json.encodeToString(PlanDayListHolder.serializer, plan.days)
            )
        )
    }

    suspend fun setItemCompleted(dayDate: String, itemId: String, completed: Boolean) {
        val plan = plan() ?: return
        val days = plan.days.map { day ->
            if (day.date != dayDate) day
            else day.copy(items = day.items.map { if (it.id == itemId) it.copy(completed = completed) else it })
        }
        savePlan(plan.copy(days = days))
    }

    suspend fun updateItem(dayDate: String, item: PlanItem) {
        val plan = plan() ?: return
        val days = plan.days.map { day ->
            if (day.date != dayDate) day
            else day.copy(items = day.items.map { if (it.id == item.id) item else it })
        }
        savePlan(plan.copy(days = days))
    }

    suspend fun todayItems(date: LocalDate = LocalDate.now()): List<PlanItem> {
        val plan = plan() ?: return emptyList()
        return StudyPlanGenerator.todayItems(plan, date)
    }

    private fun decode(entity: PlanEntity): StudyPlan = StudyPlan(
        id = entity.id,
        generatedAt = entity.generatedAt,
        days = runCatching {
            json.decodeFromString(PlanDayListHolder.serializer, entity.daysJson)
        }.getOrDefault(emptyList())
    )

    // ---- Sessions -------------------------------------------------------

    suspend fun recordSession(kind: String, subjectId: String?, startedAtMs: Long, endedAtMs: Long,
                              itemsCompleted: Int = 0) {
        val minutes = ((endedAtMs - startedAtMs) / 60_000L).toInt().coerceAtLeast(1)
        studyDao.insertSession(
            StudySessionEntity(
                id = UUID.randomUUID().toString(),
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                kind = kind,
                subjectId = subjectId,
                minutes = minutes,
                itemsCompleted = itemsCompleted
            )
        )
    }

    suspend fun sessionsBetween(fromMs: Long, toMs: Long) = studyDao.sessionsBetween(fromMs, toMs)
    suspend fun totalMinutes() = studyDao.totalMinutes()
    suspend fun minutesBetween(fromMs: Long, toMs: Long) = studyDao.minutesBetween(fromMs, toMs)

    /**
     * Streak = consecutive days (ending today or yesterday) with any recorded
     * study activity. A single missed day is NOT shamed: the UI shows
     * "Rest day recorded. Recovery is part of the plan." (shared/DESIGN.md).
     */
    suspend fun streakDays(today: LocalDate = LocalDate.now()): Int {
        val days = studyDao.activeDayStrings(400).toHashSet()
        var streak = 0
        var cursor = today
        if (cursor.toString() !in days) cursor = cursor.minusDays(1)
        while (cursor.toString() in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun observeSessions(): Flow<List<StudySessionEntity>> = kotlinx.coroutines.flow.flow {
        emit(studyDao.recentSessions(50))
    }

    // ---- Reading progress (lessons) --------------------------------------

    suspend fun saveLessonProgress(lessonId: String, percent: Float, offset: Int) {
        studyDao.upsertProgress(
            com.medtech.reviewhub.data.db.ReadingProgressEntity(
                key = "lesson:$lessonId",
                progressPercent = percent.coerceIn(0f, 1f),
                lastOffset = offset,
                bookmarked = false,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun lessonProgress(lessonId: String): com.medtech.reviewhub.data.db.ReadingProgressEntity? =
        studyDao.progress("lesson:$lessonId")

    suspend fun setLessonBookmarked(lessonId: String, bookmarked: Boolean) {
        val existing = studyDao.progress("lesson:$lessonId")
        studyDao.upsertProgress(
            com.medtech.reviewhub.data.db.ReadingProgressEntity(
                key = "lesson:$lessonId",
                progressPercent = existing?.progressPercent ?: 0f,
                lastOffset = existing?.lastOffset ?: 0,
                bookmarked = bookmarked,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun bookmarkedLessonIds(): Set<String> =
        studyDao.allProgress().filter { it.key.startsWith("lesson:") && it.bookmarked }
            .map { it.key.removePrefix("lesson:") }.toSet()

    suspend fun lastLessonWithProgress(): String? =
        studyDao.allProgress().filter { it.key.startsWith("lesson:") && it.progressPercent in 0.05f..0.95f }
            .maxByOrNull { it.updatedAtMs }?.key?.removePrefix("lesson:")

    // ---- Dashboard (local AnalyticsOverview per API.md §4) --------------

    data class DashboardStats(
        val streakDays: Int,
        val totalMinutes: Int,
        val minutesToday: Int,
        val flashcardsDueToday: Int,
        val questionsAnsweredToday: Int,
        val recentQuizAccuracy: Double?,
        val weakestSubjectId: String?,
        val examCountdownDays: Int?,
        val weeklyMinutes: List<Pair<String, Int>>
    )
}

private object PlanDayListHolder {
    val serializer = kotlinx.serialization.builtins.ListSerializer(PlanDay.serializer())
}
