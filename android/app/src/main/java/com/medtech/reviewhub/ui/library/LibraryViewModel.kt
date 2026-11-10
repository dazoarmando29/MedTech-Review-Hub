package com.medtech.reviewhub.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtech.reviewhub.data.content.ContentAssetsLoader
import com.medtech.reviewhub.data.db.ReadingProgressEntity
import com.medtech.reviewhub.data.repo.StudyRepository
import com.medtech.reviewhub.domain.model.FormulaEntry
import com.medtech.reviewhub.domain.model.Lesson
import com.medtech.reviewhub.domain.model.Subject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Review library: subjects -> topics -> lessons from bundled content JSON, with
 * reading progress + bookmark + resume position persisted in Room.
 */
class LibraryViewModel(
    private val content: ContentAssetsLoader,
    private val studyRepository: StudyRepository
) : ViewModel() {

    data class LibraryState(
        val loading: Boolean = true,
        val subjects: List<Subject> = emptyList(),
        val searchQuery: String = "",
        val searchResults: List<Lesson> = emptyList(),
        val formulas: List<FormulaEntry> = emptyList(),
        val bookmarkedLessonIds: Set<String> = emptySet()
    )

    data class LessonState(
        val lesson: Lesson? = null,
        val progress: ReadingProgressEntity? = null,
        val bookmarked: Boolean = false
    )

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _lesson = MutableStateFlow(LessonState())
    val lesson: StateFlow<LessonState> = _lesson.asStateFlow()

    fun load() {
        viewModelScope.launch {
            content.load()
            _library.value = LibraryState(
                loading = false,
                subjects = content.subjects(),
                formulas = content.formulas(),
                bookmarkedLessonIds = studyRepository.bookmarkedLessonIds()
            )
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            content.load()
            _library.value = _library.value.copy(
                searchQuery = query,
                searchResults = content.searchLessons(query)
            )
        }
    }

    fun openLesson(lessonId: String) {
        viewModelScope.launch {
            content.load()
            val l = content.lesson(lessonId)
            val progress = studyRepository.lessonProgress(lessonId)
            _lesson.value = LessonState(
                lesson = l,
                progress = progress,
                bookmarked = studyRepository.bookmarkedLessonIds().contains(lessonId)
            )
        }
    }

    fun saveProgress(lessonId: String, percent: Float, offset: Int) {
        viewModelScope.launch {
            studyRepository.saveLessonProgress(lessonId, percent, offset)
            // Contextual encouragement after finishing a lesson (once per lesson).
            if (percent >= 0.95f && completedLessons.add(lessonId)) {
                com.medtech.reviewhub.ui.motivation.EncouragementBus.trigger(
                    com.medtech.reviewhub.domain.ContextKind.AFTER_LESSON
                )
            }
        }
    }

    private val completedLessons = mutableSetOf<String>()

    fun toggleBookmark(lessonId: String) {
        viewModelScope.launch {
            val newState = !_lesson.value.bookmarked
            studyRepository.setLessonBookmarked(lessonId, newState)
            _lesson.value = _lesson.value.copy(bookmarked = newState)
            _library.value = _library.value.copy(
                bookmarkedLessonIds = studyRepository.bookmarkedLessonIds()
            )
        }
    }

    suspend fun subjectName(id: String?): String = content.subjectName(id)

    suspend fun lessonIdsForPractice(lesson: Lesson): List<String> = lesson.practiceQuestionIds

    fun contentLoader(): ContentAssetsLoader = content
}
