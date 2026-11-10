package com.medtech.reviewhub.data.network

import com.medtech.reviewhub.data.network.dto.AiRequest
import com.medtech.reviewhub.data.network.dto.AiResponse
import com.medtech.reviewhub.data.network.dto.AuthResponse
import com.medtech.reviewhub.data.network.dto.DiaryRequest
import com.medtech.reviewhub.data.network.dto.FlashcardDto
import com.medtech.reviewhub.data.network.dto.HabitLogRequest
import com.medtech.reviewhub.data.network.dto.HabitRequest
import com.medtech.reviewhub.data.network.dto.HealthResponse
import com.medtech.reviewhub.data.network.dto.LoginRequest
import com.medtech.reviewhub.data.network.dto.ProfileDto
import com.medtech.reviewhub.data.network.dto.RegisterRequest
import com.medtech.reviewhub.data.network.dto.ReviewRequest
import com.medtech.reviewhub.data.network.dto.SessionRequest
import com.medtech.reviewhub.data.network.dto.StreakResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface mirroring shared/api/API.md (v1). All calls are optional:
 * the app is fully offline-first and only talks to the network when the user
 * configures an API base URL in Settings.
 */
interface ApiService {

    // 1. Auth
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: Map<String, String>): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    // 2. Users & profile
    @GET("me")
    suspend fun me(@Header("Authorization") bearer: String): Response<Map<String, Any?>>

    @PUT("me/profile")
    suspend fun putProfile(@Header("Authorization") bearer: String, @Body body: ProfileDto): Response<Unit>

    // 4. Study sessions
    @POST("sessions")
    suspend fun createSession(@Header("Authorization") bearer: String, @Body body: SessionRequest): Response<Unit>

    // 5. Flashcards
    @POST("flashcards")
    suspend fun createFlashcard(@Header("Authorization") bearer: String, @Body body: FlashcardDto): Response<FlashcardDto>

    @PATCH("flashcards/{id}")
    suspend fun updateFlashcard(@Header("Authorization") bearer: String, @Path("id") id: String, @Body body: FlashcardDto): Response<FlashcardDto>

    @HTTP(method = "DELETE", path = "flashcards/{id}", hasBody = false)
    suspend fun deleteFlashcard(@Header("Authorization") bearer: String, @Path("id") id: String): Response<Unit>

    @POST("flashcards/{id}/review")
    suspend fun reviewFlashcard(
        @Header("Authorization") bearer: String,
        @Path("id") id: String,
        @Body body: ReviewRequest
    ): Response<Map<String, Any?>>

    @POST("flashcards/import")
    suspend fun importFlashcards(@Header("Authorization") bearer: String, @Body body: List<FlashcardDto>): Response<Unit>

    // 6. Quizzes
    @POST("quizzes/{id}/finish")
    suspend fun finishQuiz(@Header("Authorization") bearer: String, @Path("id") id: String): Response<Map<String, Any?>>

    // 8. AI tools (server-side keys only; 503 AI_UNAVAILABLE is non-blocking for clients)
    @POST("ai/summarize")
    suspend fun aiSummarize(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    @POST("ai/flashcards")
    suspend fun aiFlashcards(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    @POST("ai/explain")
    suspend fun aiExplain(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    @POST("ai/outline")
    suspend fun aiOutline(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    @POST("ai/keyterms")
    suspend fun aiKeyTerms(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    @POST("ai/diary-summary")
    suspend fun aiDiarySummary(@Header("Authorization") bearer: String, @Body body: AiRequest): Response<AiResponse>

    // 9. Habits & diary
    @POST("habits")
    suspend fun createHabit(@Header("Authorization") bearer: String, @Body body: HabitRequest): Response<Unit>

    @HTTP(method = "DELETE", path = "habits/{id}", hasBody = false)
    suspend fun deleteHabit(@Header("Authorization") bearer: String, @Path("id") id: String): Response<Unit>

    @POST("habits/log")
    suspend fun logHabit(@Header("Authorization") bearer: String, @Body body: HabitLogRequest): Response<Unit>

    @POST("diary")
    suspend fun createDiary(@Header("Authorization") bearer: String, @Body body: DiaryRequest): Response<Unit>

    @GET("streak")
    suspend fun streak(@Header("Authorization") bearer: String): Response<StreakResponse>

    // 11. Health
    @GET("health")
    suspend fun health(): Response<HealthResponse>
}
