package io.medtech.backend.service

import io.medtech.backend.db.toInstant
import io.medtech.backend.db.todayLocalDate
import io.medtech.backend.domain.AnalyticsOverview
import io.medtech.backend.domain.DayMinutes
import io.medtech.backend.domain.SessionCreateRequest
import io.medtech.backend.domain.SessionKind
import io.medtech.backend.domain.StudySession
import io.medtech.backend.repository.AnalyticsRepository
import io.medtech.backend.repository.FlashcardRepository
import io.medtech.backend.repository.ProfileRepository
import io.medtech.backend.repository.QuizRepository
import io.medtech.backend.repository.SessionRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class AnalyticsService(
    private val sessions: SessionRepository = SessionRepository(),
    private val flashcards: FlashcardRepository = FlashcardRepository(),
    private val quizzes: QuizRepository = QuizRepository(),
    private val analytics: AnalyticsRepository = AnalyticsRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
    private val habits: HabitService = HabitService(),
) {

    suspend fun recordSession(userId: String, request: SessionCreateRequest): StudySession =
        sessions.insert(userId, request)

    suspend fun listSessions(userId: String, fromMillis: Long?, toMillis: Long?): List<StudySession> =
        sessions.list(userId, fromMillis, toMillis)

    suspend fun overview(userId: String): AnalyticsOverview {
        val tz = TimeZone.UTC
        val today = todayLocalDate()
        val dayStart = today.atTime(0, 0).toInstant(tz).toEpochMilliseconds()
        val allSessions = sessions.allForUser(userId)
        val answersToday = quizzes.answersTodayCount(userId, dayStart)
        val dueToday = flashcards.countDue(userId, dayStart + 24L * 60 * 60 * 1000 - 1)

        val recentAttempts = quizzes.recentAttempts(userId, limit = 5)
        val recentCorrect = recentAttempts.sumOf { it.correctCount }
        val recentTotal = recentAttempts.sumOf { it.totalCount }
        val recentAccuracy = if (recentTotal > 0) recentCorrect.toDouble() / recentTotal else 0.0

        val weekly = (6 downTo 0).map { offset ->
            val date = today.minus(offset, DateTimeUnit.DAY)
            val from = date.atTime(0, 0).toInstant(tz).toEpochMilliseconds()
            val to = date.atTime(23, 59).toInstant(tz).toEpochMilliseconds()
            val minutes = allSessions.filter { it.startedAt.toEpochMilliseconds() in from..to }.sumOf { it.minutes }
            DayMinutes(date = date, minutes = minutes)
        }

        val mastery = analytics.masteryFor(userId)
        val profile = profiles.get(userId)
        val weakest = mastery.filter { it.mastery > 0 }.minByOrNull { it.mastery }?.subjectId
            ?: profile?.weakestSubjects?.firstOrNull()

        val examCountdown = profile?.targetExamDate?.let { target ->
            (target.toEpochDays() - today.toEpochDays()).toInt()
        }

        return AnalyticsOverview(
            streakDays = habits.streak(userId).current,
            totalMinutes = allSessions.sumOf { it.minutes }.toLong(),
            minutesToday = weekly.last().minutes,
            flashcardsDueToday = dueToday,
            questionsAnsweredToday = answersToday,
            recentQuizAccuracy = recentAccuracy,
            weakestSubjectId = weakest,
            examCountdownDays = examCountdown?.toInt(),
            weeklyCalendar = weekly,
            mastery = mastery,
        )
    }
}
