package io.medtech.backend.domain

import kotlinx.serialization.Serializable

@Serializable
data class TopicDto(
    val id: String,
    val name: String = "",
    val lessonIds: List<String> = emptyList(),
)

@Serializable
data class SubjectDto(
    val id: String,
    val name: String,
    val shortName: String = "",
    val description: String = "",
    val blueprintNote: String = "",
    val colorToken: String = "gray",
    val topics: List<TopicDto> = emptyList(),
)

@Serializable
data class LessonSummary(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val summary: String = "",
    val readingMinutes: Int = 10,
    val difficulty: String = "INTERMEDIATE",
    val keywords: List<String> = emptyList(),
)

@Serializable
data class MustKnowTable(
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)

@Serializable
data class QuickReview(
    val highYield: List<String> = emptyList(),
    val mustKnowTable: MustKnowTable = MustKnowTable(),
    val mnemonic: String? = null,
)

@Serializable
data class LessonSection(
    val heading: String,
    val body: String,
    val diagram: String? = null,
)

@Serializable
data class ComparisonTable(
    val title: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)

@Serializable
data class DeepStudy(
    val introduction: String = "",
    val sections: List<LessonSection> = emptyList(),
    val clinicalSignificance: String = "",
    val labInterpretation: String = "",
    val comparisonTables: List<ComparisonTable> = emptyList(),
    val examTraps: List<String> = emptyList(),
    val workedExample: String? = null,
)

@Serializable
data class MemoryHook(
    val type: String = "MNEMONIC",
    val text: String = "",
    val funny: String? = null,
)

@Serializable
data class Reference(
    val title: String,
    val detail: String = "",
    val url: String? = null,
)

@Serializable
data class Lesson(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val summary: String = "",
    val readingMinutes: Int = 10,
    val difficulty: String = "INTERMEDIATE",
    val keywords: List<String> = emptyList(),
    val objectives: List<String> = emptyList(),
    val quickReview: QuickReview = QuickReview(),
    val deepStudy: DeepStudy = DeepStudy(),
    val memoryHooks: List<MemoryHook> = emptyList(),
    val practiceQuestionIds: List<String> = emptyList(),
    val relatedFlashcardIds: List<String> = emptyList(),
    val followUp: List<String> = emptyList(),
    val references: List<Reference> = emptyList(),
    val editorialStatus: String = "PUBLISHED",
    val reviewedAt: String? = null,
    val reviewerNote: String? = null,
) {
    fun toSummary(): LessonSummary = LessonSummary(
        id = id,
        subjectId = subjectId,
        topicId = topicId,
        title = title,
        summary = summary,
        readingMinutes = readingMinutes,
        difficulty = difficulty,
        keywords = keywords,
    )
}

@Serializable
data class FormulaVariable(val name: String, val unit: String = "")

@Serializable
data class FormulaEntry(
    val id: String,
    val title: String,
    val formula: String,
    val variables: List<FormulaVariable> = emptyList(),
    val example: String = "",
    val caveat: String = "",
    val reference: String = "",
)

@Serializable
data class MemeEntry(
    val id: String,
    val type: String,
    val subjectId: String? = null,
    val title: String = "",
    val body: String = "",
    val punchline: String = "",
    val scientificFact: String = "",
    val lessonId: String? = null,
)

@Serializable
data class GlossaryTerm(
    val id: String,
    val term: String,
    val definition: String,
    val subjectId: String? = null,
    val source: String = "",
)

@Serializable
data class ContentManifest(
    val version: Int = 1,
    val generatedAt: String? = null,
    val note: String? = null,
    val subjects: List<ManifestSubject> = emptyList(),
    val questionBanks: List<String> = emptyList(),
    val flashcardDecks: List<String> = emptyList(),
)

@Serializable
data class ManifestSubject(val id: String, val topics: List<TopicDto> = emptyList())
