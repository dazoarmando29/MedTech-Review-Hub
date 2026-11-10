package com.medtech.reviewhub

import com.medtech.reviewhub.domain.ContextKind
import com.medtech.reviewhub.domain.Motivation
import com.medtech.reviewhub.domain.MotivationBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Daily-quote rotation determinism (shared/content/motivation.json): the daily
 * quote is a pure function of the day-of-year, "show another" rotates by an
 * offset, "random" is deterministic per seed, and context quotes always come
 * from the matching contextQuotes list.
 */
class MotivationTest {

    private val bank = MotivationBank(
        dailyQuotes = listOf("q0", "q1", "q2", "q3", "q4"),
        contextQuotes = mapOf(
            "afterLesson" to listOf("l0", "l1"),
            "afterWrong" to listOf("w0")
        )
    )

    @Test
    fun `daily quote is deterministic per day of year`() {
        assertEquals(Motivation.dailyQuote(bank, 100), Motivation.dailyQuote(bank, 100))
        assertEquals("q0", Motivation.dailyQuote(bank, 0))
        assertEquals("q3", Motivation.dailyQuote(bank, 3))
    }

    @Test
    fun `daily quote index stays in range for every day of the year`() {
        for (day in 1..366) {
            val quote = Motivation.dailyQuote(bank, day)
            assertTrue(quote in bank.dailyQuotes)
        }
    }

    @Test
    fun `rotated quote advances and wraps`() {
        assertEquals("q1", Motivation.rotatedQuote(bank, dayOfYear = 0, offset = 1))
        assertEquals("q0", Motivation.rotatedQuote(bank, dayOfYear = 0, offset = 5)) // wraps
        assertEquals("q0", Motivation.rotatedQuote(bank, dayOfYear = 0, offset = 0))
    }

    @Test
    fun `random quote is deterministic for a seed`() {
        assertEquals(Motivation.randomQuote(bank, 7), Motivation.randomQuote(bank, 7))
        assertTrue(Motivation.randomQuote(bank, 7) in bank.dailyQuotes)
    }

    @Test
    fun `context quote comes from the matching list`() {
        val quote = Motivation.contextQuote(bank, ContextKind.AFTER_LESSON, seed = 0)
        assertTrue(quote in bank.contextQuotes["afterLesson"]!!)
        val wrong = Motivation.contextQuote(bank, ContextKind.AFTER_WRONG, seed = 3)
        assertEquals("w0", wrong)
    }

    @Test
    fun `context quote is deterministic per seed`() {
        assertEquals(
            Motivation.contextQuote(bank, ContextKind.AFTER_LESSON, 4),
            Motivation.contextQuote(bank, ContextKind.AFTER_LESSON, 4)
        )
    }

    @Test
    fun `context quote falls back safely when the list is missing`() {
        val empty = MotivationBank(dailyQuotes = listOf("only"))
        val q = Motivation.contextQuote(empty, ContextKind.LONG_SESSION, seed = 1)
        assertTrue(q.isNotBlank())
    }

    @Test
    fun `fallback bank is usable`() {
        assertTrue(Motivation.FALLBACK.dailyQuotes.isNotEmpty())
        assertTrue(Motivation.dailyQuote(Motivation.FALLBACK, 42).isNotBlank())
    }

    @Test
    fun `context kind keys match motivation json keys`() {
        assertEquals("beforeStudy", ContextKind.BEFORE_STUDY.key)
        assertEquals("afterLesson", ContextKind.AFTER_LESSON.key)
        assertEquals("afterFlashcards", ContextKind.AFTER_FLASHCARDS.key)
        assertEquals("afterWrong", ContextKind.AFTER_WRONG.key)
        assertEquals("longSession", ContextKind.LONG_SESSION.key)
        assertEquals("afterGoals", ContextKind.AFTER_GOALS.key)
    }
}
