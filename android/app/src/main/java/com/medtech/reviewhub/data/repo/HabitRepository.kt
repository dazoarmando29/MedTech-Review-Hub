package com.medtech.reviewhub.data.repo

import com.medtech.reviewhub.data.db.DiaryEntity
import com.medtech.reviewhub.data.db.HabitDao
import com.medtech.reviewhub.data.db.HabitEntity
import com.medtech.reviewhub.data.db.HabitLogEntity
import com.medtech.reviewhub.domain.model.DiaryEntry
import com.medtech.reviewhub.domain.model.Habit
import com.medtech.reviewhub.domain.model.HabitKind
import com.medtech.reviewhub.domain.model.HabitLog
import com.medtech.reviewhub.domain.model.Mood
import com.medtech.reviewhub.domain.model.StreakInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Habits + diary + habit streak. Streak rule (shared/DESIGN.md voice): missing
 * one day is never shamed — the UI shows "Rest day recorded. Recovery is part
 * of the plan." and the streak survives a single gap.
 */
class HabitRepository(private val dao: HabitDao) {

    fun observeHabits(): Flow<List<Habit>> = dao.habits().map { list -> list.map { it.toDomain() } }

    suspend fun habitsOnce(): List<Habit> = dao.habitsOnce().map { it.toDomain() }

    suspend fun addHabit(name: String, targetPerDay: Int, kind: HabitKind): Habit {
        val habit = Habit(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            targetPerDay = targetPerDay.coerceAtLeast(1),
            kind = kind
        )
        dao.upsertHabit(habit.toEntity())
        return habit
    }

    suspend fun deleteHabit(habit: Habit) = dao.deleteHabit(habit.toEntity())

    suspend fun logAmount(habitId: String, date: LocalDate, amount: Int) {
        val existing = dao.logsForDate(date.toString()).firstOrNull { it.habitId == habitId }
        val total = (existing?.amount ?: 0) + amount
        dao.upsertLog(HabitLogEntity(id = existing?.id ?: 0, habitId = habitId, date = date.toString(), amount = total.coerceAtLeast(0)))
    }

    suspend fun setAmount(habitId: String, date: LocalDate, amount: Int) {
        val existing = dao.logsForDate(date.toString()).firstOrNull { it.habitId == habitId }
        dao.upsertLog(HabitLogEntity(id = existing?.id ?: 0, habitId = habitId, date = date.toString(), amount = amount.coerceAtLeast(0)))
    }

    suspend fun logsForDate(date: LocalDate): List<HabitLog> =
        dao.logsForDate(date.toString()).map { HabitLog(it.habitId, it.date, it.amount) }

    suspend fun logsBetween(from: LocalDate, to: LocalDate): List<HabitLog> =
        dao.logsBetween(from.toString(), to.toString()).map { HabitLog(it.habitId, it.date, it.amount) }

    // ---- Diary ----------------------------------------------------------

    fun observeDiary(): Flow<List<DiaryEntry>> = dao.diary().map { list -> list.map { it.toDomain() } }

    suspend fun addDiary(date: LocalDate, mood: Mood, text: String, tags: List<String>): DiaryEntry {
        val entry = DiaryEntry(
            id = UUID.randomUUID().toString(),
            date = date.toString(),
            mood = mood,
            text = text,
            tags = tags
        )
        dao.insertDiary(entry.toEntity())
        return entry
    }

    suspend fun deleteDiary(entry: DiaryEntry) = dao.deleteDiary(entry.toEntity())

    suspend fun recentDiary(limit: Int): List<DiaryEntry> = dao.recentDiary(limit).map { it.toDomain() }

    // ---- Streak (rest-day rule) -----------------------------------------

    /**
     * Habit-streak with a rest-day rule (shared/DESIGN.md): a gap of exactly
     * one day does not break the streak and is never shamed — it surfaces
     * [StreakInfo.restDayMessage] ("Rest day recorded. Recovery is part of the
     * plan."). A second consecutive missed day resets the streak.
     */
    suspend fun streak(today: LocalDate = LocalDate.now()): StreakInfo {
        val from = today.minusDays(120)
        val logs = dao.logsBetween(from.toString(), today.toString())
        val activeDays = logs.filter { it.amount > 0 }.map { it.date }.toHashSet()

        val rawDays = (0L..120L).map { today.minusDays(it).toString() }
        var streak = 0
        var best = 0
        var gapUsed = false
        var restDayMessage: String? = null
        for (day in rawDays) {
            if (day in activeDays) {
                streak++
                best = maxOf(best, streak)
            } else if (!gapUsed) {
                gapUsed = true
                restDayMessage = "Rest day recorded. Recovery is part of the plan."
            } else {
                break
            }
        }
        return StreakInfo(current = streak, best = best, restDayMessage = restDayMessage)
    }
}

// ---- Entity mapping ----------------------------------------------------

private fun HabitEntity.toDomain() = Habit(
    id = id, name = name, targetPerDay = targetPerDay,
    kind = runCatching { HabitKind.valueOf(kind) }.getOrDefault(HabitKind.MINUTES), archived = archived
)

private fun Habit.toEntity() = HabitEntity(
    id = id, name = name, targetPerDay = targetPerDay, kind = kind.name, archived = archived
)

private fun DiaryEntity.toDomain() = DiaryEntry(
    id = id, date = date,
    mood = runCatching { Mood.valueOf(mood) }.getOrDefault(Mood.OKAY),
    text = text,
    tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
)

private fun DiaryEntry.toEntity() = DiaryEntity(
    id = id, date = date, mood = mood.name, text = text, tagsCsv = tags.joinToString(","), createdAtMs = System.currentTimeMillis()
)
