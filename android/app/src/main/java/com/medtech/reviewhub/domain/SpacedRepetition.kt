package com.medtech.reviewhub.domain

import com.medtech.reviewhub.domain.model.CardSchedule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-4.5 spaced-repetition scheduler — implements shared/FSRS.md EXACTLY.
 * The web client (web/src/lib/fsrs.ts) and this Kotlin file must stay in sync.
 *
 * Reference: FSRS-4.5 by Jarrett Ye (fsrs4anki v4.5 default parameters, BSD-3).
 *
 * Honest labeling: we call this "spaced repetition (FSRS-4.5)". Practice
 * percentages and readiness scores elsewhere in the app are estimates of study
 * performance, not ASCP scores or pass guarantees.
 */
object SpacedRepetition {

    // ---- Constants (shared/FSRS.md) -------------------------------------
    const val DECAY = -0.5

    /** FACTOR = 0.9^(1/DECAY) - 1 = 19/81 */
    val FACTOR: Double = 19.0 / 81.0

    const val DEFAULT_RETENTION = 0.9

    /** FSRS-4.5 default weights W[0..16]. */
    val W: DoubleArray = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
        1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755
    )

    const val MIN_STABILITY = 0.1
    const val MAX_STABILITY = 36500.0
    const val MIN_DIFFICULTY = 1.0
    const val MAX_DIFFICULTY = 10.0
    const val MIN_INTERVAL_DAYS = 1.0
    const val MAX_INTERVAL_DAYS = 36500.0

    /** Learning steps in minutes (new cards): 1 min, 10 min. */
    val LEARNING_STEPS_MIN: DoubleArray = doubleArrayOf(1.0, 10.0)

    /** Relearning steps in minutes (after a lapse): 10 min. */
    val RELEARNING_STEPS_MIN: DoubleArray = doubleArrayOf(10.0)

    private const val MS_PER_DAY = 86_400_000.0
    private const val MIN_REVIEW_GAP_MS = 60_000L

    // ---- Ratings --------------------------------------------------------

    // ---- Core formulas (shared/FSRS.md) --------------------------------

    /** retrievability(t, S) = (1 + FACTOR * t / S) ^ DECAY ; t in days. */
    fun retrievability(daysSinceReview: Double, stability: Double): Double {
        require(stability > 0.0) { "stability must be > 0" }
        return (1.0 + FACTOR * daysSinceReview / stability).pow(DECAY)
    }

    /** init_stability(G) = max(W[G-1], 0.1) */
    fun initStability(rating: Rating): Double = max(W[rating.value - 1], MIN_STABILITY)

    /** init_difficulty(G) = clamp(W[4] - (G - 3) * W[5], 1, 10) */
    fun initDifficulty(rating: Rating): Double =
        clamp(W[4] - (rating.value - 3) * W[5], MIN_DIFFICULTY, MAX_DIFFICULTY)

    /**
     * next_difficulty(D, G):
     *   d'    = D - W[6] * (G - 3) * (10 - D) / 9
     *   D_new = clamp(W[7] * init_difficulty(4) + (1 - W[7]) * d', 1, 10)
     * (init_difficulty(4) = init difficulty of rating Easy.)
     */
    fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val dPrime = difficulty - W[6] * (rating.value - 3) * (10.0 - difficulty) / 9.0
        val target = initDifficulty(Rating.EASY)
        return clamp(W[7] * target + (1.0 - W[7]) * dPrime, MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /**
     * next_stability(D, S, G, R):
     *   Again: S_new = clamp(W[11] * D^(-W[12]) * ((S+1)^W[13] - 1) * exp(W[14] * (1-R)), 0.1, S)
     *   else:  S_new = clamp(S * (1 + exp(W[8]) * (11-D) * S^(-W[9])
     *                    * (exp(W[10] * (1-R)) - 1) * hard * easy), S, 36500)
     *   hard = W[15] if G == Hard else 1 ; easy = W[16] if G == Easy else 1
     */
    fun nextStability(difficulty: Double, stability: Double, rating: Rating, retrievability: Double): Double {
        return if (rating == Rating.AGAIN) {
            val lapse =
                W[11] * difficulty.pow(-W[12]) * ((stability + 1.0).pow(W[13]) - 1.0) *
                    Math.exp(W[14] * (1.0 - retrievability))
            clamp(lapse, MIN_STABILITY, stability)
        } else {
            val hard = if (rating == Rating.HARD) W[15] else 1.0
            val easy = if (rating == Rating.EASY) W[16] else 1.0
            val grown = stability * (
                1.0 + Math.exp(W[8]) * (11.0 - difficulty) * stability.pow(-W[9]) *
                    (Math.exp(W[10] * (1.0 - retrievability)) - 1.0) * hard * easy
                )
            clamp(grown, stability, MAX_STABILITY)
        }
    }

    /**
     * interval_days(S, request):
     *   I = S / FACTOR * (request ^ (1 / DECAY) - 1)   (= S when request = 0.9)
     *   return clamp(round(I), 1, 36500)
     */
    fun intervalDays(stability: Double, request: Double = DEFAULT_RETENTION): Double {
        val i = stability / FACTOR * (request.pow(1.0 / DECAY) - 1.0)
        val rounded = i.roundToLong().toDouble()
        return clamp(rounded, MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
    }

    // ---- Scheduling flow ------------------------------------------------

    /**
     * Applies one review of [rating] to [schedule] at [nowMs].
     *
     * State machine (shared/FSRS.md "Scheduling flow"):
     *  - NEW enters LEARNING with steps [1 min, 10 min].
     *    Again -> restart steps; Hard -> current step x 1.2; Good -> next step;
     *    Easy -> graduate immediately. After the last step the card graduates to
     *    REVIEW with S = init_stability(G), D = init_difficulty(G).
     *  - RELEARNING uses step [10 min]; graduation keeps the S computed by the
     *    lapse formula ("graduate at S from the lapse formula").
     *  - REVIEW: R from elapsed days, S/D updated, due = now + interval_days(S_new).
     *    Again -> lapses += 1, enter RELEARNING at 10 min.
     *  - A review less than 1 min after the previous one is ignored.
     */
    fun review(schedule: CardSchedule, rating: Rating, nowMs: Long): ReviewOutcome {
        val last = schedule.lastReviewAtMs
        if (last != null && nowMs - last < MIN_REVIEW_GAP_MS) {
            return ReviewOutcome(schedule, intervalDays = 0.0, ignored = true)
        }
        val reps = schedule.reps + 1
        return when (schedule.state) {
            CardState.NEW, CardState.LEARNING -> learningStep(schedule.copy(reps = reps), rating, nowMs, isNew = schedule.state == CardState.NEW)
            CardState.RELEARNING -> relearningStep(schedule.copy(reps = reps), rating, nowMs)
            CardState.REVIEW -> reviewStep(schedule.copy(reps = reps), rating, nowMs)
        }
    }

    private fun learningStep(schedule: CardSchedule, rating: Rating, nowMs: Long, isNew: Boolean): ReviewOutcome {
        val steps = LEARNING_STEPS_MIN
        var stepIndex = if (isNew) 0 else schedule.stepIndex
        if (stepIndex < 0) stepIndex = 0
        val base = schedule.copy(state = CardState.LEARNING, lastReviewAtMs = nowMs)
        val updated: CardSchedule
        val dueMs: Long
        when (rating) {
            Rating.AGAIN -> {
                stepIndex = 0
                dueMs = nowMs + (steps[0] * 60_000).toLong()
                updated = base.copy(stepIndex = 0, dueAtMs = dueMs)
            }
            Rating.HARD -> {
                // "Hard -> next step x 1.2": repeat the current step stretched by 1.2.
                val step = steps[min(stepIndex, steps.size - 1)]
                dueMs = nowMs + (step * 1.2 * 60_000).toLong()
                updated = base.copy(stepIndex = stepIndex, dueAtMs = dueMs)
            }
            Rating.GOOD -> {
                if (stepIndex + 1 < steps.size) {
                    stepIndex += 1
                    dueMs = nowMs + (steps[stepIndex] * 60_000).toLong()
                    updated = base.copy(stepIndex = stepIndex, dueAtMs = dueMs)
                } else {
                    return graduate(base.copy(stepIndex = 0), rating, nowMs)
                }
            }
            Rating.EASY -> return graduate(base.copy(stepIndex = 0), rating, nowMs)
        }
        val intervalDays = (updated.dueAtMs - nowMs) / MS_PER_DAY
        return ReviewOutcome(updated, intervalDays)
    }

    private fun graduate(schedule: CardSchedule, rating: Rating, nowMs: Long): ReviewOutcome {
        val s = initStability(rating)
        val d = initDifficulty(rating)
        val interval = intervalDays(s)
        val dueMs = nowMs + (interval * MS_PER_DAY).toLong()
        val updated = schedule.copy(
            state = CardState.REVIEW,
            stability = s,
            difficulty = d,
            dueAtMs = dueMs,
            lastReviewAtMs = nowMs,
            stepIndex = 0
        )
        return ReviewOutcome(updated, interval)
    }

    private fun relearningStep(schedule: CardSchedule, rating: Rating, nowMs: Long): ReviewOutcome {
        val steps = RELEARNING_STEPS_MIN
        val base = schedule.copy(lastReviewAtMs = nowMs)
        val updated: CardSchedule
        val dueMs: Long
        when (rating) {
            Rating.AGAIN -> {
                dueMs = nowMs + (steps[0] * 60_000).toLong()
                updated = base.copy(stepIndex = 0, dueAtMs = dueMs)
            }
            Rating.HARD -> {
                dueMs = nowMs + (steps[0] * 1.2 * 60_000).toLong()
                updated = base.copy(stepIndex = 0, dueAtMs = dueMs)
            }
            Rating.GOOD, Rating.EASY -> {
                // Graduate at S from the lapse formula (S was stored when the lapse happened).
                val s = max(schedule.stability, MIN_STABILITY)
                val interval = intervalDays(s)
                dueMs = nowMs + (interval * MS_PER_DAY).toLong()
                updated = base.copy(
                    state = CardState.REVIEW,
                    stability = s,
                    dueAtMs = dueMs,
                    stepIndex = 0
                )
                return ReviewOutcome(updated, interval)
            }
        }
        val intervalDays = (updated.dueAtMs - nowMs) / MS_PER_DAY
        return ReviewOutcome(updated, intervalDays)
    }

    private fun reviewStep(schedule: CardSchedule, rating: Rating, nowMs: Long): ReviewOutcome {
        val last = schedule.lastReviewAtMs ?: nowMs
        val t = (nowMs - last) / MS_PER_DAY
        val s = schedule.stability.coerceAtLeast(MIN_STABILITY)
        val d = schedule.difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        val r = retrievability(t, s)
        val dNew = nextDifficulty(d, rating)
        val sNew = nextStability(d, s, rating, r)
        return if (rating == Rating.AGAIN) {
            // Lapse: enter RELEARNING with 10-min step; S already holds the lapse value.
            val dueMs = nowMs + (RELEARNING_STEPS_MIN[0] * 60_000).toLong()
            val updated = schedule.copy(
                state = CardState.RELEARNING,
                stability = sNew,
                difficulty = dNew,
                dueAtMs = dueMs,
                lastReviewAtMs = nowMs,
                lapses = schedule.lapses + 1,
                stepIndex = 0
            )
            ReviewOutcome(updated, (dueMs - nowMs) / MS_PER_DAY)
        } else {
            val interval = intervalDays(sNew)
            val dueMs = nowMs + (interval * MS_PER_DAY).toLong()
            val updated = schedule.copy(
                state = CardState.REVIEW,
                stability = sNew,
                difficulty = dNew,
                dueAtMs = dueMs,
                lastReviewAtMs = nowMs
            )
            ReviewOutcome(updated, interval)
        }
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = min(max(v, lo), hi)
}

/** Rating mapping: Again=1, Hard=2, Good=3, Easy=4. */
enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        fun fromName(name: String): Rating = when (name.uppercase()) {
            "AGAIN" -> AGAIN
            "HARD" -> HARD
            "GOOD" -> GOOD
            "EASY" -> EASY
            else -> GOOD
        }
    }
}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

/** Per-review outcome: new schedule + interval in days (0 when ignored). */
data class ReviewOutcome(val schedule: CardSchedule, val intervalDays: Double, val ignored: Boolean = false)

/** History entry stored per card: { at, rating, intervalDays } (API.md). */
@kotlinx.serialization.Serializable
data class ReviewLogEntry(val atMs: Long, val rating: Rating, val intervalDays: Double)
