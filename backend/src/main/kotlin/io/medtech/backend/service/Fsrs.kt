package io.medtech.backend.service

import io.medtech.backend.domain.FlashcardHistoryEntry
import io.medtech.backend.domain.FlashcardSchedule
import io.medtech.backend.domain.Rating
import io.medtech.backend.domain.ScheduleState
import kotlinx.datetime.Instant
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round

/**
 * FSRS-4.5 scheduler (shared/FSRS.md). Kept in sync with the web (web/src/lib/fsrs.ts) and
 * Android (SpacedRepetition.kt) reference implementations.
 */
object Fsrs {
    const val DECAY = -0.5
    val FACTOR: Double = 0.9.pow(1.0 / DECAY) - 1.0 // = 19/81 ≈ 0.2345679
    const val DEFAULT_RETENTION = 0.9

    // Default FSRS-4.5 parameters (fsrs4anki v4.5), 0-indexed as in shared/FSRS.md
    val W = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
        1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )

    const val DAY_MILLIS = 86_400_000.0
    const val MINUTE_MILLIS = 60_000.0

    private val learningSteps = listOf(1.0, 10.0) // minutes
    private val relearningSteps = listOf(10.0) // minutes

    fun retrievability(tDays: Double, stability: Double): Double =
        (1 + FACTOR * tDays / stability).pow(DECAY)

    fun initStability(g: Int): Double = maxOf(W[g - 1], 0.1)

    fun initDifficulty(g: Int): Double = clamp(W[4] - (g - 3) * W[5], 1.0, 10.0)

    fun nextDifficulty(d: Double, g: Int): Double {
        val dPrime = d - W[6] * (g - 3) * (10 - d) / 9
        return clamp(W[7] * initDifficulty(4) + (1 - W[7]) * dPrime, 1.0, 10.0)
    }

    fun nextStability(d: Double, s: Double, g: Int, r: Double): Double =
        if (g == 1) {
            clamp(
                W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)),
                0.1,
                s,
            )
        } else {
            val hard = if (g == 2) W[15] else 1.0
            val easy = if (g == 4) W[16] else 1.0
            clamp(
                s * (1 + exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy),
                s,
                36_500.0,
            )
        }

    fun intervalDays(stability: Double, request: Double = DEFAULT_RETENTION): Int {
        val i = stability / FACTOR * (request.pow(1.0 / DECAY) - 1)
        return clamp(round(i), 1.0, 36_500.0).toInt()
    }

    private fun clamp(x: Double, min: Double, max: Double): Double = x.coerceIn(min, max)

    data class CardState(
        val state: ScheduleState,
        val stability: Double,
        val difficulty: Double,
        val dueAtMillis: Long,
        val lastReviewAtMillis: Long?,
        val reps: Int,
        val lapses: Int,
        val learningStep: Int,
    )

    data class ReviewOutcome(
        val schedule: FlashcardSchedule,
        val history: FlashcardHistoryEntry,
        val changed: Boolean,
        val learningStep: Int,
    )

    private data class Step(
        val state: ScheduleState,
        val stability: Double,
        val difficulty: Double,
        val dueAtMillis: Long,
        val learningStep: Int,
    )

    fun review(card: CardState, rating: Rating, nowMillis: Long): ReviewOutcome {
        val g = when (rating) {
            Rating.AGAIN -> 1
            Rating.HARD -> 2
            Rating.GOOD -> 3
            Rating.EASY -> 4
        }
        val last = card.lastReviewAtMillis
        if (card.state != ScheduleState.NEW && last != null && nowMillis - last < MINUTE_MILLIS) {
            // Same-minute reviews are ignored: no schedule change.
            return ReviewOutcome(
                schedule = card.toSchedule(),
                history = FlashcardHistoryEntry(Instant.fromEpochMilliseconds(nowMillis), rating, 0.0),
                changed = false,
                learningStep = card.learningStep,
            )
        }

        val step = when (card.state) {
            ScheduleState.NEW -> reviewNew(g, nowMillis)
            ScheduleState.LEARNING, ScheduleState.RELEARNING -> reviewLearning(card, g, nowMillis)
            ScheduleState.REVIEW -> reviewReview(card, g, nowMillis)
        }
        val intervalDaysActual = (step.dueAtMillis - nowMillis) / DAY_MILLIS

        val schedule = FlashcardSchedule(
            state = step.state,
            stability = step.stability,
            difficulty = step.difficulty,
            dueAt = Instant.fromEpochMilliseconds(step.dueAtMillis),
            lastReviewAt = Instant.fromEpochMilliseconds(nowMillis),
            reps = card.reps + 1,
            lapses = card.lapses + if (step.state == ScheduleState.RELEARNING && card.state == ScheduleState.REVIEW) 1 else 0,
        )
        return ReviewOutcome(
            schedule = schedule,
            history = FlashcardHistoryEntry(
                at = Instant.fromEpochMilliseconds(nowMillis),
                rating = rating,
                intervalDays = (round(intervalDaysActual * 100) / 100),
            ),
            changed = true,
            learningStep = step.learningStep,
        )
    }

    private fun reviewNew(g: Int, nowMillis: Long): Step {
        return when (g) {
            1 -> Step(ScheduleState.LEARNING, 0.0, 0.0, (nowMillis + MINUTE_MILLIS).toLong(), 0)
            2 -> Step(ScheduleState.LEARNING, 0.0, 0.0, (nowMillis + 10.0 * 1.2 * MINUTE_MILLIS).toLong(), 1)
            3 -> {
                val s = initStability(3)
                val d = initDifficulty(3)
                Step(ScheduleState.REVIEW, s, d, (nowMillis + intervalDays(s) * DAY_MILLIS).toLong(), 0)
            }
            else -> {
                val s = initStability(4)
                val d = initDifficulty(4)
                Step(ScheduleState.REVIEW, s, d, (nowMillis + intervalDays(s) * DAY_MILLIS).toLong(), 0)
            }
        }
    }

    private fun reviewLearning(card: CardState, g: Int, nowMillis: Long): Step {
        val steps = if (card.state == ScheduleState.RELEARNING) relearningSteps else learningSteps
        val step = card.learningStep.coerceIn(0, steps.lastIndex)
        val stability = if (card.stability > 0) card.stability else 0.0
        val difficulty = if (card.difficulty > 0) card.difficulty else 0.0
        return when (g) {
            1 -> Step(card.state, stability, difficulty, (nowMillis + steps[0] * MINUTE_MILLIS).toLong(), 0)
            2 -> Step(card.state, stability, difficulty, (nowMillis + steps[step] * 1.2 * MINUTE_MILLIS).toLong(), step)
            3 -> {
                if (step < steps.lastIndex) {
                    Step(card.state, stability, difficulty, (nowMillis + steps[step + 1] * MINUTE_MILLIS).toLong(), step + 1)
                } else {
                    graduate(stability, difficulty, g, nowMillis)
                }
            }
            else -> graduate(stability, difficulty, g, nowMillis)
        }
    }

    private fun graduate(stability: Double, difficulty: Double, g: Int, nowMillis: Long): Step {
        val s = if (stability > 0) stability else initStability(g)
        val d = if (difficulty > 0) difficulty else initDifficulty(g)
        return Step(ScheduleState.REVIEW, s, d, (nowMillis + intervalDays(s) * DAY_MILLIS).toLong(), 0)
    }

    private fun reviewReview(card: CardState, g: Int, nowMillis: Long): Step {
        val last = card.lastReviewAtMillis ?: (nowMillis - DAY_MILLIS).toLong()
        val t = (nowMillis - last) / DAY_MILLIS
        val r = retrievability(t, card.stability.coerceAtLeast(0.1))
        val d = nextDifficulty(card.difficulty.coerceIn(1.0, 10.0), g)
        return if (g == 1) {
            val s = nextStability(d, card.stability.coerceAtLeast(0.1), g, r)
            Step(ScheduleState.RELEARNING, s, d, (nowMillis + relearningSteps[0] * MINUTE_MILLIS).toLong(), 0)
        } else {
            val s = nextStability(d, card.stability.coerceAtLeast(0.1), g, r)
            Step(ScheduleState.REVIEW, s, d, (nowMillis + intervalDays(s) * DAY_MILLIS).toLong(), 0)
        }
    }

    private fun CardState.toSchedule() = FlashcardSchedule(
        state = state,
        stability = stability,
        difficulty = difficulty,
        dueAt = Instant.fromEpochMilliseconds(dueAtMillis),
        lastReviewAt = lastReviewAtMillis?.let { Instant.fromEpochMilliseconds(it) },
        reps = reps,
        lapses = lapses,
    )
}
