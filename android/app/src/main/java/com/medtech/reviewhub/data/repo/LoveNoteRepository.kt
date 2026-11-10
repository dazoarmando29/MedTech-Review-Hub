package com.medtech.reviewhub.data.repo

import com.medtech.reviewhub.data.db.LoveNoteDao
import com.medtech.reviewhub.data.db.LoveNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * "Little Notes for You" - private personal notes (Room). CRUD + the due-note
 * selection rules, kept pure enough to unit-test on the JVM with a fake DAO.
 *
 * Privacy: notes are device-local. They are never sent to the sync worker
 * (see SyncWorker) and appear in the JSON data export only. The app NEVER
 * auto-generates notes pretending to be from a person.
 */
class LoveNoteRepository(private val dao: LoveNoteDao) {

    fun observe(): Flow<List<LoveNoteEntity>> = dao.all()

    suspend fun all(): List<LoveNoteEntity> = dao.allOnce()

    suspend fun favorites(): List<LoveNoteEntity> = dao.favorites()

    suspend fun create(text: String, showOnDate: String? = null, favorite: Boolean = false): LoveNoteEntity {
        val now = System.currentTimeMillis()
        val note = LoveNoteEntity(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            showOnDate = showOnDate?.trim()?.takeIf { it.isNotBlank() },
            favorite = favorite,
            createdAtMs = now,
            updatedAtMs = now
        )
        dao.upsert(note)
        return note
    }

    suspend fun update(note: LoveNoteEntity) {
        dao.upsert(note.copy(updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val note = dao.byId(id) ?: return
        dao.upsert(note.copy(favorite = favorite, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    /**
     * Notes due for display on [today] (yyyy-MM-dd): notes with no show-on
     * date (always eligible) plus notes whose show-on date is today or earlier.
     */
    fun dueNotes(notes: List<LoveNoteEntity>, today: String): List<LoveNoteEntity> =
        notes.filter { it.showOnDate == null || it.showOnDate <= today }

    /** Deterministic pick of a display note for the dashboard (seed = day-of-year). */
    fun pickDisplayNote(notes: List<LoveNoteEntity>, today: String, seed: Long): LoveNoteEntity? {
        val due = dueNotes(notes, today)
        if (due.isEmpty()) return null
        return due[Math.floorMod(seed, due.size)]
    }
}
