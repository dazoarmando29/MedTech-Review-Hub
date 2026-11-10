package com.medtech.reviewhub.data.music

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.musicDataStore: DataStore<Preferences> by preferencesDataStore(name = "music_prefs")

@Serializable
data class UserPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList()
)

/**
 * Study-music user data: favorite tracks and personal playlists. Device-local
 * only (DataStore) - never synced (like "Little Notes for You"), because
 * generated audio caches and personal playlists are device-specific.
 */
class MusicRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorite_track_ids")
        val PLAYLISTS = stringPreferencesKey("user_playlists")
    }

    val favorites: Flow<Set<String>> = context.musicDataStore.data.map { p ->
        decodeSet(p[Keys.FAVORITES])
    }

    val playlists: Flow<List<UserPlaylist>> = context.musicDataStore.data.map { p ->
        decodePlaylists(p[Keys.PLAYLISTS])
    }

    suspend fun favoriteOnce(): Set<String> = favorites.first()
    suspend fun playlistsOnce(): List<UserPlaylist> = playlists.first()

    suspend fun toggleFavorite(trackId: String) {
        context.musicDataStore.edit { p ->
            val current = decodeSet(p[Keys.FAVORITES])
            val next = if (trackId in current) current - trackId else current + trackId
            p[Keys.FAVORITES] = next.sorted().joinToString("\u0001")
        }
    }

    suspend fun createPlaylist(name: String): UserPlaylist {
        val playlist = UserPlaylist(
            id = "up-" + java.util.UUID.randomUUID().toString().take(8),
            name = name.trim().ifBlank { "My playlist" }
        )
        context.musicDataStore.edit { p ->
            p[Keys.PLAYLISTS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(UserPlaylist.serializer()),
                decodePlaylists(p[Keys.PLAYLISTS]) + playlist
            )
        }
        return playlist
    }

    suspend fun renamePlaylist(id: String, name: String) = editPlaylists { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
    }

    suspend fun deletePlaylist(id: String) = editPlaylists { list -> list.filterNot { it.id == id } }

    suspend fun addTrack(playlistId: String, trackId: String) = editPlaylists { list ->
        list.map {
            if (it.id == playlistId && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
        }
    }

    suspend fun removeTrack(playlistId: String, trackId: String) = editPlaylists { list ->
        list.map {
            if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it
        }
    }

    private suspend fun editPlaylists(block: (List<UserPlaylist>) -> List<UserPlaylist>) {
        context.musicDataStore.edit { p ->
            p[Keys.PLAYLISTS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(UserPlaylist.serializer()),
                block(decodePlaylists(p[Keys.PLAYLISTS]))
            )
        }
    }

    private fun decodeSet(raw: String?): Set<String> =
        if (raw.isNullOrBlank()) emptySet() else raw.split("\u0001").filter { it.isNotBlank() }.toSet()

    private fun decodePlaylists(raw: String?): List<UserPlaylist> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(UserPlaylist.serializer()), raw
            )
        }.getOrDefault(emptyList())
}
