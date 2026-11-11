package io.medtech.backend.service

import io.medtech.backend.domain.ExamTarget
import io.medtech.backend.domain.PlanDay
import io.medtech.backend.domain.PlanItem
import io.medtech.backend.domain.PlanItemType
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.SubjectDto
import io.medtech.backend.db.newId
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Builds a 4-week daily study plan from the user profile.
 *
 * Subject time allocation uses ASCP BOC MLS content-outline style weights
 * ("approximate content outline weights, not official percentages"). The PH_BOARD
 * profile uses a separate PH MTLE oriented allocation. Weakest subjects get 1.5x time.
 */
object PlanGenerator {

    data class GeneratedPlan(val days: List<PlanDay>, val weightsJson: String, val label: String)

    private val ASCPI_WEIGHTS = mapOf(
        "blood-banking" to 0.08,
        "clinical-chemistry" to 0.16,
        "hematology" to 0.15,
        "microbiology" to 0.15,
        "parasitology" to 0.08,
        "immunology" to 0.09,
        "urinalysis" to 0.09,
        "lab-operations" to 0.12,
    )

    private val PH_BOARD_WEIGHTS = mapOf(
        "blood-banking" to 0.09,
        "clinical-chemistry" to 0.16,
        "hematology" to 0.14,
        "microbiology" to 0.14,
        "parasitology" to 0.11,
        "immunology" to 0.09,
        "urinalysis" to 0.12,
        "lab-operations" to 0.15,
    )

    private val SUBJECT_ORDER = listOf(
        "blood-banking", "clinical-chemistry", "hematology", "microbiology",
        "parasitology", "immunology", "urinalysis", "lab-operations",
    )

    private val ITEM_CYCLE = listOf(
        PlanItemType.READ, PlanItemType.FLASHCARDS, PlanItemType.QUIZ,
        PlanItemType.REVIEW_WRONG, PlanItemType.FORMULAS,
    )

    fun generate(profile: Profile, subjects: List<SubjectDto>, today: LocalDate = currentDay()): GeneratedPlan {
        val baseWeights = when (profile.examTarget) {
            ExamTarget.MLS_ASCPI -> ASCPI_WEIGHTS
            ExamTarget.PH_BOARD -> PH_BOARD_WEIGHTS
            ExamTarget.CUSTOM -> SUBJECT_ORDER.associateWith { 1.0 / SUBJECT_ORDER.size }
        }
        val label = when (profile.examTarget) {
            ExamTarget.MLS_ASCPI -> "ASCPi MLS plan (approximate content outline weights, not official percentages)"
            ExamTarget.PH_BOARD -> "PH MTLE plan (approximate subject allocation, not official percentages)"
            ExamTarget.CUSTOM -> "Custom balanced plan (approximate allocation, not official percentages)"
        }

        val weak = profile.weakestSubjects.toSet()
        val adjusted = baseWeights.mapValues { (subject, weight) -> if (subject in weak) weight * 1.5 else weight }
        val sum = adjusted.values.sum().coerceAtLeast(0.0001)

        // Decide plan length: 4 weeks, or until the exam date if earlier.
        val examDate = profile.targetExamDate
        val defaultEnd = today.plus(27, DateTimeUnit.DAY)
        val end = if (examDate != null && examDate < defaultEnd && examDate > today) examDate else defaultEnd
        val dates = generateSequence(today) { prev -> if (prev < end) prev.plus(1, DateTimeUnit.DAY) else null }.toList()

        // Subject rotation: subjects repeated proportionally to weight, weak ones first.
        val rotation = buildRotation(adjusted, sum)
        var rotationIndex = 0
        var typeIndex = 0

        val topicCursor = mutableMapOf<String, Int>()
        val days = dates.map { date ->
            val windows = scheduleWindowsFor(profile, date)
            val dayMinutes = when {
                profile.preferredSchedule.isEmpty() -> profile.minutesPerDay
                windows.isEmpty() -> 0 // explicit rest day
                else -> minOf(profile.minutesPerDay, windows.sumOf { it[1] - it[0] })
            }
            if (dayMinutes <= 0) {
                PlanDay(date = date, items = emptyList())
            } else {
                val itemCount = (dayMinutes / 30).coerceIn(1, 4)
                val perItem = maxOf(10, ((dayMinutes / itemCount) / 5) * 5)
                val items = (0 until itemCount).map {
                    val subjectId = rotation[rotationIndex % rotation.size]
                    rotationIndex++
                    val type = ITEM_CYCLE[typeIndex % ITEM_CYCLE.size]
                    typeIndex++
                    val subjectTopics = subjects.find { it.id == subjectId }?.topics.orEmpty()
                    val topic = if (subjectTopics.isEmpty()) null else {
                        val cursor = topicCursor[subjectId] ?: 0
                        topicCursor[subjectId] = cursor + 1
                        subjectTopics[cursor % subjectTopics.size]
                    }
                    PlanItem(
                        id = newId(),
                        type = type,
                        subjectId = subjectId,
                        topicId = topic?.id,
                        lessonId = topic?.lessonIds?.firstOrNull(),
                        targetMinutes = perItem,
                        completed = false,
                    )
                }
                PlanDay(date = date, items = items)
            }
        }

        val weightsJson = encodeWeights(adjusted.mapValues { it.value / sum })
        return GeneratedPlan(days = days, weightsJson = weightsJson, label = label)
    }

    private fun buildRotation(adjusted: Map<String, Double>, sum: Double): List<String> {
        val rotation = mutableListOf<String>()
        for ((subject, weight) in adjusted.entries.sortedByDescending { it.value }) {
            val slots = maxOf(1, Math.round(weight / sum * 28).toInt())
            repeat(slots) { rotation += subject }
        }
        return rotation.ifEmpty { SUBJECT_ORDER }
    }

    private fun scheduleWindowsFor(profile: Profile, date: LocalDate): List<List<Int>> {
        if (profile.preferredSchedule.isEmpty()) return listOf(listOf(0, 24 * 60))
        val key = date.dayOfWeek.name.lowercase().take(3) // mon, tue, ...
        return profile.preferredSchedule[key].orEmpty()
    }

    private fun encodeWeights(weights: Map<String, Double>): String =
        weights.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":${"%.4f".format(v)}"
        }

    private fun currentDay(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}
