package com.medtech.reviewhub.domain

import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.domain.model.QuizResult
import com.medtech.reviewhub.domain.model.SubjectScore
import com.medtech.reviewhub.domain.model.WeakTopic

/**
 * Quiz scoring + the adaptive difficulty-selection rule.
 *
 * Scoring produces the QuizResult shape from shared/api/API.md §6.
 * All percentages here are estimates of study performance, NOT ASCP scores or
 * pass guarantees (shared/FSRS.md "Honest labeling").
 */
object QuizScoring {

    /** Immutable record of one answered question. */
    data class Answer(val question: QuizQuestion, val chosenId: String, val timeSeconds: Int = 0)

    fun score(answers: List<Answer>, totalSeconds: Int): QuizResult {
        val correct = answers.count { it.chosenId == it.question.correctChoiceId }
        val total = answers.size
        val bySubject = answers
            .groupBy { it.question.subjectId }
            .map { (subject, list) ->
                SubjectScore(
                    subjectId = subject,
                    correct = list.count { it.chosenId == it.question.correctChoiceId },
                    total = list.size
                )
            }
            .sortedBy { it.subjectId }
        val weakTopics = answers
            .groupBy { it.question.topicId }
            .map { (topic, list) ->
                val acc = list.count { it.chosenId == it.question.correctChoiceId }.toDouble() / list.size
                WeakTopic(topicId = topic, accuracy = acc)
            }
            .filter { it.accuracy < 0.75 }
            .sortedBy { it.accuracy }
        val incorrect = answers.filter { it.chosenId != it.question.correctChoiceId }.map { it.question.id }
        return QuizResult(
            score = if (total == 0) 0.0 else correct.toDouble() / total,
            correct = correct,
            total = total,
            timeSeconds = totalSeconds,
            bySubject = bySubject,
            weakTopics = weakTopics,
            incorrectQuestionIds = incorrect
        )
    }

    // ------------------------------------------------------------------
    // Adaptive practice difficulty-selection rule (documented, transparent).
    //
    // THIS IS NOT THE ASCP COMPUTER-ADAPTIVE TEST (CAT). It is a simple,
    // explainable self-adjusting rule for practice only:
    //   1. Maintain a rolling "skill estimate" per difficulty band: the last 5
    //      answers per band determine band accuracy.
    //   2. If accuracy in the current band >= 0.80 over its last 5 answers and
    //      a harder band exists, move up one band.
    //   3. If accuracy <= 0.40 over its last 5 answers and an easier band
    //      exists, move down one band.
    //   4. Otherwise stay. The pool then draws from the chosen band, with 20%
    //      of questions drawn from adjacent bands to keep variety.
    // ------------------------------------------------------------------

    const val ADAPTIVE_DISCLAIMER =
        "Adaptive practice (self-adjusting difficulty) adjusts question difficulty " +
            "based on your recent answers using a simple documented rule. " +
            "It is NOT the ASCP computer-adaptive test (CAT) and is not affiliated with ASCP."

    val BANDS = listOf("EASY", "MEDIUM", "HARD")

    data class AdaptiveState(
        val currentBand: String = "MEDIUM",
        val history: Map<String, List<Boolean>> = emptyMap()
    )

    fun recordAnswer(state: AdaptiveState, band: String, wasCorrect: Boolean): AdaptiveState {
        val h = state.history[band].orEmpty().takeLast(4) + wasCorrect
        return state.copy(history = state.history + (band to h))
    }

    fun nextBand(state: AdaptiveState): String {
        val band = state.currentBand
        val idx = BANDS.indexOf(band).coerceAtLeast(0)
        val recent = state.history[band].orEmpty()
        if (recent.size < 5) return band
        val acc = recent.count { it }.toDouble() / recent.size
        return when {
            acc >= 0.80 && idx < BANDS.lastIndex -> BANDS[idx + 1]
            acc <= 0.40 && idx > 0 -> BANDS[idx - 1]
            else -> band
        }
    }

    /** Picks the next question given the adaptive state (band + 20% adjacent variety). */
    fun pickAdaptiveQuestion(
        state: AdaptiveState,
        pool: List<QuizQuestion>,
        remainingIds: Set<String>,
        random: java.util.Random = java.util.Random(0)
    ): QuizQuestion? {
        val remaining = pool.filter { it.id in remainingIds }
        if (remaining.isEmpty()) return null
        val band = nextBand(state)
        val inBand = remaining.filter { it.difficulty.equals(band, ignoreCase = true) }
        val adjacent = when (band) {
            "EASY" -> remaining.filter { it.difficulty.equals("MEDIUM", ignoreCase = true) }
            "HARD" -> remaining.filter { it.difficulty.equals("MEDIUM", ignoreCase = true) }
            else -> remaining.filter { it.difficulty.equals("EASY", ignoreCase = true) || it.difficulty.equals("HARD", ignoreCase = true) }
        }
        val useAdjacent = inBand.isNotEmpty() && adjacent.isNotEmpty() && random.nextInt(5) == 0
        val bucket = if (useAdjacent) adjacent else inBand.ifEmpty { remaining }
        return bucket[random.nextInt(bucket.size)]
    }
}
