package com.medtech.reviewhub.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medtech.reviewhub.ReviewHubApp
import com.medtech.reviewhub.data.network.NetworkModule
import com.medtech.reviewhub.data.network.dto.SessionRequest
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Best-effort background sync (API.md "Sync model"): pushes local study
 * sessions and flashcard schedules to the backend when the user is signed in
 * and a server URL is configured. Failures are swallowed - the app is
 * offline-first and never blocks on the network. Conflict rule: last-write-wins
 * per entity using updatedAt; flashcard schedules merge by later lastReviewAt.
 *
 * Sync-safe subset only: study sessions + flashcard schedules. "Little Notes
 * for You" (love_notes) are private and are NEVER synced (they appear in the
 * local JSON export only); study-music playlists/favorites and generated WAV
 * caches are device-specific and are not synced either (no device paths ever
 * leave the device).
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = ReviewHubApp.container ?: return Result.success()
        val settings = container.settings.current()
        val token = settings.accessToken ?: return Result.success()
        val base = settings.apiBaseUrl
        if (base.isBlank()) return Result.success()

        return try {
            val api = NetworkModule.api(base)
            val bearer = "Bearer $token"
            val since = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
            val sessions = container.studyRepository.sessionsBetween(since, System.currentTimeMillis() + 1)
            for (s in sessions) {
                runCatching {
                    api.createSession(
                        bearer,
                        SessionRequest(
                            id = s.id,
                            startedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(s.startedAtMs)),
                            endedAt = s.endedAtMs?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) },
                            kind = s.kind,
                            subjectId = s.subjectId,
                            minutes = s.minutes,
                            itemsCompleted = s.itemsCompleted
                        )
                    )
                }
            }
            // Flashcard schedule push: last-write-wins by later lastReviewAt / higher reps.
            val cards = container.flashcardRepository.allCards()
            for (card in cards) {
                runCatching {
                    api.updateFlashcard(
                        bearer,
                        card.id,
                        com.medtech.reviewhub.data.network.dto.FlashcardDto(
                            id = card.id,
                            deckId = card.deckId,
                            front = card.front,
                            back = card.back,
                            type = card.type,
                            tags = card.tags,
                            subjectId = card.subjectId,
                            topicId = card.topicId,
                            difficulty = card.difficulty,
                            note = card.note,
                            favorite = card.favorite,
                            schedule = com.medtech.reviewhub.data.network.dto.FlashcardScheduleDto(
                                state = card.schedule.state.name,
                                stability = card.schedule.stability,
                                difficulty = card.schedule.difficulty,
                                dueAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(card.schedule.dueAtMs)),
                                lastReviewAt = card.schedule.lastReviewAtMs?.let {
                                    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it))
                                },
                                reps = card.schedule.reps,
                                lapses = card.schedule.lapses
                            )
                        )
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "medtech-sync"
    }
}
