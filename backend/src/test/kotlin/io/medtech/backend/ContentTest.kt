package io.medtech.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.FormulaEntry
import io.medtech.backend.domain.Lesson
import io.medtech.backend.domain.LessonSummary
import io.medtech.backend.domain.MemeEntry
import io.medtech.backend.domain.QuizQuestion
import io.medtech.backend.domain.SubjectDto
import io.medtech.backend.domain.decodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentTest {

    @Test
    fun `content endpoints serve bundled sample lessons and questions`() = withApp {
        val subjectsResp = client.get("/api/v1/content/subjects")
        assertEquals(HttpStatusCode.OK, subjectsResp.status)
        val subjects: List<SubjectDto> = decodeJson(subjectsResp.text())
        assertEquals(2, subjects.size, "bundled sample ships 2 subjects")
        val bb = subjects.first { it.id == "blood-banking" }
        assertTrue(bb.topics.any { it.id == "abo-system" }, "blood-banking must expose abo-system topic")
        assertTrue(subjects.any { it.id == "hematology" })
        assertTrue(bb.blueprintNote.contains("approximate") && bb.blueprintNote.contains("official"), "weights must be honestly labeled")

        val lessonsResp = client.get("/api/v1/content/lessons")
        assertEquals(HttpStatusCode.OK, lessonsResp.status)
        val lessons: List<LessonSummary> = decodeJson(lessonsResp.text())
        assertEquals(2, lessons.size)
        assertTrue(lessons.any { it.id == "blood-banking/abo-basics" })
        assertTrue(lessons.any { it.id == "hematology/cbc-basics" })

        val lessonResp = client.get("/api/v1/content/lessons/blood-banking/abo-basics")
        assertEquals(HttpStatusCode.OK, lessonResp.status)
        val lesson: Lesson = decodeJson(lessonResp.text())
        assertEquals("blood-banking/abo-basics", lesson.id)
        assertTrue(lesson.quickReview.highYield.isNotEmpty(), "lesson must include quickReview.highYield")
        assertTrue(lesson.quickReview.mustKnowTable.rows.isNotEmpty(), "lesson must include mustKnowTable")
        assertTrue(lesson.deepStudy.examTraps.isNotEmpty(), "lesson must include examTraps")
        assertTrue(lesson.references.isNotEmpty(), "every lesson must list a real reference")

        // Search filters
        val searchResp = client.get("/api/v1/content/lessons?q=ABO")
        assertEquals(HttpStatusCode.OK, searchResp.status)
        val search: List<LessonSummary> = decodeJson(searchResp.text())
        assertTrue(search.any { it.id == "blood-banking/abo-basics" })
        val subjectFilter = client.get("/api/v1/content/lessons?subject=hematology")
        val filtered: List<LessonSummary> = decodeJson(subjectFilter.text())
        assertEquals(1, filtered.size)

        val questionsResp = client.get("/api/v1/content/questions")
        assertEquals(HttpStatusCode.OK, questionsResp.status)
        val questions: List<QuizQuestion> = decodeJson(questionsResp.text())
        assertEquals(6, questions.size, "bundled sample ships 6 questions")
        questions.forEach { q ->
            assertTrue(q.correctChoiceId != null, "${q.id} must expose correctChoiceId")
            assertEquals(4, q.choices.size, "${q.id} must have 4 choices")
            val rationales = q.rationales ?: error("${q.id} must expose rationales")
            q.choices.forEach { choice ->
                assertTrue(rationales[choice.id] != null, "${q.id} must have a rationale for choice ${choice.id}")
            }
            assertTrue(q.misconception != null && q.misconception!!.isNotBlank(), "${q.id} needs a misconception note")
            assertTrue(q.reference != null && q.reference!!.isNotBlank(), "${q.id} needs a reference")
        }

        val limited = client.get("/api/v1/content/questions?subject=blood-banking&limit=2")
        val limitedList: List<QuizQuestion> = decodeJson(limited.text())
        assertEquals(2, limitedList.size)
        assertTrue(limitedList.all { it.subjectId == "blood-banking" })

        val formulasResp = client.get("/api/v1/content/formulas")
        assertEquals(HttpStatusCode.OK, formulasResp.status)
        val formulas: List<FormulaEntry> = decodeJson(formulasResp.text())
        assertTrue(formulas.isNotEmpty(), "bundled formulas must be served")

        val memesResp = client.get("/api/v1/content/memes")
        assertEquals(HttpStatusCode.OK, memesResp.status)
        val memes: List<MemeEntry> = decodeJson(memesResp.text())
        assertTrue(memes.isNotEmpty(), "bundled memes must be served")
        assertTrue(memes.all { it.scientificFact.isNotBlank() }, "every meme must carry a scientificFact")

        val health = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.text().contains("\"status\":\"ok\""), health.text())
    }
}
