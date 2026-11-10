package com.medtech.reviewhub

import com.medtech.reviewhub.domain.CardState
import com.medtech.reviewhub.domain.Rating
import com.medtech.reviewhub.domain.SpacedRepetition
import com.medtech.reviewhub.domain.model.CardSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FSRS-4.5 scheduler tests — verify the Kotlin implementation against the
 * formulas in shared/FSRS.md.
 */
class FsrsTest {

    private val dayMs = 86_400_000L
    private val now = 1_700_000_000_000L

    // ---- Constants from shared/FSRS.md ---------------------------------

    @Test
    fun `factor equals 19 over 81`() {
        assertEquals(19.0 / 81.0, SpacedRepetition.FACTOR, 1e-12)
        // And that equals 0.9^(1/DECAY) - 1
        val derived = Math.pow(0.9, 1.0 / SpacedRepetition.DECAY) - 1.0
        assertEquals(derived, SpacedRepetition.FACTOR, 1e-12)
    }

    @Test
    fun `default weights match FSRS-45`() {
        assertEquals(17, SpacedRepetition.W.size)
        assertEquals(0.4872, SpacedRepetition.W[0], 1e-12)
        assertEquals(1.4003, SpacedRepetition.W[1], 1e-12)
        assertEquals(3.7145, SpacedRepetition.W[2], 1e-12)
        assertEquals(2.8755, SpacedRepetition.W[16], 1e-12)
    }

    // ---- Retrievability --------------------------------------------------

    @Test
    fun `retrievability at t equals S is 0`() {
        // R(t=S) = (1 + FACTOR) ^ DECAY = (100/81)^-0.5 = 0.9
        for (s in listOf(1.0, 3.7145, 10.0, 100.5)) {
            assertEquals(0.9, SpacedRepetition.retrievability(s, s), 1e-9)
        }
    }

    @Test
    fun `retrievability decreases with time`() {
        val s = 10.0
        val early = SpacedRepetition.retrievability(1.0, s)
        val late = SpacedRepetition.retrievability(30.0, s)
        assertTrue(early > late)
    }

    // ---- Init stability / difficulty --------------------------------------

    @Test
    fun `init stability equals W indexed by rating`() {
        assertEquals(SpacedRepetition.W[0], SpacedRepetition.initStability(Rating.AGAIN), 1e-12)
        assertEquals(SpacedRepetition.W[1], SpacedRepetition.initStability(Rating.HARD), 1e-12)
        assertEquals(SpacedRepetition.W[2], SpacedRepetition.initStability(Rating.GOOD), 1e-12)
        assertEquals(SpacedRepetition.W[3], SpacedRepetition.initStability(Rating.EASY), 1e-12)
    }

    @Test
    fun `init difficulty clamps to 1 through 10`() {
        for (rating in Rating.entries) {
            val d = SpacedRepetition.initDifficulty(rating)
            assertTrue("difficulty $d out of range", d in 1.0..10.0)
        }
        // clamp() behavior directly
        assertEquals(1.0, SpacedRepetition.clamp(-5.0, 1.0, 10.0), 1e-12)
        assertEquals(10.0, SpacedRepetition.clamp(99.0, 1.0, 10.0), 1e-12)
    }

    @Test
    fun `next difficulty clamps to 1 through 10`() {
        // Extreme inputs are clamped into [1, 10] by the formula's clamp.
        assertEquals(1.0, SpacedRepetition.nextDifficulty(-100.0, Rating.EASY), 1e-9)
        assertEquals(10.0, SpacedRepetition.nextDifficulty(999.0, Rating.AGAIN), 1e-9)
        for (rating in Rating.entries) {
            assertTrue(SpacedRepetition.nextDifficulty(5.0, rating) in 1.0..10.0)
        }
    }

    // ---- Interval formula -------------------------------------------------

    @Test
    fun `interval days equals S at default retention`() {
        for (s in listOf(1.0, 3.7145, 7.3, 30.2)) {
            assertEquals(Math.round(s).toDouble(), SpacedRepetition.intervalDays(s), 1e-9)
        }
    }

    @Test
    fun `interval days is clamped between 1 and 36500`() {
        assertEquals(1.0, SpacedRepetition.intervalDays(0.001), 1e-9)
        assertEquals(36500.0, SpacedRepetition.intervalDays(100_000.0), 1e-9)
    }

    // ---- Scheduling flow ---------------------------------------------------

    @Test
    fun `new card rated Good once stays in learning at the 10 minute step`() {
        val outcome = SpacedRepetition.review(CardSchedule(), Rating.GOOD, now)
        assertEquals(CardState.LEARNING, outcome.schedule.state)
        // 10-minute step after first Good (step index 1)
        assertEquals(10 * 60_000L, outcome.schedule.dueAtMs - now)
    }

    @Test
    fun `new card rated Good twice graduates with stability W2 and interval about S days`() {
        val first = SpacedRepetition.review(CardSchedule(), Rating.GOOD, now)
        val second = SpacedRepetition.review(first.schedule, Rating.GOOD, now + 11 * 60_000L)
        assertEquals(CardState.REVIEW, second.schedule.state)
        assertEquals(SpacedRepetition.W[2], second.schedule.stability, 1e-12)
        // interval ≈ stability days (rounded)
        assertEquals(Math.round(SpacedRepetition.W[2]).toDouble(), second.intervalDays, 1e-9)
        assertEquals(Math.round(SpacedRepetition.W[2]).toDouble() * dayMs, (second.schedule.dueAtMs - (now + 11 * 60_000L)).toDouble(), 1.0)
        // difficulty initialized from the graduating rating (Good)
        assertEquals(SpacedRepetition.initDifficulty(Rating.GOOD), second.schedule.difficulty, 1e-12)
    }

    @Test
    fun `new card rated Easy graduates immediately with stability W3`() {
        val outcome = SpacedRepetition.review(CardSchedule(), Rating.EASY, now)
        assertEquals(CardState.REVIEW, outcome.schedule.state)
        assertEquals(SpacedRepetition.W[3], outcome.schedule.stability, 1e-12)
    }

    @Test
    fun `new card rated Again restarts the learning steps`() {
        val good = SpacedRepetition.review(CardSchedule(), Rating.GOOD, now)
        assertEquals(1, good.schedule.stepIndex)
        val again = SpacedRepetition.review(good.schedule, Rating.AGAIN, now + 11 * 60_000L)
        assertEquals(CardState.LEARNING, again.schedule.state)
        assertEquals(0, again.schedule.stepIndex)
        assertEquals(1 * 60_000L, again.schedule.dueAtMs - (now + 11 * 60_000L))
    }

    @Test
    fun `review Again decreases stability`() {
        val graduated = reviewGraduatedGood()
        val sBefore = graduated.schedule.stability
        val outcome = SpacedRepetition.review(
            graduated.schedule, Rating.AGAIN,
            graduated.schedule.lastReviewAtMs!! + 3 * dayMs
        )
        assertTrue(outcome.schedule.stability < sBefore)
        assertTrue(outcome.schedule.stability >= 0.1)
        assertEquals(CardState.RELEARNING, outcome.schedule.state)
        assertEquals(1, outcome.schedule.lapses)
    }

    @Test
    fun `review Good grows stability`() {
        val graduated = reviewGraduatedGood()
        val sBefore = graduated.schedule.stability
        val outcome = SpacedRepetition.review(
            graduated.schedule, Rating.GOOD,
            graduated.schedule.lastReviewAtMs!! + 3 * dayMs
        )
        assertTrue(outcome.schedule.stability > sBefore)
        assertEquals(CardState.REVIEW, outcome.schedule.state)
    }

    @Test
    fun `lapse graduates relearning at the lapse stability`() {
        val graduated = reviewGraduatedGood()
        val lapse = SpacedRepetition.review(
            graduated.schedule, Rating.AGAIN,
            graduated.schedule.lastReviewAtMs!! + 3 * dayMs
        )
        val sLapse = lapse.schedule.stability
        val relearned = SpacedRepetition.review(
            lapse.schedule, Rating.GOOD,
            lapse.schedule.dueAtMs + 60_000L
        )
        assertEquals(CardState.REVIEW, relearned.schedule.state)
        assertEquals(sLapse, relearned.schedule.stability, 1e-12)
    }

    @Test
    fun `review within one minute is ignored`() {
        val graduated = reviewGraduatedGood()
        val last = graduated.schedule.lastReviewAtMs!!
        val outcome = SpacedRepetition.review(graduated.schedule, Rating.AGAIN, last + 30_000L)
        assertTrue(outcome.ignored)
        assertEquals(graduated.schedule, outcome.schedule)
    }

    @Test
    fun `reps increments on every non-ignored review`() {
        var schedule = CardSchedule()
        schedule = SpacedRepetition.review(schedule, Rating.GOOD, now).schedule
        schedule = SpacedRepetition.review(schedule, Rating.GOOD, now + 11 * 60_000L).schedule
        assertEquals(2, schedule.reps)
    }

    /** Helper: a NEW card graduated with Good. */
    private fun reviewGraduatedGood() =
        SpacedRepetition.review(
            SpacedRepetition.review(CardSchedule(), Rating.GOOD, now).schedule,
            Rating.GOOD, now + 11 * 60_000L
        )
}
