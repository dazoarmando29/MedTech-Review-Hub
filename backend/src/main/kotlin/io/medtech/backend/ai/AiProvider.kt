package io.medtech.backend.ai

import io.medtech.backend.config.AppConfig

enum class AiTask(val label: String) {
    SUMMARIZE("summarize"),
    FLASHCARDS("flashcards"),
    QUIZ("quiz"),
    EXPLAIN("explain"),
    OUTLINE("outline"),
    KEYTERMS("keyterms"),
    DIARY_SUMMARY("diary-summary"),
}

data class AiCompletion(
    val text: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)

interface AiProvider {
    val name: String
    suspend fun complete(task: AiTask, system: String, prompt: String): AiCompletion
}

/**
 * Deterministic offline provider used when AI_PROVIDER=LOCAL (no external keys).
 * Returns strict JSON payloads matching what AiService expects so clients can
 * develop against the full AI flow without any cloud key.
 */
class LocalStubProvider : AiProvider {
    override val name: String = "LOCAL"

    override suspend fun complete(task: AiTask, system: String, prompt: String): AiCompletion {
        val text = when (task) {
            AiTask.SUMMARIZE -> """
                {"summary":"Key points from the provided document. [cite]${snippet(prompt)}[/cite] Details not stated in the document are marked not found in document.","keyPoints":["Main idea [cite]${snippet(prompt)}[/cite]"]}
            """.trimIndent()
            AiTask.FLASHCARDS -> """
                {"flashcards":[{"front":"What is the main concept in this passage? [cite]${snippet(prompt)}[/cite]","back":"See the cited passage. [cite]${snippet(prompt)}[/cite]","tags":["generated"]}]}
            """.trimIndent()
            AiTask.QUIZ -> """
                {"questions":[{"stem":"Based on the document, which statement is supported? [cite]${snippet(prompt)}[/cite]","choices":[{"id":"A","text":"Supported statement"},{"id":"B","text":"Not supported"},{"id":"C","text":"Not mentioned"},{"id":"D","text":"Opposite"}],"correctChoiceId":"A","rationales":{"A":"Supported by the cited text.","B":"Contradicted by the document.","C":"The document does not state this.","D":"The document says the opposite."},"explanation":"Only claims present in the document are used."}]}
            """.trimIndent()
            AiTask.EXPLAIN -> """
                {"explanation":"In simple terms: ${snippet(prompt)} [cite]${snippet(prompt)}[/cite] Anything beyond the document is marked not found in document."}
            """.trimIndent()
            AiTask.OUTLINE -> """
                {"outline":[{"heading":"Overview","bullets":["${snippet(prompt)} [cite]${snippet(prompt)}[/cite]"]}]}
            """.trimIndent()
            AiTask.KEYTERMS -> """
                {"terms":[{"term":"key term","definition":"definition grounded in the document [cite]${snippet(prompt)}[/cite]"}]}
            """.trimIndent()
            AiTask.DIARY_SUMMARY -> """
                {"summary":"You studied consistently and revisited weak topics. Anything not present in your diary entries is marked not found in document."}
            """.trimIndent()
        }
        return AiCompletion(text = text, inputTokens = 200, outputTokens = 120)
    }

    private fun snippet(prompt: String): String {
        val cleaned = prompt.replace(Regex("\\s+"), " ").trim()
        return if (cleaned.length <= 120) cleaned else cleaned.take(120) + "..."
    }
}

object AiProviders {
    fun create(config: AppConfig): AiProvider? {
        return when (config.aiProvider.lowercase()) {
            "none", "", "disabled" -> null
            "local" -> LocalStubProvider()
            "openai" -> {
                val key = System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() } ?: return null
                OpenAiProvider(key, System.getenv("OPENAI_MODEL") ?: "gpt-4o-mini")
            }
            "anthropic" -> {
                val key = System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() } ?: return null
                AnthropicProvider(key, System.getenv("ANTHROPIC_MODEL") ?: "claude-3-5-haiku-latest")
            }
            "gemini" -> {
                val key = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() } ?: return null
                GeminiProvider(key, System.getenv("GEMINI_MODEL") ?: "gemini-1.5-flash")
            }
            else -> null
        }
    }
}
