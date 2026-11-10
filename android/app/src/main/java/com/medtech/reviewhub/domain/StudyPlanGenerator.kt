package com.medtech.reviewhub.domain

import com.medtech.reviewhub.domain.model.ExamTarget
import com.medtech.reviewhub.domain.model.KnowledgeLevel
import com.medtech.reviewhub.domain.model.PlanDay
import com.medtech.reviewhub.domain.model.PlanItem
import com.medtech.reviewhub.domain.model.PlanItemType
import com.medtech.reviewhub.domain.model.StudyPlan
import com.medtech.reviewhub.domain.model.Topic
import com.medtech.reviewhub.domain.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Local study-plan generator. Mirrors the backend plan generator rules:
 *  - Subject mix comes from the exam blueprint weights (ASCPi / PH / equal for CUSTOM).
 *  - Weakest subjects get 1.5x the time.
 *  - Daily budget = profile.minutesPerDay, spread over the days until the target date.
 *  - Item types rotate READ -> FLASHCARDS -> QUIZ -> REVIEW_WRONG -> FORMULAS and are
 *    biased by the user's learning preferences.
 *
 * Weights are approximations of the published blueprint outlines (see
 * shared/CONTENT_SCHEMA.md "blueprintNote"); normalized to sum to 1.
 */
object StudyPlanGenerator {

    val ASCPI_WEIGHTS: Map<String, Double> = mapOf(
        "blood-banking" to 9.0,
        "clinical-chemistry" to 17.0,
        "hematology" to 16.0,
        "microbiology" to 18.0,
        "immunology" to 10.0,
        "urinalysis" to 10.0,
        "lab-operations" to 12.0
    )

    val PH_WEIGHTS: Map<String, Double> = mapOf(
        "clinical-chemistry" to 20.0,
        "microbiology" to 20.0,
        "hematology" to 20.0,
        "blood-banking" to 15.0,
        "immunology" to 10.0,
        "urinalysis" to 5.0,
        "lab-operations" to 10.0
    )

    const val WEAK_SUBJECT_MULTIPLIER = 1.5

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun weightsFor(target: ExamTarget): Map<String, Double> = when (target) {
        ExamTarget.MLS_ASCPI -> ASCPI_WEIGHTS
        ExamTarget.PH_BOARD -> PH_WEIGHTS
        ExamTarget.CUSTOM -> ASCPI_WEIGHTS.mapValues { 1.0 }
    }

    /**
     * Minutes per subject after applying the weak-subject 1.5x rule and
     * normalizing so the total equals [totalMinutes].
     */
    fun allocation(
        target: ExamTarget,
        weakSubjects: List<String>,
        totalMinutes: Int,
        subjects: List<String>
    ): Map<String, Int> {
        val base = weightsFor(target).filterKeys { it in subjects.ifEmpty { weightsFor(target).keys.toList() } }
            .ifEmpty { weightsFor(target) }
        val adjusted = base.mapValues { (id, w) ->
            if (id in weakSubjects) w * WEAK_SUBJECT_MULTIPLIER else w
        }
        val sum = adjusted.values.sum().coerceAtLeast(1e-9)
        val raw = adjusted.mapValues { totalMinutes * it.value / sum }
        // Largest-remainder rounding so the per-subject minutes add up exactly.
        val floors = raw.mapValues { it.value.toInt() }
        var remainder = totalMinutes - floors.values.sum()
        val order = raw.entries.sortedByDescending { it.value - it.value.toInt() }
        val result = floors.toMutableMap()
        var i = 0
        while (remainder > 0 && order.isNotEmpty()) {
            val key = order[i % order.size].key
            result[key] = (result[key] ?: 0) + 1
            remainder--
            i++
        }
        return result
    }

    /**
     * Generates a plan starting tomorrow (or today when [startToday]) through
     * [profile.targetExamDate] (default 8 weeks out). Returns a plan with at
     * most [maxDays] days of items (default 28) so local storage stays small;
     * regenerate as the exam approaches.
     */
    fun generate(
        profile: UserProfile,
        subjectTopics: Map<String, List<Topic>>,
        today: LocalDate = LocalDate.now(),
        startToday: Boolean = true,
        maxDays: Int = 28
    ): StudyPlan {
        val subjects = subjectTopics.keys.toList().ifEmpty { weightsFor(profile.examTarget).keys.toList() }
        val startDate = if (startToday) today else today.plusDays(1)
        val endDate = profile.targetExamDate?.let {
            runCatching { LocalDate.parse(it, iso) }.getOrNull()
        } ?: today.plusDays(56)
        val totalDays = ((endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1).coerceIn(1, maxDays)

        val minutesPerDay = profile.minutesPerDay.coerceIn(10, 600)
        val weak = profile.weakestSubjects.filter { it in subjects }
        val dailyAllocation = allocation(profile.examTarget, weak, minutesPerDay, subjects)

        val prefs = profile.learningPreferences.map { it.lowercase() }
        val rotation = buildList {
            add(PlanItemType.READ)
            if ("flashcards" in prefs || prefs.isEmpty()) add(PlanItemType.FLASHCARDS)
            add(PlanItemType.QUIZ)
            if ("practice" in prefs || "questions" in prefs || prefs.isEmpty()) add(PlanItemType.REVIEW_WRONG)
            if ("formulas" in prefs || prefs.isEmpty()) add(PlanItemType.FORMULAS)
        }.ifEmpty { listOf(PlanItemType.READ, PlanItemType.QUIZ) }

        // Difficulty bias: beginners get more READ, advanced get more QUIZ.
        val readSlots = when (profile.knowledgeLevel) {
            KnowledgeLevel.BEGINNER -> 2
            KnowledgeLevel.INTERMEDIATE -> 1
            KnowledgeLevel.ADVANCED -> 0
        }

        val days = mutableListOf<PlanDay>()
        for (d in 0 until totalDays) {
            val date = startDate.plusDays(d.toLong())
            val items = mutableListOf<PlanItem>()
            val scheduleWindows = profile.preferredSchedule[date.dayOfWeek.name.lowercase().take(3)] ?: emptyList()
            val dayScale = if (scheduleWindows.isEmpty()) 1.0 else 1.0 // windows are informational for now
            val weakToday = subjects.filter { it in weak }.filterIndexed { i, _ -> (d + i) % 2 == 0 }.ifEmpty { subjects }
            val activeSubjects = if (weak.isEmpty()) subjects else weakToday

            var slot = 0
            for (subjectId in activeSubjects) {
                val minutes = ((dailyAllocation[subjectId] ?: (minutesPerDay / activeSubjects.size)) * dayScale).toInt().coerceAtLeast(10)
                val topics = subjectTopics[subjectId].orEmpty()
                val topic = topics.getOrNull((d + slot) % (topics.size.coerceAtLeast(1)))
                val type = when {
                    slot < readSlots -> PlanItemType.READ
                    else -> rotation[slot % rotation.size]
                }
                items += PlanItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    subjectId = subjectId,
                    topicId = topic?.id,
                    lessonId = topic?.lessonIds?.firstOrNull(),
                    targetMinutes = (minutes / 2).coerceAtLeast(10),
                    completed = false
                )
                slot++
            }
            // Guarantee at least 2 items/day so the dashboard checklist feels alive.
            if (items.size < 2) {
                val extraType = rotation[d % rotation.size]
                items += PlanItem(
                    id = UUID.randomUUID().toString(),
                    type = extraType,
                    subjectId = subjects.firstOrNull(),
                    topicId = null,
                    lessonId = null,
                    targetMinutes = (minutesPerDay / 3).coerceAtLeast(10),
                    completed = false
                )
            }
            days += PlanDay(date = date.format(iso), items = items)
        }
        return StudyPlan(
            id = UUID.randomUUID().toString(),
            generatedAt = today.atStartOfDay().toString(),
            days = days
        )
    }

    fun todayItems(plan: StudyPlan, today: LocalDate = LocalDate.now()): List<PlanItem> =
        plan.days.firstOrNull { it.date == today.format(iso) }?.items ?: emptyList()
}
