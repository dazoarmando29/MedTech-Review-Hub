package com.medtech.reviewhub.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// DTOs for shared/api/API.md. Unknown fields are ignored by Moshi on read;
// the backend likewise ignores unknown fields.
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = false)
data class ApiErrorBody(val error: ApiError? = null)

@JsonClass(generateAdapter = false)
data class ApiError(val code: String? = null, val message: String? = null)

@JsonClass(generateAdapter = false)
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String
)

@JsonClass(generateAdapter = false)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = false)
data class AuthResponse(
    val user: UserDto? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null
)

@JsonClass(generateAdapter = false)
data class UserDto(
    val id: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = false)
data class ProfileDto(
    val examTarget: String? = null,
    val targetExamDate: String? = null,
    val knowledgeLevel: String? = null,
    val strongestSubjects: List<String>? = null,
    val weakestSubjects: List<String>? = null,
    val minutesPerDay: Int? = null,
    val preferredSchedule: Map<String, List<List<Int>>>? = null,
    val learningPreferences: List<String>? = null,
    val explanationStyle: String? = null,
    val notificationsEnabled: Boolean? = null,
    val focusFeatureEnabled: Boolean? = null,
    val onboardingCompleted: Boolean? = null
)

@JsonClass(generateAdapter = false)
data class SessionRequest(
    val id: String,
    val startedAt: String,
    val endedAt: String? = null,
    val kind: String,
    val subjectId: String? = null,
    val minutes: Int = 0,
    val itemsCompleted: Int = 0
)

@JsonClass(generateAdapter = false)
data class FlashcardScheduleDto(
    val state: String? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueAt: String? = null,
    val lastReviewAt: String? = null,
    val reps: Int? = null,
    val lapses: Int? = null
)

@JsonClass(generateAdapter = false)
data class FlashcardHistoryDto(val at: String? = null, val rating: String? = null, val intervalDays: Double? = null)

@JsonClass(generateAdapter = false)
data class FlashcardDto(
    val id: String? = null,
    val deckId: String? = null,
    val front: String? = null,
    val back: String? = null,
    val type: String? = null,
    val tags: List<String>? = null,
    val subjectId: String? = null,
    val topicId: String? = null,
    val difficulty: Int? = null,
    val note: String? = null,
    val favorite: Boolean? = null,
    val schedule: FlashcardScheduleDto? = null,
    val history: List<FlashcardHistoryDto>? = null
)

@JsonClass(generateAdapter = false)
data class ReviewRequest(val rating: String, val reviewedAt: String)

@JsonClass(generateAdapter = false)
data class QuizStartRequest(val mode: String, val subjectId: String? = null, val topicId: String? = null, val questionCount: Int? = null)

@JsonClass(generateAdapter = false)
data class QuizAnswerRequest(val questionId: String, val chosenChoiceId: String, val timeSeconds: Int? = null)

@JsonClass(generateAdapter = false)
data class AiRequest(
    val text: String,
    val mode: String? = null,
    val style: String? = null,
    val pdfId: String? = null,
    val page: Int? = null
)

@JsonClass(generateAdapter = false)
data class AiResponse(
    val result: Map<String, Any?>? = null,
    val citations: List<Map<String, Any?>>? = null,
    val warnings: List<String>? = null,
    val usage: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = false)
data class HabitRequest(val id: String? = null, val name: String, val targetPerDay: Int, val kind: String)

@JsonClass(generateAdapter = false)
data class HabitLogRequest(val habitId: String, val date: String, val amount: Int)

@JsonClass(generateAdapter = false)
data class DiaryRequest(val date: String, val mood: String, val text: String, val tags: List<String>? = null)

@JsonClass(generateAdapter = false)
data class StreakResponse(val current: Int? = null, val best: Int? = null, val freezeDaysLeft: Int? = null)

@JsonClass(generateAdapter = false)
data class HealthResponse(val status: String? = null, val version: String? = null, val database: String? = null)

// ---- GitHub Releases (update checker) -----------------------------------

@JsonClass(generateAdapter = false)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)
