package io.medtech.backend.service

import io.medtech.backend.db.newId
import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.DiaryEntry
import io.medtech.backend.domain.Flashcard
import io.medtech.backend.domain.Habit
import io.medtech.backend.domain.HabitLog
import io.medtech.backend.domain.Preferences
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.StudyPlan
import io.medtech.backend.domain.StudySession
import io.medtech.backend.domain.TopicMastery
import io.medtech.backend.domain.UpdateMeRequest
import io.medtech.backend.domain.User
import io.medtech.backend.domain.UserResponse
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import io.medtech.backend.domain.PdfDocumentView
import io.medtech.backend.domain.Annotation
import io.medtech.backend.repository.HabitDiaryRepository
import io.medtech.backend.repository.AiContentRepository
import io.medtech.backend.repository.AnalyticsRepository
import io.medtech.backend.repository.FlashcardRepository
import io.medtech.backend.repository.PdfRepository
import io.medtech.backend.repository.PlanRepository
import io.medtech.backend.repository.PreferencesRepository
import io.medtech.backend.repository.ProfileRepository
import io.medtech.backend.repository.QuizRepository
import io.medtech.backend.repository.SessionRepository
import io.medtech.backend.repository.UserRepository

class UserService(
    private val users: UserRepository = UserRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
    private val preferences: PreferencesRepository = PreferencesRepository(),
    private val flashcards: FlashcardRepository = FlashcardRepository(),
    private val quizzes: QuizRepository = QuizRepository(),
    private val pdfs: PdfRepository = PdfRepository(),
    private val habitDiary: HabitDiaryRepository = HabitDiaryRepository(),
    private val plans: PlanRepository = PlanRepository(),
    private val sessions: SessionRepository = SessionRepository(),
    private val analytics: AnalyticsRepository = AnalyticsRepository(),
    private val aiContent: AiContentRepository = AiContentRepository(),
) {

    suspend fun getMe(userId: String): UserResponse {
        val user = users.findById(userId) ?: throw ApiException.notFound("User not found")
        return UserResponse(
            user = user.toUser(),
            profile = profiles.get(userId),
            preferences = preferences.get(userId),
        )
    }

    suspend fun updateMe(userId: String, request: UpdateMeRequest): UserResponse {
        val user = users.findById(userId) ?: throw ApiException.notFound("User not found")
        request.displayName?.let { name ->
            val clean = io.medtech.backend.security.Sanitizer.clean(name, 120)
            if (clean.isBlank()) throw ApiException.validation("displayName must not be blank")
            users.updateDisplayName(userId, clean)
        }
        val currentPrefs = preferences.get(userId)
        var prefs = currentPrefs
        request.avatarMascot?.let { mascot ->
            if (mascot != "cat" && mascot != "dog") {
                throw ApiException.validation("avatarMascot must be 'cat' or 'dog'", mapOf("avatarMascot" to "must be cat or dog"))
            }
            prefs = prefs.copy(avatarMascot = mascot)
        }
        request.locale?.let { locale ->
            if (locale != "en" && locale != "taglish") {
                throw ApiException.validation("locale must be 'en' or 'taglish'", mapOf("locale" to "must be en or taglish"))
            }
            prefs = prefs.copy(locale = locale)
        }
        request.theme?.let { theme -> prefs = prefs.copy(theme = io.medtech.backend.security.Sanitizer.cleanToken(theme, 32)) }
        request.seriousMode?.let { serious -> prefs = prefs.copy(seriousMode = serious) }
        if (prefs != currentPrefs) preferences.upsert(userId, prefs)
        return UserResponse(
            user = users.findById(userId)!!.toUser(),
            profile = profiles.get(userId),
            preferences = prefs,
        )
    }

    suspend fun putProfile(userId: String, profile: Profile): Profile {
        if (profile.minutesPerDay !in 1..24 * 60) {
            throw ApiException.validation("minutesPerDay must be between 1 and 1440")
        }
        profile.preferredSchedule.values.forEach { windows ->
            windows.forEach { window ->
                if (window.size != 2 || window[0] < 0 || window[1] > 24 * 60 || window[0] >= window[1]) {
                    throw ApiException.validation("preferredSchedule windows must be [startMinute, endMinute] within a day")
                }
            }
        }
        profiles.upsert(userId, profile)
        preferences.upsertNotifications(userId, profile.notificationsEnabled)
        return profile
    }

    suspend fun export(userId: String): UserExport {
        val user = users.findById(userId) ?: throw ApiException.notFound("User not found")
        val pdfList = pdfs.list(userId)
        val annotations = pdfList.flatMap { pdfs.annotations(userId, it.id) }
        val attempts = quizzes.listAttempts(userId, null)
        return UserExport(
            exportedAt = nowMillis().toInstant(),
            user = user.toUser(),
            profile = profiles.get(userId),
            preferences = preferences.get(userId),
            flashcards = flashcards.exportAll(userId),
            quizAttempts = attempts.map { attempt ->
                ExportQuizAttempt(
                    id = attempt.id,
                    mode = attempt.mode,
                    status = attempt.status,
                    startedAt = attempt.startedAtMillis.toInstant(),
                    finishedAt = attempt.finishedAtMillis?.toInstant(),
                    score = attempt.resultJson?.let { runCatching { io.medtech.backend.domain.decodeJson<io.medtech.backend.domain.QuizResult>(it) }.getOrNull() },
                    questionIds = attempt.questionIds,
                    answers = quizzes.answers(userId, attempt.id).map {
                        ExportAnswer(it.questionId, it.choiceId, it.isCorrect, it.answeredAtMillis.toInstant())
                    },
                )
            },
            pdfDocuments = pdfList,
            pdfAnnotations = annotations,
            habits = habitDiary.habits(userId),
            habitLogs = emptyList(),
            diaryEntries = habitDiary.diary(userId),
            studyPlans = listOfNotNull(plans.latestForUser(userId)),
            studySessions = sessions.allForUser(userId),
            mastery = analytics.masteryFor(userId),
            generatedAiContent = aiContent.allForUser(userId).map {
                ExportAiContent(
                    id = it.id,
                    task = it.task,
                    responseJson = it.responseJson,
                    inputTokens = it.inputTokens,
                    outputTokens = it.outputTokens,
                    createdAt = it.createdAtMillis.toInstant(),
                )
            },
        )
    }
}

@Serializable
data class ExportAnswer(val questionId: String, val choiceId: String, val isCorrect: Boolean, val answeredAt: Instant)

@Serializable
data class ExportQuizAttempt(
    val id: String,
    val mode: io.medtech.backend.domain.QuizMode,
    val status: io.medtech.backend.domain.QuizStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val score: io.medtech.backend.domain.QuizResult? = null,
    val questionIds: List<String> = emptyList(),
    val answers: List<ExportAnswer> = emptyList(),
)

@Serializable
data class ExportAiContent(
    val id: String,
    val task: String,
    val responseJson: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val createdAt: Instant,
)

@Serializable
data class UserExport(
    val exportedAt: Instant,
    val user: User,
    val profile: Profile? = null,
    val preferences: Preferences? = null,
    val flashcards: List<Flashcard> = emptyList(),
    val quizAttempts: List<ExportQuizAttempt> = emptyList(),
    val pdfDocuments: List<PdfDocumentView> = emptyList(),
    val pdfAnnotations: List<Annotation> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val diaryEntries: List<DiaryEntry> = emptyList(),
    val studyPlans: List<StudyPlan> = emptyList(),
    val studySessions: List<StudySession> = emptyList(),
    val mastery: List<TopicMastery> = emptyList(),
    val generatedAiContent: List<ExportAiContent> = emptyList(),
)
