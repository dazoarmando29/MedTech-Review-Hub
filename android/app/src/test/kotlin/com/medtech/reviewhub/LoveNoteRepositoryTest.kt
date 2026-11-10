package com.medtech.reviewhub

import com.medtech.reviewhub.data.db.LoveNoteDao
import com.medtech.reviewhub.data.db.LoveNoteEntity
import com.medtech.reviewhub.data.repo.LoveNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Little Notes for You" CRUD + due-note selection logic. The repository only
 * depends on the [LoveNoteDao] interface, so a fake DAO keeps these tests
 * plain-JVM (Room never runs here). Notes are private: the tests also pin the
 * "never auto-generated" rule - the repository creates only user-written text.
 */
class LoveNoteRepositoryTest {

    private class FakeLoveNoteDao : LoveNoteDao {
        val store = linkedMapOf<String, LoveNoteEntity>()
        private val flow = MutableStateFlow<List<LoveNoteEntity>>(emptyList())

        private fun sync() {
            flow.value = store.values.sortedByDescending { it.updatedAtMs }
        }

        override suspend fun upsert(note: LoveNoteEntity) {
            store[note.id] = note
            sync()
        }

        override suspend fun deleteById(id: String) {
            store.remove(id)
            sync()
        }

        override suspend fun byId(id: String): LoveNoteEntity? = store[id]
        override fun all(): Flow<List<LoveNoteEntity>> = flow
        override suspend fun allOnce(): List<LoveNoteEntity> = flow.value
        override suspend fun favorites(): List<LoveNoteEntity> = store.values.filter { it.favorite }
    }

    private fun repo(dao: FakeLoveNoteDao = FakeLoveNoteDao()) = LoveNoteRepository(dao)

    @Test
    fun `create stores the user text and returns the note`() = runBlocking {
        val repository = repo()
        val note = repository.create("You showed up today.", showOnDate = "2026-11-11")
        assertEquals("You showed up today.", note.text)
        assertEquals("2026-11-11", note.showOnDate)
        assertFalse(note.favorite)
        assertEquals(listOf(note), repository.all())
    }

    @Test
    fun `create trims text and treats blank date as none`() = runBlocking {
        val repository = repo()
        val note = repository.create("  hello  ", showOnDate = "   ")
        assertEquals("hello", note.text)
        assertNull(note.showOnDate)
    }

    @Test
    fun `update edits text and show-on date`() = runBlocking {
        val repository = repo()
        val note = repository.create("draft")
        repository.update(note.copy(text = "final", showOnDate = "2026-12-01"))
        val stored = repository.all().first()
        assertEquals("final", stored.text)
        assertEquals("2026-12-01", stored.showOnDate)
        assertTrue(stored.updatedAtMs >= note.updatedAtMs)
    }

    @Test
    fun `favorite toggles`() = runBlocking {
        val repository = repo()
        val note = repository.create("favorite me")
        repository.setFavorite(note.id, true)
        assertTrue(repository.all().first().favorite)
        assertEquals(1, repository.favorites().size)
        repository.setFavorite(note.id, false)
        assertFalse(repository.all().first().favorite)
    }

    @Test
    fun `delete removes the note`() = runBlocking {
        val dao = FakeLoveNoteDao()
        val repository = repo(dao)
        val note = repository.create("temporary")
        repository.delete(note.id)
        assertTrue(repository.all().isEmpty())
        assertNull(dao.byId(note.id))
    }

    @Test
    fun `due notes respect the show-on date`() {
        val repository = repo()
        val notes = listOf(
            LoveNoteEntity("1", "always", null, false, 0, 0),
            LoveNoteEntity("2", "today", "2026-11-11", false, 0, 0),
            LoveNoteEntity("3", "future", "2027-01-01", false, 0, 0),
            LoveNoteEntity("4", "past", "2026-01-01", false, 0, 0)
        )
        val due = repository.dueNotes(notes, today = "2026-11-11")
        assertEquals(listOf("1", "2", "4"), due.map { it.id })
    }

    @Test
    fun `pick display note is deterministic and only picks due notes`() {
        val repository = repo()
        val notes = listOf(
            LoveNoteEntity("1", "a", null, false, 0, 0),
            LoveNoteEntity("2", "b", "2026-11-11", false, 0, 0),
            LoveNoteEntity("3", "c", "2027-01-01", false, 0, 0)
        )
        val first = repository.pickDisplayNote(notes, "2026-11-11", seed = 1)
        val second = repository.pickDisplayNote(notes, "2026-11-11", seed = 1)
        assertNotNull(first)
        assertEquals(first, second)
        assertTrue(first!!.id != "3")
    }

    @Test
    fun `pick display note returns null when nothing is due`() {
        val repository = repo()
        val notes = listOf(LoveNoteEntity("1", "future", "2027-01-01", false, 0, 0))
        assertNull(repository.pickDisplayNote(notes, "2026-11-11", seed = 0))
    }
}
