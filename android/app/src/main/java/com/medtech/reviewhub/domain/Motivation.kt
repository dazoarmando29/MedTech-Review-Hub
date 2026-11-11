package com.medtech.reviewhub.domain

/**
 * Daily motivation + contextual encouragement (shared/content/motivation.json).
 *
 * Pure selection logic (no Android dependencies) so it unit-tests on the JVM.
 * Quote banks are original unattributed statements - no invented celebrity
 * attributions. The user's own personal encouragement lives in "Little Notes
 * for You" (Room, private) and is never generated here.
 */
data class MotivationBank(
    val dailyQuotes: List<String> = emptyList(),
    val contextQuotes: Map<String, List<String>> = emptyMap(),
    val mascotLines: Map<String, List<String>> = emptyMap()
) {
    fun isEmpty(): Boolean = dailyQuotes.isEmpty()
}

enum class ContextKind(val key: String, val label: String) {
    BEFORE_STUDY("beforeStudy", "Before study"),
    AFTER_LESSON("afterLesson", "After a lesson"),
    AFTER_FLASHCARDS("afterFlashcards", "After flashcards"),
    AFTER_WRONG("afterWrong", "After wrong answers"),
    LONG_SESSION("longSession", "Long focus session"),
    AFTER_GOALS("afterGoals", "Daily goals complete");

    companion object {
        fun fromKey(key: String): ContextKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Deterministic quote selection:
 *  - the daily quote is chosen by day-of-year index into [MotivationBank.dailyQuotes]
 *    (same quote all day, every device - like the dashboard MotivationCard);
 *  - "show another" rotates by an offset from the daily index (wraps);
 *  - "random quote" uses a caller-supplied seed (deterministic for tests;
 *    the UI passes a time-based seed).
 */
object Motivation {

    /** Minimal bank used when motivation.json is not bundled (build never fails). */
    val FALLBACK = MotivationBank(
        dailyQuotes = listOf(
            "Every page you study brings you closer to your dream.",
            "One question at a time. One chapter at a time. One step closer.",
            "You showed up today - that already counts.",
            "Little by little, a little becomes a lot."
        ),
        contextQuotes = mapOf(
            ContextKind.AFTER_LESSON.key to listOf("One more topic explored. Great work!"),
            ContextKind.AFTER_FLASHCARDS.key to listOf("Look at that memory power!"),
            ContextKind.AFTER_WRONG.key to listOf("Wrong answers show us what to practice next."),
            ContextKind.LONG_SESSION.key to listOf("You've been focusing for a while. Want a short break?"),
            ContextKind.AFTER_GOALS.key to listOf("Daily goals complete - go celebrate a little."),
            ContextKind.BEFORE_STUDY.key to listOf("Ready for another little study adventure?")
        ),
        mascotLines = emptyMap()
    )

    fun dailyQuote(bank: MotivationBank, dayOfYear: Int): String =
        bank.dailyQuotes.pick(Math.floorMod(dayOfYear, bank.dailyQuotes.size))

    /** "Show another": the quote [offset] slots after the daily one (wraps). */
    fun rotatedQuote(bank: MotivationBank, dayOfYear: Int, offset: Int): String =
        bank.dailyQuotes.pick(Math.floorMod(dayOfYear + offset, bank.dailyQuotes.size))

    /** Deterministic pseudo-random quote for a given seed. */
    fun randomQuote(bank: MotivationBank, seed: Long): String =
        bank.dailyQuotes.pick(Math.floorMod(seed, bank.dailyQuotes.size))

    /** Deterministic contextual encouragement for [kind]. */
    fun contextQuote(bank: MotivationBank, kind: ContextKind, seed: Long): String {
        val list = bank.contextQuotes[kind.key] ?: FALLBACK.contextQuotes[kind.key].orEmpty()
        return list.pick(Math.floorMod(seed, list.size))
    }

    fun mascotLine(bank: MotivationBank, group: String, seed: Long): String {
        val list = bank.mascotLines[group].orEmpty()
        return list.pick(Math.floorMod(seed, list.size))
    }

    /** mascotLines group holding the partner's love notes (mascotLines.lalabs). */
    const val LALABS = "lalabs"

    /**
     * "From Lalabs" love note: deterministic day-of-year index into
     * mascotLines.lalabs (same note all day), with [offset] advancing to the
     * next note ("another note"); wraps, and returns "" when the bank has no
     * lalabs lines.
     */
    fun lalabsNote(bank: MotivationBank, dayOfYear: Int, offset: Int = 0): String =
        bank.mascotLines[LALABS].orEmpty().pick(dayOfYear + offset)

    private fun List<String>.pick(index: Int): String =
        if (isEmpty()) "" else this[Math.floorMod(index, size)]
}
