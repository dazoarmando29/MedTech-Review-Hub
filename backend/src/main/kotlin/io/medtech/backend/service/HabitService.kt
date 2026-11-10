package io.medtech.backend.service

import io.medtech.backend.db.toInstant
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.DiaryCreateRequest
import io.medtech.backend.domain.DiaryEntry
import io.medtech.backend.domain.Habit
import io.medtech.backend.domain.HabitCreateRequest
import io.medtech.backend.domain.HabitKind
import io.medtech.backend.domain.HabitLog
import io.medtech.backend.domain.HabitLogRequest
import io.medtech.backend.domain.StreakInfo
import io.medtech.backend.repository.HabitDiaryRepository
import io.medtech.backend.repository.SessionRepository
import io.medtech.backend.security.Sanitizer
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class HabitService(
    private val habitDiary: HabitDiaryRepository = HabitDiaryRepository(),
    private val sessions: SessionRepository = SessionRepository(),
) {
    companion object {
        const val MAX_FREEZE_DAYS = 2
    }

    suspend fun habits(userId: String): List<Habit> = habitDiary.habits(userId)

    suspend fun createHabit(userId: String, request: HabitCreateRequest): Habit {
        val name = Sanitizer.clean(request.name, 160)
        if (name.isBlank()) throw ApiException.validation("name must not be blank")
        if (request.targetPerDay <= 0) throw ApiException.validation("targetPerDay must be positive")
        return habitDiary.insertHabit(userId, name, request.targetPerDay, request.kind)
    }

    suspend fun deleteHabit(userId: String, habitId: String) {
        if (!habitDiary.deleteHabit(userId, habitId)) throw ApiException.notFound("Habit not found")
    }

    suspend fun logHabit(userId: String, request: HabitLogRequest): HabitLog {
        val owned = habitDiary.habits(userId).any { it.id == request.habitId }
        if (!owned) throw ApiException.notFound("Habit not found")
        if (request.amount < 0) throw ApiException.validation("amount must not be negative")
        return habitDiary.logHabit(userId, request.habitId, request.date, request.amount)
    }

    suspend fun diary(userId: String): List<DiaryEntry> = habitDiary.diary(userId)

    suspend fun createDiary(userId: String, request: DiaryCreateRequest): DiaryEntry {
        val text = Sanitizer.clean(request.text, 20000)
        val mood = Sanitizer.cleanToken(request.mood, 32).ifBlank { "NEUTRAL" }
        return habitDiary.insertDiary(
            userId = userId,
            date = request.date,
            mood = mood,
            text = text,
            tags = request.tags.take(32).map { Sanitizer.cleanToken(it, 64) },
        )
    }

    suspend fun streak(userId: String): StreakInfo {
        val activeDates = buildSet {
            addAll(habitDiary.habitLogDates(userId).map { LocalDate.parse(it) })
            addAll(habitDiary.diary(userId).map { it.date })
            addAll(sessions.allForUser(userId).map { it.startedAt.toLocalDateTime(TimeZone.UTC).date })
        }
        return StreakInfo(
            current = currentStreak(activeDates),
            best = bestStreak(activeDates),
            freezeDaysLeft = freezeDaysLeft(activeDates),
        )
    }

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    private fun currentStreak(active: Set<LocalDate>): Int {
        if (active.isEmpty()) return 0
        val today = today()
        var cursor = if (today in active) today else if (today.minus(1, DateTimeUnit.DAY) in active) today.minus(1, DateTimeUnit.DAY) else return 0
        var streak = 0
        var freezes = 0
        while (true) {
            when {
                cursor in active -> {
                    streak++
                    cursor = cursor.minus(1, DateTimeUnit.DAY)
                }
                freezes < MAX_FREEZE_DAYS -> {
                    freezes++
                    cursor = cursor.minus(1, DateTimeUnit.DAY)
                }
                else -> break
            }
        }
        return streak
    }

    private fun freezeDaysLeft(active: Set<LocalDate>): Int {
        if (active.isEmpty()) return MAX_FREEZE_DAYS
        val today = today()
        var cursor = if (today in active) today else if (today.minus(1, DateTimeUnit.DAY) in active) today.minus(1, DateTimeUnit.DAY) else return MAX_FREEZE_DAYS
        var freezes = 0
        while (true) {
            when {
                cursor in active -> cursor = cursor.minus(1, DateTimeUnit.DAY)
                freezes < MAX_FREEZE_DAYS -> {
                    freezes++
                    cursor = cursor.minus(1, DateTimeUnit.DAY)
                }
                else -> break
            }
        }
        return MAX_FREEZE_DAYS - freezes
    }

    private fun bestStreak(active: Set<LocalDate>): Int {
        if (active.isEmpty()) return 0
        var best = 0
        var run = 0
        var freezesLeft = MAX_FREEZE_DAYS
        var cursor = active.min()
        val end = active.max()
        while (cursor <= end) {
            if (cursor in active) {
                run++
                freezesLeft = MAX_FREEZE_DAYS
                best = maxOf(best, run)
            } else if (freezesLeft > 0) {
                freezesLeft--
            } else {
                run = 0
                freezesLeft = MAX_FREEZE_DAYS
            }
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return best
    }
}
