package com.medtech.reviewhub.data.content

import android.content.res.AssetManager
import com.medtech.reviewhub.domain.model.ContentFlashcard
import com.medtech.reviewhub.domain.model.ContentManifest
import com.medtech.reviewhub.domain.model.FlashcardDeckFile
import com.medtech.reviewhub.domain.model.FormulaEntry
import com.medtech.reviewhub.domain.model.FormulaFile
import com.medtech.reviewhub.domain.model.GlossaryEntry
import com.medtech.reviewhub.domain.model.GlossaryFile
import com.medtech.reviewhub.domain.model.Lesson
import com.medtech.reviewhub.domain.model.MemeEntry
import com.medtech.reviewhub.domain.model.MemeFile
import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.domain.model.Subject
import com.medtech.reviewhub.domain.model.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads bundled review-library content from `assets/content/` (the same JSON
 * shape as shared/content/ — see shared/CONTENT_SCHEMA.md). The Gradle task
 * `copyContentAssets` keeps the assets in sync with shared/content when it
 * exists; otherwise the embedded sample content is used so the build always
 * works fully offline.
 */
class ContentAssetsLoader(private val assets: AssetManager) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = false }
    private val mutex = Mutex()

    private var manifest: ContentManifest? = null
    private var subjects: List<Subject> = emptyList()
    private var lessons: Map<String, Lesson> = emptyMap()
    private var questions: List<QuizQuestion> = emptyList()
    private var decks: List<FlashcardDeckFile> = emptyList()
    private var formulas: List<FormulaEntry> = emptyList()
    private var memes: List<MemeEntry> = emptyList()
    private var glossary: List<GlossaryEntry> = emptyList()

    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (manifest != null) return@withLock
            manifest = runCatching {
                json.decodeFromString<ContentManifest>(readText("content/manifest.json"))
            }.getOrElse { ContentManifest() }

            val subjectFiles = assets.list("content/subjects").orEmpty().filter { it.endsWith(".json") }
            subjects = subjectFiles.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<Subject>(readText("content/subjects/$file"))
                }.getOrNull()
            }

            val loadedLessons = mutableMapOf<String, Lesson>()
            val lessonRoots = assets.list("content/lessons").orEmpty()
            for (dir in lessonRoots) {
                val files = assets.list("content/lessons/$dir").orEmpty().filter { it.endsWith(".json") }
                for (file in files) {
                    runCatching {
                        json.decodeFromString<Lesson>(readText("content/lessons/$dir/$file"))
                    }.getOrNull()?.let { loadedLessons[it.id] = it }
                }
            }
            lessons = loadedLessons

            questions = subjectFiles.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<List<QuizQuestion>>(readText("content/questions/$file"))
                }.getOrNull()
            }.flatten()

            decks = assets.list("content/flashcards").orEmpty().filter { it.endsWith(".json") }.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<FlashcardDeckFile>(readText("content/flashcards/$file"))
                }.getOrNull()
            }

            formulas = runCatching {
                json.decodeFromString<FormulaFile>(readText("content/formulas.json")).entries
            }.getOrDefault(emptyList())
            memes = runCatching {
                json.decodeFromString<MemeFile>(readText("content/memes.json")).entries
            }.getOrDefault(emptyList())
            glossary = runCatching {
                json.decodeFromString<GlossaryFile>(readText("content/glossary.json")).entries
            }.getOrDefault(emptyList())
        }
    }

    private fun readText(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    suspend fun subjects(): List<Subject> { load(); return subjects }
    suspend fun manifest(): ContentManifest { load(); return manifest ?: ContentManifest() }

    suspend fun subject(id: String): Subject? = subjects().firstOrNull { it.id == id }

    suspend fun topics(subjectId: String): List<Topic> = subject(subjectId)?.topics ?: emptyList()

    suspend fun lesson(id: String): Lesson? { load(); return lessons[id] }

    suspend fun lessonsFor(subjectId: String, topicId: String? = null): List<Lesson> {
        load()
        return lessons.values.filter {
            it.subjectId == subjectId && (topicId == null || it.topicId == topicId)
        }.sortedBy { it.title }
    }

    suspend fun allLessons(): List<Lesson> { load(); return lessons.values.sortedBy { it.id } }

    suspend fun searchLessons(query: String): List<Lesson> {
        load()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return lessons.values.filter { l ->
            l.title.lowercase().contains(q) ||
                l.summary.lowercase().contains(q) ||
                l.keywords.any { it.lowercase().contains(q) }
        }.sortedBy { it.title }
    }

    suspend fun questions(subjectId: String? = null, topicId: String? = null, limit: Int = Int.MAX_VALUE): List<QuizQuestion> {
        load()
        return questions.asSequence()
            .filter { subjectId == null || it.subjectId == subjectId }
            .filter { topicId == null || it.topicId == topicId }
            .take(limit)
            .toList()
    }

    suspend fun questionById(id: String): QuizQuestion? { load(); return questions.firstOrNull { it.id == id } }

    suspend fun allQuestions(): List<QuizQuestion> { load(); return questions }

    suspend fun decks(): List<FlashcardDeckFile> { load(); return decks }

    suspend fun contentFlashcard(id: String): ContentFlashcard? {
        load()
        return decks.asSequence().flatMap { it.cards.asSequence() }.firstOrNull { it.id == id }
    }

    suspend fun formulas(): List<FormulaEntry> { load(); return formulas }
    suspend fun memes(): List<MemeEntry> { load(); return memes }
    suspend fun glossary(): List<GlossaryEntry> { load(); return glossary }

    suspend fun subjectName(id: String?): String = id?.let { subject(it)?.shortName ?: it } ?: ""
}
