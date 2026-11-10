package com.medtech.reviewhub.domain

import com.medtech.reviewhub.R

/**
 * Mascots: cat "Mimi" and dog "Bantay" (shared/DESIGN.md "Mascot").
 * Vector-drawable illustrations live in res/drawable/mimi_*.xml and
 * bantay_*.xml — original simple shapes, one per mood.
 *
 * Voice rules (shared/DESIGN.md): warm and playful microcopy, Taglish allowed,
 * never shame missed days, never claim ASCP endorsement.
 */
enum class MascotKind(val displayName: String) {
    CAT("Mimi"), DOG("Bantay")
}

enum class MascotMood {
    SLEEPING, HAPPY, CURIOUS, DISAPPOINTED, CELEBRATING, ENCOURAGING, DRAMATIC;

    fun drawable(kind: MascotKind): Int = when (kind) {
        MascotKind.CAT -> when (this) {
            SLEEPING -> R.drawable.mimi_sleeping
            HAPPY -> R.drawable.mimi_happy
            CURIOUS -> R.drawable.mimi_curious
            DISAPPOINTED -> R.drawable.mimi_disappointed
            CELEBRATING -> R.drawable.mimi_celebrating
            ENCOURAGING -> R.drawable.mimi_encouraging
            DRAMATIC -> R.drawable.mimi_dramatic
        }
        MascotKind.DOG -> when (this) {
            SLEEPING -> R.drawable.bantay_sleeping
            HAPPY -> R.drawable.bantay_happy
            CURIOUS -> R.drawable.bantay_curious
            DISAPPOINTED -> R.drawable.bantay_disappointed
            CELEBRATING -> R.drawable.bantay_celebrating
            ENCOURAGING -> R.drawable.bantay_encouraging
            DRAMATIC -> R.drawable.bantay_dramatic
        }
    }
}

object MascotLines {

    /** Mimi (cat) — 12+ original lines. Taglish where it feels natural. */
    val CAT: Map<MascotMood, List<String>> = mapOf(
        MascotMood.HAPPY to listOf(
            "Meow. Blood Banking muna bago TikTok.",
            "Kaya mo 'to, future RMT! One card at a time.",
            "Purr-fect. Your streak is alive and whisker-strong.",
            "Mimi approves of this study session. Treats for everyone.",
            "Forward typing today, tapos samgyup later. Balance yan.",
            "That accuracy? Ang taas. I'm not crying, you're crying.",
            "You showed up today. That's the whole battle, meow."
        ),
        MascotMood.CURIOUS to listOf(
            "Hmm, curious ako: alam mo ba kung bakit O has nO antigens?",
            "Psst… try the flashcards first. Mas madali siya than you think.",
            "Reading time! Sabihin mo sa akin kung ano ang ABO discrepancy.",
            "Question: anong enzyme ang pang-confirm ng liver damage? Let's find out.",
            "Mimi is watching (in a supportive, non-creepy way)."
        ),
        MascotMood.SLEEPING to listOf(
            "Zzz… wake me when the flashcards are due.",
            "Power nap mode. 20 minutes lang, ha? Hindi 2 hours.",
            "Even cats rest. Rest day recorded. Recovery is part of the plan.",
            "Sleeping beauty ako, pero ikaw — tulog ka na ba? Matulog ka na."
        ),
        MascotMood.DISAPPOINTED to listOf(
            "Ay, missed day? Rest day recorded. Recovery is part of the plan.",
            "Hindi kita jinajudge. Bumalik ka lang, okay?",
            "Medyo sad si Mimi, pero bounce back tayo bukas.",
            "One wrong answer is data, not destiny. Laban lang."
        ),
        MascotMood.CELEBRATING to listOf(
            "YAAAS! New streak record! Confetti for the future RMT!",
            "Mimi is doing a happy dance. Nakakaproud ka!",
            "Grabe, quiz crushed! Deserve mo ang milk tea today.",
            "Achievement unlocked: consistent ka na. Hindi biro yan!"
        ),
        MascotMood.ENCOURAGING to listOf(
            "Konti na lang. Malapit na sa dulo. Tuloy lang.",
            "Hard topic? Basahin mo lang ulit. Slowly pero surely.",
            "Every expert was once a beginner. Even this cat.",
            "You don't have to be perfect — you just have to keep showing up.",
            "I believe in you more than I believe in 3am snacks. And that's a lot."
        ),
        MascotMood.DRAMATIC to listOf(
            "THE EXAM IS COMING. *dramatic music* …Kidding. Kaya mo yan.",
            "I lay here, defeated by one hard question… charot. Next!",
            "Wala nang second chance sa red blood cell. Pero sa quiz, meron!",
            "This is my Oscar-worthy moment: 'Study now, thank yourself later.'"
        )
    )

    /** Bantay (dog) — 12+ original lines. */
    val DOG: Map<MascotMood, List<String>> = mapOf(
        MascotMood.HAPPY to listOf(
            "Woof! Review muna bago walk. Deal?",
            "Good job, master! Bantay is proud. Libre mo ko ng treat?",
            "Streak secured! Mas masipag ka pa kay Bantay pag-umaga.",
            "Arf! Ang galing. Blood Banking done, walk naman tayo?",
            "Consistency ka, treats ako. Fair deal, di ba?",
            "Woof woof! Quiz accuracy mo, pang-valedictorian!"
        ),
        MascotMood.CURIOUS to listOf(
            "Woof? Ano yung Rh system? Sabihin mo kay Bantay habang nagre-review.",
            "Hmmm… sniff sniff… amoy ko, may quiz ka today!",
            "Curious si Bantay: kaya mo bang i-explain ang reverse typing?",
            "Tanong lang: sino ang mas mabilis, ikaw o ang mga isohemagglutinins?"
        ),
        MascotMood.SLEEPING to listOf(
            "Zzz… 5 more minutes… tulog muna si Bantay.",
            "Rest day recorded. Pati aso, nagpapahinga. Recovery is part of the plan.",
            "Nap time. Gisingin mo ko pag flashcard time na.",
            "Woof… zzz… huwag mong kalimutan ang streak bukas."
        ),
        MascotMood.DISAPPOINTED to listOf(
            "Ayaw ko ng sad face. Bawi bukas, okay?",
            "Woof… sana nag-review tayo kanina. Pero okay lang yan.",
            "Missed day? Noted. Pero hindi kita iiwan. Laban ulit!",
            "Isang maling sagot lang yan. Hindi ikaw yan. Next question!"
        ),
        MascotMood.CELEBRATING to listOf(
            "WOOOF! Bagong record! Sayaw si Bantay!",
            "Topnotcher behavior! Confetti! Dog treats for everyone!",
            "Grabe ka! Perfect score? Bantay is SHOOK.",
            "Tara, celebrate — tapos ulit bukas. Champion ka na!"
        ),
        MascotMood.ENCOURAGING to listOf(
            "Konti na lang! Malapit na! Woof woof!",
            "Kaya mo yan, pramis. Sabay tayo, hindi ka nag-iisa.",
            "Slow progress is still progress. Kahit si Bantay, natututo rin.",
            "One more page, one more card. Tapos pahinga. Deal?",
            "Future RMT ka. Act like it — with kindness sa sarili mo."
        ),
        MascotMood.DRAMATIC to listOf(
            "THE BOARD EXAM. *growl* …Joke lang. Kaya mo yan, master!",
            "If I can guard the house, you can guard your study time!",
            "Drama ni Bantay: 'Walang iwanan… sa review season!'",
            "This is my teleserye moment: 'Babalik ka ba sa review mo?' Charot."
        )
    )

    fun line(kind: MascotKind, mood: MascotMood, seed: Long = System.nanoTime()): String {
        val lines = when (kind) {
            MascotKind.CAT -> CAT[mood].orEmpty()
            MascotKind.DOG -> DOG[mood].orEmpty()
        }
        if (lines.isEmpty()) return ""
        return lines[Math.floorMod(seed, lines.size.toLong()).toInt()]
    }

    /** Greeting used on the home dashboard. */
    fun greeting(kind: MascotKind, hourOfDay: Int): String {
        val base = when {
            hourOfDay < 5 -> "Bakit gising ka pa? Pero sige, samahan kita."
            hourOfDay < 12 -> "Good morning, future RMT!"
            hourOfDay < 18 -> "Good afternoon! Review time na."
            else -> "Good evening! Konting review bago matulog."
        }
        val tail = if (kind == MascotKind.CAT) " Meow." else " Woof!"
        return base + tail
    }
}
