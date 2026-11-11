package io.medtech.backend.service

import io.medtech.backend.ai.AiCompletion
import io.medtech.backend.ai.AiProviders
import io.medtech.backend.ai.AiTask
import io.medtech.backend.config.AppConfig
import io.medtech.backend.db.nowMillis
import io.medtech.backend.domain.AiCitation
import io.medtech.backend.domain.AiEnvelope
import io.medtech.backend.domain.AiUsage
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.AppJson
import io.medtech.backend.repository.AiContentRepository
import io.medtech.backend.repository.HabitDiaryRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

class AiService(
    private val config: AppConfig,
    private val aiContent: AiContentRepository = AiContentRepository(),
    private val habitDiary: HabitDiaryRepository = HabitDiaryRepository(),
) {
    private val provider = AiProviders.create(config)
    private val cache = object : LinkedHashMap<String, AiEnvelope>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AiEnvelope>): Boolean = size > 256
    }

    val isConfigured: Boolean get() = provider != null

    private fun requireProvider() {
        if (provider == null) throw ApiException.aiUnavailable()
    }

    suspend fun summarize(userId: String, text: String, mode: String, pdfId: String?, page: Int?): AiEnvelope {
        requireProvider()
        val prompt = """
            Summarize the document text below for a medical laboratory science student.
            Mode: $mode.

            CITATION RULES (mandatory):
            - Every factual statement must carry a citation like [cite]exact quote[/cite] taken from the document text.
            - If the document does not support a statement, write exactly: not found in document.
            - Return strict JSON: {"summary": "...", "keyPoints": ["..."]}

            Document text:
            ${text.take(12000)}
        """.trimIndent()
        val envelope = execute(userId, AiTask.SUMMARIZE, prompt, pdfId, page, text)
        return envelope
    }

    suspend fun flashcards(userId: String, text: String, count: Int): AiEnvelope {
        requireProvider()
        val prompt = """
            Create at most $count study flashcards from the document text below.
            CITATION RULES (mandatory): every answer must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"flashcards":[{"front":"...","back":"...","tags":["..."]}]}
            Document text:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.FLASHCARDS, prompt, null, null, text)
    }

    suspend fun quiz(userId: String, text: String, count: Int): AiEnvelope {
        requireProvider()
        val prompt = """
            Write at most $count multiple-choice questions (4 choices, one best answer) from the document text below.
            CITATION RULES (mandatory): every explanation must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"questions":[{"stem":"...","choices":[{"id":"A","text":"..."}],"correctChoiceId":"A","rationales":{"A":"..."},"explanation":"..."}]}
            Document text:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.QUIZ, prompt, null, null, text)
    }

    suspend fun explain(userId: String, text: String, style: String): AiEnvelope {
        requireProvider()
        val styleGuide = when (style) {
            "TAGLISH" -> "Use Taglish (natural Tagalog-English mix) for friendly asides while keeping medical terms in English."
            "SIMPLE_ENGLISH" -> "Use simple English."
            "FUNNY" -> "Explain in a funny, meme-like way - like the wittiest reviewer - but keep every fact scientifically accurate and end with one memory hook."
            "TAGLISH_FUNNY" -> "Explain in funny Taglish (natural Tagalog-English mix) like a funny study buddy - so easy that even someone who is NOT a medtech student can understand. Keep every fact accurate. End with 1-2 practical, kind study tips (what to review first)."
            else -> "Use the style: $style."
        }
        val prompt = """
            Explain the document text below to a student. $styleGuide
            CITATION RULES (mandatory): every factual statement must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"explanation":"..."}
            Document text:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.EXPLAIN, prompt, null, null, text)
    }

    suspend fun outline(userId: String, text: String): AiEnvelope {
        requireProvider()
        val prompt = """
            Build a study outline of the document text below.
            CITATION RULES (mandatory): every bullet must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"outline":[{"heading":"...","bullets":["..."]}]}
            Document text:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.OUTLINE, prompt, null, null, text)
    }

    suspend fun keyterms(userId: String, text: String): AiEnvelope {
        requireProvider()
        val prompt = """
            Extract key terms and one-line definitions from the document text below.
            CITATION RULES (mandatory): every definition must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"terms":[{"term":"...","definition":"..."}]}
            Document text:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.KEYTERMS, prompt, null, null, text)
    }

    suspend fun diarySummary(userId: String, days: Int): AiEnvelope {
        requireProvider()
        val entries = habitDiary.diaryTexts(userId, limit = 50)
            .sortedByDescending { it.date }
            .take(days.coerceIn(1, 60))
        if (entries.isEmpty()) {
            return AiEnvelope(
                result = buildJsonObject { put("summary", "No diary entries found in the selected range.") },
                citations = emptyList(),
                warnings = listOf("not found in document"),
                usage = AiUsage(cached = false, tokensUsed = 0),
            )
        }
        val text = entries.joinToString("\n") { "${it.date}: [${it.mood}] ${it.text}" }
        val prompt = """
            Summarize the student's diary entries below: study patterns, wins, and struggles.
            CITATION RULES (mandatory): every observation must include [cite]exact quote[/cite], or state 'not found in document'.
            Return strict JSON: {"summary":"..."}
            Diary entries:
            ${text.take(12000)}
        """.trimIndent()
        return execute(userId, AiTask.DIARY_SUMMARY, prompt, null, null, text)
    }

    private suspend fun execute(
        userId: String,
        task: AiTask,
        prompt: String,
        pdfId: String?,
        page: Int?,
        sourceText: String,
    ): AiEnvelope {
        val activeProvider = provider ?: throw ApiException.aiUnavailable()
        val requestHash = sha256("${task.name}:$prompt")
        val cacheKey = "$userId:${task.name}:$requestHash"
        synchronized(cache) { cache[cacheKey] }?.let { return it.copy(usage = it.usage.copy(cached = true)) }

        val dayStart = nowMillis() - (nowMillis() % (24 * 60 * 60 * 1000))
        val usedToday = aiContent.tokensUsedToday(userId, dayStart)
        if (usedToday >= config.aiDailyTokenBudget) {
            throw ApiException.rateLimited("Daily AI token budget of ${config.aiDailyTokenBudget} tokens exceeded")
        }

        val system = """
            You are a careful medical laboratory science study assistant.
            You ONLY use the provided document. Every fact must include [cite]exact quote[/cite] or the phrase 'not found in document'.
            Answer with strict JSON only, no markdown fences.
        """.trimIndent()

        var completion: AiCompletion? = null
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                completion = activeProvider.complete(task, system, prompt)
                return@repeat
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) delay(if (attempt == 0) 500L else 1500L)
            }
        }
        val done = completion ?: throw ApiException.internal("AI provider call failed: ${lastError?.message}")

        val result = parseResult(done.text)
        val citation = AiCitation(
            pdfId = pdfId,
            page = page,
            quote = sourceText.trim().replace(Regex("\\s+"), " ").take(160),
        )
        val warnings = mutableListOf<String>()
        if (!done.text.contains("[cite]") && !done.text.contains("not found in document")) {
            warnings += "Flagged: response lacks document citations; verify every fact before use"
        }
        warnings += "AI-generated content may be incomplete or wrong; verify against your references"

        val envelope = AiEnvelope(
            result = result,
            citations = if (sourceText.isBlank()) emptyList() else listOf(citation),
            warnings = warnings,
            usage = AiUsage(cached = false, tokensUsed = done.inputTokens + done.outputTokens),
        )
        synchronized(cache) { cache[cacheKey] = envelope }
        aiContent.insert(
            userId = userId,
            task = task.name,
            requestHash = requestHash,
            responseJson = done.text,
            inputTokens = done.inputTokens,
            outputTokens = done.outputTokens,
        )
        return envelope
    }

    private fun parseResult(text: String): JsonObject {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { AppJson.parseToJsonElement(cleaned) as? JsonObject }.getOrNull()
            ?: buildJsonObject { put("text", text) }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
