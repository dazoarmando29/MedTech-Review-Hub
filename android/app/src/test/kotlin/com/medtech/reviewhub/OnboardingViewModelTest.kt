package com.medtech.reviewhub

import com.medtech.reviewhub.domain.model.ExamTarget
import com.medtech.reviewhub.domain.model.ExplanationStyle
import com.medtech.reviewhub.domain.model.KnowledgeLevel
import com.medtech.reviewhub.domain.model.StudyPlan
import com.medtech.reviewhub.domain.model.Topic
import com.medtech.reviewhub.domain.model.UserProfile
import com.medtech.reviewhub.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compose-less ViewModel test: the OnboardingViewModel is a plain state machine
 * (no Android or Compose dependencies), so its step navigation, validation and
 * plan generation are testable with plain JUnit.
 */
class OnboardingViewModelTest {

    private val topics = mapOf(
        "blood-banking" to listOf(Topic("abo-system", "ABO", listOf("blood-banking/abo-basics"))),
        "hematology" to listOf(Topic("cbc", "CBC", listOf("hematology/cbc-indices")))
    )

    private fun vm() = OnboardingViewModel(topicProvider = { topics })

    @Test
    fun `walks through all steps in order`() {
        val vm = vm()
        // Preset a subject so the SUBJECTS step validates when leaving it.
        vm.toggleWeak("hematology")
        assertEquals(OnboardingViewModel.Step.EXAM, vm.state.value.step)
        val steps = OnboardingViewModel.Step.entries
        for (expected in steps.drop(1)) {
            assertTrue(vm.nextStep())
            assertEquals(expected, vm.state.value.step)
        }
        // last step: nextStep() returns false and triggers finish flow in the UI
        assertFalse(vm.nextStep())
    }

    @Test
    fun `prevStep goes back and stops at the first step`() {
        val vm = vm()
        assertFalse(vm.prevStep())
        vm.nextStep()
        vm.prevStep()
        assertEquals(OnboardingViewModel.Step.EXAM, vm.state.value.step)
    }

    @Test
    fun `subject validation requires at least one strong or weak`() {
        val vm = vm()
        vm.selectExam(ExamTarget.MLS_ASCPI)
        vm.nextStep() // -> DATE
        vm.nextStep() // -> LEVEL
        vm.setLevel(KnowledgeLevel.BEGINNER)
        vm.nextStep() // -> SUBJECTS
        val error = vm.validate(OnboardingViewModel.Step.SUBJECTS)
        assertNotNull(error)
    }

    @Test
    fun `subject cannot be both strong and weak`() {
        val vm = vm()
        vm.toggleStrong("blood-banking")
        vm.toggleWeak("blood-banking")
        // weak wins: toggling weak removes it from strong
        assertTrue("blood-banking" in vm.state.value.weakSubjects)
        assertFalse("blood-banking" in vm.state.value.strongSubjects)
        assertNull(vm.validate(OnboardingViewModel.Step.SUBJECTS))
    }

    @Test
    fun `buildProfile carries all wizard choices`() {
        val vm = vm()
        vm.selectExam(ExamTarget.PH_BOARD)
        vm.setTargetDate("2026-11-30")
        vm.setLevel(KnowledgeLevel.ADVANCED)
        vm.toggleWeak("hematology")
        vm.toggleStrong("blood-banking")
        vm.setMinutesPerDay(120)
        vm.setScheduleWindow("mon", 720, 1260)
        vm.toggleLearningPref("flashcards")
        vm.setExplanationStyle(ExplanationStyle.TAGLISH)
        vm.setNotifications(false)
        vm.setFocusFeature(true)

        val profile: UserProfile = vm.buildProfile()
        assertEquals(ExamTarget.PH_BOARD, profile.examTarget)
        assertEquals("2026-11-30", profile.targetExamDate)
        assertEquals(KnowledgeLevel.ADVANCED, profile.knowledgeLevel)
        assertEquals(listOf("hematology"), profile.weakestSubjects)
        assertEquals(listOf("blood-banking"), profile.strongestSubjects)
        assertEquals(120, profile.minutesPerDay)
        assertEquals(listOf(listOf(720, 1260)), profile.preferredSchedule["mon"])
        assertEquals(listOf("flashcards"), profile.learningPreferences)
        assertEquals(ExplanationStyle.TAGLISH, profile.explanationStyle)
        assertFalse(profile.notificationsEnabled)
        assertTrue(profile.focusFeatureEnabled)
        assertTrue(profile.onboardingCompleted)
    }

    @Test
    fun `generate builds a study plan and finish persists it`() {
        var savedProfile: UserProfile? = null
        var savedPlan: StudyPlan? = null
        val vm = OnboardingViewModel(
            topicProvider = { topics },
            onFinish = { p, plan -> savedProfile = p; savedPlan = plan }
        )
        vm.toggleWeak("blood-banking")
        vm.setTargetDate("2026-12-31")
        val plan = runBlocking { vm.finish() }
        assertTrue(plan.days.isNotEmpty())
        assertNotNull(savedPlan)
        assertEquals("blood-banking", savedProfile?.weakestSubjects?.firstOrNull())
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun `minutes per day is clamped to a sane range`() {
        val vm = vm()
        vm.setMinutesPerDay(5)
        assertEquals(10, vm.state.value.minutesPerDay)
        vm.setMinutesPerDay(9999)
        assertEquals(600, vm.state.value.minutesPerDay)
    }
}
