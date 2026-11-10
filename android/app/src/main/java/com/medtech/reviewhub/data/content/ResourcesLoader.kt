package com.medtech.reviewhub.data.content

import android.content.res.AssetManager
import com.medtech.reviewhub.domain.model.ExamInfoEntry
import com.medtech.reviewhub.domain.model.OfficialLink
import com.medtech.reviewhub.domain.model.ReadingListEntry
import com.medtech.reviewhub.domain.model.ResourcesFile
import com.medtech.reviewhub.domain.model.ReviewCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the Philippine ASCPi resources file from `assets/content/resources.json`
 * (copied from shared/content/resources.json by the `copyContentAssets` Gradle
 * task). Missing or malformed files resolve to [ResourcesFile.EMPTY] and the
 * screen shows an empty state - the app never invents resource content.
 */
class ResourcesLoader(private val assets: AssetManager) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cached: ResourcesFile? = null

    suspend fun load(): ResourcesFile = withContext(Dispatchers.IO) {
        cached?.let { return@withContext it }
        val parsed = runCatching {
            val text = assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<ResourcesFile>(text)
        }.getOrNull() ?: ResourcesFile.EMPTY
        cached = parsed
        parsed
    }

    companion object {
        const val ASSET_PATH = "content/resources.json"
    }
}
