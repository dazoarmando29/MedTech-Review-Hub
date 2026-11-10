package io.medtech.backend.content

import io.medtech.backend.domain.AppJson
import io.medtech.backend.domain.ContentManifest
import io.medtech.backend.domain.FormulaEntry
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.MemeEntry
import io.medtech.backend.domain.GlossaryTerm
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.SubjectDto
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class CardSourceRef(val kind: String = "MANUAL", val lessonId: String? = null)

@Serializable
data class ContentDeckCard(
    val id: String,
    val type: String = "BASIC",
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    val difficulty: Int = 1,
    val note: String = "",
    val source: CardSourceRef = CardSourceRef(),
)

@Serializable
data class ContentDeck(
    val id: String,
    val title: String,
    val subjectId: String? = null,
    val cards: List<ContentDeckCard> = emptyList(),
)

/**
 * In-memory review library. Loads shared/content/ (CONTENT_DIR) when present,
 * otherwise the bundled fallback sample in resources/content/.
 */
class ContentStore(
    val source: String,
    val manifest: ContentManifest,
    val subjectMeta: Map<String, SubjectDto>,
    val lessons: Map<String, Lesson>,
    val questions: List<QuizQuestion>,
    val decks: List<ContentDeck>,
    val formulas: List<FormulaEntry>,
    val memes: List<MemeEntry>,
    val glossary: List<GlossaryTerm>,
) {
    fun subjects(): List<SubjectDto> = manifest.subjects.map { manifestSubject ->
        subjectMeta[manifestSubject.id] ?: SubjectDto(id = manifestSubject.id, name = manifestSubject.id, topics = manifestSubject.topics)
    }

    fun lesson(id: String): Lesson? = lessons[id]

    fun lessonSummaries(): List<Lesson> = lessons.values.sortedBy { it.id }

    fun questions(subject: String? = null, topic: String? = null): List<QuizQuestion> =
        questions.filter { (subject == null || it.subjectId == subject) && (topic == null || it.topicId == topic) }

    companion object {
        fun load(contentDir: String?): ContentStore {
            val dir = resolveContentDir(contentDir)
            return if (dir != null) loadFromDirectory(dir) else loadFromClasspath()
        }

        private fun resolveContentDir(contentDir: String?): File? {
            val candidates = listOfNotNull(
                contentDir?.takeIf { it.isNotBlank() },
                "shared/content",
                "../shared/content",
                "../../shared/content",
            )
            for (candidate in candidates) {
                val dir = File(candidate).absoluteFile.normalize()
                if (File(dir, "manifest.json").isFile) return dir
            }
            return null
        }

        private fun read(dir: File, relative: String): String? {
            val file = File(dir, relative)
            return if (file.isFile) file.readText(Charsets.UTF_8) else null
        }

        private fun readResource(relative: String): String? =
            ContentStore::class.java.classLoader.getResourceAsStream(relative)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

        private fun loadFromDirectory(dir: File): ContentStore {
            val reader: (String) -> String? = { path -> read(dir, path) }
            return build("shared-content:${dir.absolutePath}", reader)
        }

        private fun loadFromClasspath(): ContentStore {
            val reader: (String) -> String? = { path -> readResource("content/$path") }
            return build("bundled:resources/content", reader)
        }

        private fun build(source: String, read: (String) -> String?): ContentStore {
            val manifestText = read("manifest.json")
                ?: error("content manifest.json not found")
            val manifest = AppJson.decodeFromString(ContentManifest.serializer(), manifestText)

            val subjectMeta = mutableMapOf<String, SubjectDto>()
            for (subject in manifest.subjects) {
                val text = read("subjects/${subject.id}.json") ?: continue
                val meta = AppJson.decodeFromString(SubjectDto.serializer(), text)
                subjectMeta[meta.id] = meta.copy(topics = if (meta.topics.isNotEmpty()) meta.topics else subject.topics)
            }

            val lessons = mutableMapOf<String, Lesson>()
            for (subject in manifest.subjects) {
                for (topic in subject.topics) {
                    for (lessonId in topic.lessonIds) {
                        val fileName = lessonId.substringAfter('/', lessonId)
                        val text = read("lessons/${subject.id}/$fileName.json") ?: continue
                        val lesson = AppJson.decodeFromString(Lesson.serializer(), text)
                        lessons[lesson.id] = lesson
                    }
                }
            }

            val questions = mutableListOf<QuizQuestion>()
            for (bank in manifest.questionBanks) {
                val text = read("questions/$bank.json") ?: continue
                questions += AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(QuizQuestion.serializer()), text)
            }

            val decks = mutableListOf<ContentDeck>()
            for (deckId in manifest.flashcardDecks) {
                val text = read("flashcards/$deckId.json") ?: continue
                // shared/content stores flashcard files as arrays of decks; the
                // bundled sample stores one deck per file. Accept both.
                val parsed = runCatching {
                    AppJson.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(ContentDeck.serializer()),
                        text,
                    )
                }.getOrElse {
                    listOf(AppJson.decodeFromString(ContentDeck.serializer(), text))
                }
                decks += parsed
            }

            val formulas = read("formulas.json")?.let {
                AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(FormulaEntry.serializer()), it)
            } ?: emptyList()
            val memes = read("memes.json")?.let {
                AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MemeEntry.serializer()), it)
            } ?: emptyList()
            val glossary = read("glossary.json")?.let {
                AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GlossaryTerm.serializer()), it)
            } ?: emptyList()

            return ContentStore(
                source = source,
                manifest = manifest,
                subjectMeta = subjectMeta,
                lessons = lessons,
                questions = questions,
                decks = decks,
                formulas = formulas,
                memes = memes,
                glossary = glossary,
            )
        }
    }
}
