package com.medtech.reviewhub.data.content

import android.content.res.AssetManager
import com.medtech.reviewhub.domain.ContextKind
import com.medtech.reviewhub.domain.MotivationBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MotivationJson(
    val note: String = "",
    val dailyQuotes: List<String> = emptyList(),
    val contextQuotes: Map<String, List<String>> = emptyMap(),
    val mascotLines: Map<String, List<String>> = emptyMap()
)

/**
 * Loads shared/content/motivation.json from assets (kept in sync by the
 * `copyContentAssets` Gradle task; an embedded fallback keeps the build and
 * the app working when the file is missing). Quotes are original unattributed
 * statements - never invented celebrity attributions.
 */
object MotivationStore {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var bank: MotivationBank? = null

    suspend fun load(assets: AssetManager): MotivationBank = withContext(Dispatchers.IO) {
        bank ?: runCatching {
            val text = assets.open("content/motivation.json").bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<MotivationJson>(text)
            MotivationBank(
                dailyQuotes = parsed.dailyQuotes,
                contextQuotes = parsed.contextQuotes,
                mascotLines = parsed.mascotLines
            )
        }.getOrDefault(MotivationBank()).let {
            val safe = if (it.dailyQuotes.isEmpty()) com.medtech.reviewhub.domain.Motivation.FALLBACK else it
            bank = safe
            safe
        }
    }

    fun bankOrNull(): MotivationBank? = bank

    fun bankOrFallback(): MotivationBank = bank ?: com.medtech.reviewhub.domain.Motivation.FALLBACK

    /** Test seam. */
    fun setBankForTesting(b: MotivationBank?) {
        bank = b
    }
}
