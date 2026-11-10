package com.medtech.reviewhub

import com.medtech.reviewhub.domain.QuizScoring
import com.medtech.reviewhub.domain.model.Choice
import com.medtech.reviewhub.domain.model.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScoringTest {

    private fun q(id: String, correct: String, subject: String = "hematology", topic: String = "cbc",
                  difficulty: String = "MEDIUM") = QuizQuestion(
        id = id, subjectId = subject, topicId = topic, difficulty = difficulty,
        stem = "Stem $id",
        choices = listOf(Choice("A", "a"), Choice("B", "b"), Choice("C", "c"), Choice("D", "d")),
        correctChoiceId = correct
    )

    @Test
    fun `score computes correct total and accuracy`() {
        val questions = listOf(q("1", "A"), q("2", "B"), q("3", "C"), q("4", "D"))
        val answers = listOf(
            QuizScoring.Answer(questions[0], "A"),
            QuizScoring.Answer(questions[1], "A"),
            QuizScoring.Answer(questions[2], "C"),
            QuizScoring.Answer(questions[3], "B")
        )
        val result = QuizScoring.score(answers, totalSeconds = 120)
        assertEquals(2, result.correct)
        assertEquals(4, result.total)
        assertEquals(0.5, result.score, 1e-9)
        assertEquals(120, result.timeSeconds)
        assertEquals(listOf("2", "4"), result.incorrectQuestionIds)
    }

    @Test
    fun `score groups by subject`() {
        val questions = listOf(
            q("1", "A", subject = "hematology"), q("2", "B", subject = "hematology"),
            q("3", "C", subject = "microbiology")
        )
        val answers = listOf(
            QuizScoring.Answer(questions[0], "A"),
            QuizScoring.Answer(questions[1], "A"),
            QuizScoring.Answer(questions[2], "C")
        )
        val result = QuizScoring.score(answers, 60)
        val hem = result.bySubject.first { it.subjectId == "hematology" }
        val micro = result.bySubject.first { it.subjectId == "microbiology" }
        assertEquals(1, hem.correct); assertEquals(2, hem.total)
        assertEquals(1, micro.correct); assertEquals(1, micro.total)
    }

    @Test
    fun `weak topics are accuracy below 0_75 sorted ascending`() {
        val questions = listOf(
            q("1", "A", topic = "weak-topic"), q("2", "A", topic = "weak-topic"),
            q("3", "A", topic = "weak-topic"), q("4", "B", topic = "strong-topic")
        )
        val answers = listOf(
            QuizScoring.Answer(questions[0], "B"),
            QuizScoring.Answer(questions[1], "B"),
            QuizScoring.Answer(questions[2], "B"),
            QuizScoring.Answer(questions[3], "B")
        )
        val result = QuizScoring.score(answers, 30)
        assertEquals(listOf("weak-topic"), result.weakTopics.map { it.topicId })
        assertEquals(0.0, result.weakTopics.first().accuracy, 1e-9)
    }

    @Test
    fun `empty answers produce zero result`() {
        val result = QuizScoring.score(emptyList(), 0)
        assertEquals(0, result.correct)
        assertEquals(0, result.total)
        assertEquals(0.0, result.score, 1e-9)
    }

    // ---- Adaptive difficulty rule ------------------------------------------

    @Test
    fun `adaptive band moves up after strong performance`() {
        var state = QuizScoring.AdaptiveState(currentBand = "EASY")
        repeat(5) { state = QuizScoring.recordAnswer(state, "EASY", true) }
        assertEquals("MEDIUM", QuizScoring.nextBand(state))
    }

    @Test
    fun `adaptive band moves down after weak performance`() {
        var state = QuizScoring.AdaptiveState(currentBand = "MEDIUM")
        repeat(5) { state = QuizScoring.recordAnswer(state, "MEDIUM", false) }
        assertEquals("EASY", QuizScoring.nextBand(state))
    }

    @Test
    fun `adaptive band stays with mixed performance`() {
        var state = QuizScoring.AdaptiveState(currentBand = "MEDIUM")
        repeat(3) { state = QuizScoring.recordAnswer(state, "MEDIUM", true) }
        repeat(2) { state = QuizScoring.recordAnswer(state, "MEDIUM", false) }
        assertEquals("MEDIUM", QuizScoring.nextBand(state))
    }

    @Test
    fun `adaptive pick stays in the current band when no variety bucket is used`() {
        // Deterministic: with only EASY questions available, the picker must
        // return one of them (the 20% adjacent-band variety has no candidates).
        val bank = listOf(
            q("e1", "A", difficulty = "EASY"), q("e2", "A", difficulty = "EASY")
        )
        val state = QuizScoring.AdaptiveState(currentBand = "EASY")
        val picked = QuizScoring.pickAdaptiveQuestion(state, bank, setOf("e1", "e2"))
        assertTrue(picked != null && picked.id.startsWith("e"))
    }

    @Test
    fun `adaptive pick returns null when nothing remains`() {
        val state = QuizScoring.AdaptiveState(currentBand = "EASY")
        val picked = QuizScoring.pickAdaptiveQuestion(state, emptyList(), emptySet())
        assertTrue(picked == null)
    }
}
