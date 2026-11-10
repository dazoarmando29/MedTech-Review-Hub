package com.medtech.reviewhub

import com.medtech.reviewhub.domain.model.Choice
import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.domain.model.QuizResult
import com.medtech.reviewhub.ui.quiz.QuizMode
import com.medtech.reviewhub.ui.quiz.QuizPersistence
import com.medtech.reviewhub.ui.quiz.QuizStyle
import com.medtech.reviewhub.ui.quiz.QuizViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compose-less ViewModel test: QuizViewModel is a pure session state machine
 * (persistence injected via [QuizPersistence]), testable with plain JUnit.
 */
class QuizViewModelTest {

    private fun q(id: String, correct: String, difficulty: String = "MEDIUM") = QuizQuestion(
        id = id, subjectId = "hematology", topicId = "cbc", difficulty = difficulty,
        stem = "Question $id",
        choices = listOf(Choice("A", "a"), Choice("B", "b"), Choice("C", "c"), Choice("D", "d")),
        correctChoiceId = correct,
        explanation = "Explanation $id",
        rationales = mapOf("A" to "why A", "B" to "why B", "C" to "why C", "D" to "why D"),
        misconception = "Trap $id"
    )

    private val bank = listOf(q("1", "A"), q("2", "B"), q("3", "C"))

    @Test
    fun `study mode returns feedback with misconception`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.RANDOM, QuizStyle.STUDY)
        val feedback = vm.answer("D", nowMs = 1000L)
        assertNotNull(feedback)
        assertFalse(feedback!!.correct)
        assertEquals("A", feedback.correctChoiceId)
        assertEquals("why D", feedback.rationaleChosen)
        assertEquals("Trap 1", feedback.misconception)
    }

    @Test
    fun `exam mode hides feedback until finish`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.MOCK_EXAM, QuizStyle.EXAM)
        val feedback = vm.answer("A", nowMs = 1000L)
        assertNull("exam mode must not expose feedback while answering", vm.state.value.feedback)
        assertNotNull(feedback) // returned for internal use, but not stored in state
        val review = vm.feedbackFor("1")
        assertNotNull(review)
        assertTrue(review!!.correct)
    }

    @Test
    fun `finishing computes the quiz result`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.RANDOM, QuizStyle.STUDY, nowMs = 0L)
        vm.answer("A", nowMs = 1000L)  // correct
        vm.next(nowMs = 2000L)
        vm.answer("A", nowMs = 3000L)  // wrong (correct is B)
        vm.next(nowMs = 4000L)
        vm.answer("C", nowMs = 5000L)  // correct
        vm.markTime(nowMs = 60_000L)
        val result: QuizResult = vm.finish(generateCardsForMissed = true)
        assertEquals(2, result.correct)
        assertEquals(3, result.total)
        assertEquals(2.0 / 3.0, result.score, 1e-9)
        assertEquals(listOf("2"), result.incorrectQuestionIds)
        assertEquals(60, result.timeSeconds)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun `double answering is ignored`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.RANDOM, QuizStyle.STUDY)
        vm.answer("A")
        vm.answer("B")
        assertEquals("A", vm.state.value.answers["1"])
    }

    @Test
    fun `next returns false at the end`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.RANDOM, QuizStyle.STUDY)
        assertTrue(vm.next())
        assertTrue(vm.next())
        assertFalse(vm.next())
    }

    @Test
    fun `persistence hooks are called`() = runBlocking {
        var started = false
        var finished = false
        var missedCount = -1
        val persistence = object : QuizPersistence {
            override suspend fun onQuizStart(questionIds: List<String>, mode: String) { started = true }
            override suspend fun onQuizFinish(result: QuizResult, answers: Map<String, String>, questions: List<QuizQuestion>, timeSeconds: Int) { finished = true }
            override suspend fun onMissed(questions: List<QuizQuestion>, answers: Map<String, String>): Int {
                missedCount = questions.size
                return questions.size
            }
        }
        val vm = QuizViewModel(bank, persistence)
        vm.start(bank, QuizMode.RANDOM, QuizStyle.STUDY, nowMs = 0L)
        vm.answer("D", nowMs = 1L) // wrong
        vm.finish()
        assertTrue(started)
        assertTrue(finished)
        assertEquals(1, missedCount)
    }

    @Test
    fun `mock exam time limit is 150 minutes`() = runBlocking {
        val vm = QuizViewModel(bank)
        vm.start(bank, QuizMode.MOCK_EXAM, QuizStyle.EXAM, nowMs = 0L)
        assertEquals(150 * 60, vm.state.value.timeLimitSeconds)
        assertFalse(vm.timeExpired(nowMs = 60_000L))
        assertTrue(vm.timeExpired(nowMs = 151L * 60_000L))
    }

    @Test
    fun `select picks by mode`() {
        val subject = QuizViewModel.select(bank, QuizMode.SUBJECT, subjectId = "hematology", limit = 10)
        assertEquals(3, subject.size)
        val topic = QuizViewModel.select(bank, QuizMode.TOPIC, topicId = "nope", limit = 10)
        assertTrue(topic.isEmpty())
        val incorrect = QuizViewModel.select(bank, QuizMode.PREVIOUSLY_INCORRECT, incorrectIds = setOf("2"), limit = 10)
        assertEquals(listOf("2"), incorrect.map { it.id })
    }
}
