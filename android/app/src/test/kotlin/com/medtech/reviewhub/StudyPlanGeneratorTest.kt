package com.medtech.reviewhub

import com.medtech.reviewhub.domain.StudyPlanGenerator
import com.medtech.reviewhub.domain.model.ExamTarget
import com.medtech.reviewhub.domain.model.Topic
import com.medtech.reviewhub.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanGeneratorTest {

    private val topics = mapOf(
        "blood-banking" to listOf(Topic("abo-system", "ABO System", listOf("blood-banking/abo-basics"))),
        "hematology" to listOf(Topic("cbc", "CBC", listOf("hematology/cbc-indices"))),
        "microbiology" to listOf(Topic("gram-stain", "Gram Stain", listOf("microbiology/gram-stain")))
    )

    @Test
    fun `weak subjects get 1_5x time`() {
        val total = 100
        val withWeak = StudyPlanGenerator.allocation(ExamTarget.MLS_ASCPI, listOf("blood-banking"), total, topics.keys.toList())
        val withoutWeak = StudyPlanGenerator.allocation(ExamTarget.MLS_ASCPI, emptyList(), total, topics.keys.toList())
        assertTrue(withWeak.getValue("blood-banking") > withoutWeak.getValue("blood-banking"))
    }

    @Test
    fun `allocation sums to total minutes`() {
        val allocation = StudyPlanGenerator.allocation(ExamTarget.MLS_ASCPI, listOf("hematology"), 120, topics.keys.toList())
        assertEquals(120, allocation.values.sum())
    }

    @Test
    fun `ASCPi documented weights sum to 92 and allocation normalizes to total`() {
        // shared contract: approx weights bb 9, cc 17, hem 16, micro 18, imm 10, ua 10, ops 12
        val sum = StudyPlanGenerator.ASCPI_WEIGHTS.values.sum()
        assertEquals(92.0, sum, 0.0001)
        val allocation = StudyPlanGenerator.allocation(ExamTarget.MLS_ASCPI, emptyList(), 100, emptyList())
        assertEquals(100, allocation.values.sum())
    }

    @Test
    fun `generate produces days until target date`() {
        val profile = UserProfile(
            examTarget = ExamTarget.MLS_ASCPI,
            targetExamDate = "2026-02-28",
            minutesPerDay = 60,
            weakestSubjects = listOf("blood-banking"),
            learningPreferences = listOf("reading", "flashcards"),
            onboardingCompleted = true
        )
        val plan = StudyPlanGenerator.generate(profile, topics, today = java.time.LocalDate.parse("2026-02-20"))
        assertEquals(9, plan.days.size) // Feb 20..28 inclusive
        assertTrue(plan.days.all { it.items.isNotEmpty() })
        assertTrue(plan.days.first().items.any { it.targetMinutes > 0 })
    }

    @Test
    fun `plan items reference subjects and carry target minutes`() {
        val profile = UserProfile(minutesPerDay = 90, targetExamDate = "2026-03-01")
        val plan = StudyPlanGenerator.generate(profile, topics, today = java.time.LocalDate.parse("2026-02-25"))
        plan.days.forEach { day ->
            day.items.forEach { item ->
                assertTrue(item.targetMinutes >= 10)
                assertTrue(item.subjectId == null || item.subjectId in topics.keys)
            }
        }
    }

    @Test
    fun `todayItems returns items for today`() {
        val profile = UserProfile(minutesPerDay = 60, targetExamDate = "2026-03-01")
        val today = java.time.LocalDate.parse("2026-02-25")
        val plan = StudyPlanGenerator.generate(profile, topics, today = today)
        val items = StudyPlanGenerator.todayItems(plan, today)
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun `PH profile uses its own weights`() {
        val ph = StudyPlanGenerator.weightsFor(ExamTarget.PH_BOARD)
        val ascpi = StudyPlanGenerator.weightsFor(ExamTarget.MLS_ASCPI)
        assertEquals(ph.getValue("clinical-chemistry"), 16.0, 0.0001)
        assertTrue(ph.getValue("urinalysis") != ascpi.getValue("urinalysis"))
        assertTrue(ph.getValue("parasitology") > ascpi.getValue("parasitology"))
    }
}
