package com.medtech.reviewhub.ui.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.medtech.reviewhub.data.music.MusicTrackJson
import com.medtech.reviewhub.data.music.UserPlaylist
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.SectionHeader

/**
 * Study Music screen: "Find your focus, one melody at a time."
 * Category pills with album-style illustrations, per-track play/favorite/
 * download controls, suggested + personal playlists, and the full player sheet
 * (real ExoPlayer position, next/prev, shuffle, repeat, volume, mute).
 */
@Composable
fun MusicScreen(vm: MusicViewModel, onOpenPlayer: () -> Unit) {
    val state by vm.state.collectAsState()
    val player by vm.player.collectAsState()
    var selectedCategory by remember { mutableStateOf("") }
    var expandedPlaylist by remember { mutableStateOf("") }
    var addTrackMenuFor by remember { mutableStateOf<MusicTrackJson?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var renamePlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var renameText by remember { mutableStateOf("") }

    val categories = vm.library.categories
    val category = categories.firstOrNull { it.id == selectedCategory } ?: categories.firstOrNull()
    val tracks = category?.let { vm.library.tracksIn(it.id) }.orEmpty()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Study Music", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Find your focus, one melody at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "All audio is generated on this device - original compositions (CC0) " +
                "by MedTech Companion Studio.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // ---- Category pills with album-style illustrations ----
        SectionHeader("Categories")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { c ->
                val selected = c.id == category?.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedCategory = c.id }
                        .padding(8.dp)
                ) {
                    Image(
                        painter = painterResource(vm.library.artDrawable(c.art)),
                        contentDescription = c.name,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        c.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        category?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- Tracks of the selected category ----
        Spacer(Modifier.height(8.dp))
        SectionHeader(category?.name ?: "Tracks")
        tracks.forEach { track ->
            TrackRow(
                track = track,
                isCurrent = player.trackId == track.id && player.isPlaying,
                favorite = track.id in state.favorites,
                generating = track.id in state.generating,
                downloaded = track.id in state.generated,
                onPlay = { vm.playTrack(track, tracks) },
                onTogglePlay = { vm.togglePlayPause() },
                onFavorite = { vm.toggleFavorite(track.id) },
                onDownload = { vm.generateDownload(track) },
                onRemoveDownload = { vm.removeDownload(track.id) },
                onAddToPlaylist = { addTrackMenuFor = track }
            )
        }

        // ---- Quick picks ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Quick picks")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(modifier = Modifier.weight(1f), onClick = { vm.playQuickPick("morning") }) {
                Text("Morning study", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("My Pink Study Playlist", style = MaterialTheme.typography.bodySmall)
            }
            AppCard(modifier = Modifier.weight(1f), onClick = { vm.playQuickPick("late-night") }) {
                Text("Late-night review", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text("Midnight ASCPi Review", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Suggested playlists ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Suggested playlists")
        vm.library.suggestedPlaylists.forEach { pl ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.titleSmall)
                        Text("${pl.trackIds.size} tracks", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.playSuggested(pl.id) }) { Text("Play") }
                }
            }
        }

        // ---- Personal playlists ----
        Spacer(Modifier.height(16.dp))
        SectionHeader(
            "My playlists",
            actionLabel = "New",
            onAction = {
                newPlaylistName = ""
                showNewPlaylist = true
            }
        )
        if (state.playlists.isEmpty()) {
            AppCard {
                Text("Create your own playlists - morning study, late-night review, anything cozy.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.playlists.forEach { pl ->
            val expanded = expandedPlaylist == pl.id
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).clickable {
                        expandedPlaylist = if (expanded) "" else pl.id
                    }) {
                        Text(pl.name, style = MaterialTheme.typography.titleSmall)
                        Text("${pl.trackIds.size} tracks", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.playUserPlaylist(pl) }) { Text("Play") }
                    IconButton(onClick = {
                        renamePlaylist = pl
                        renameText = pl.name
                    }) {
                        Text("Rename", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { vm.deletePlaylist(pl.id) }) {
                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = {
                        expandedPlaylist = if (expanded) "" else pl.id
                    }) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand")
                    }
                }
                if (expanded) {
                    val playlistTracks = pl.trackIds.mapNotNull { id -> vm.library.track(id) }
                    playlistTracks.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { vm.playTrack(t, playlistTracks) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                            }
                            IconButton(onClick = { vm.removeFromPlaylist(pl.id, t.id) }) {
                                Text("Remove", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ---- Favorites ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Favorites")
        val favoriteTracks = vm.library.tracks.filter { it.id in state.favorites }
        if (favoriteTracks.isEmpty()) {
            AppCard {
                Text("Tap the heart on any track to keep it here.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        favoriteTracks.forEach { track ->
            TrackRow(
                track = track,
                isCurrent = player.trackId == track.id && player.isPlaying,
                favorite = true,
                generating = track.id in state.generating,
                downloaded = track.id in state.generated,
                onPlay = { vm.playTracks(favoriteTracks, favoriteTracks.indexOf(track)) },
                onTogglePlay = { vm.togglePlayPause() },
                onFavorite = { vm.toggleFavorite(track.id) },
                onDownload = { vm.generateDownload(track) },
                onRemoveDownload = { vm.removeDownload(track.id) },
                onAddToPlaylist = { addTrackMenuFor = track }
            )
        }

        // ---- Downloads / storage ----
        Spacer(Modifier.height(16.dp))
        SectionHeader("Offline downloads")
        AppCard {
            Text(
                "Storage used by generated audio: ${formatBytes(state.storageBytes)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Audio is synthesized on-device, so \"download\" simply pre-generates the " +
                    "track for instant playback. ${state.generated.size} track(s) cached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.clearAllDownloads() }) { Text("Remove all downloads") }
        }
        Spacer(Modifier.height(24.dp))
    }

    // Add-to-playlist menu.
    addTrackMenuFor?.let { track ->
        var menuOpen by remember { mutableStateOf(true) }
        DropdownMenu(expanded = menuOpen, onDismissRequest = {
            menuOpen = false
            addTrackMenuFor = null
        }) {
            state.playlists.forEach { pl ->
                DropdownMenuItem(
                    text = { Text(pl.name) },
                    onClick = {
                        vm.addToPlaylist(pl.id, track.id)
                        menuOpen = false
                        addTrackMenuFor = null
                    },
                    trailingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text("New playlist...") },
                onClick = {
                    menuOpen = false
                    addTrackMenuFor = null
                    newPlaylistName = ""
                    showNewPlaylist = true
                }
            )
        }
    }

    if (showNewPlaylist) {
        AlertDialog(
            onDismissRequest = { showNewPlaylist = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.createPlaylist(newPlaylistName)
                    showNewPlaylist = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylist = false }) { Text("Cancel") }
            }
        )
    }

    renamePlaylist?.let { pl ->
        AlertDialog(
            onDismissRequest = { renamePlaylist = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renamePlaylist(pl.id, renameText)
                    renamePlaylist = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamePlaylist = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrackRow(
    track: MusicTrackJson,
    isCurrent: Boolean,
    favorite: Boolean,
    generating: Boolean,
    downloaded: Boolean,
    onPlay: () -> Unit,
    onTogglePlay: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (isCurrent) onTogglePlay() else onPlay() }) {
                Icon(
                    if (isCurrent) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isCurrent) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium)
                Text(track.artist, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(track.license, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (favorite) "Unfavorite" else "Favorite",
                    tint = if (favorite) Color(0xFFF48FB1) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (generating) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { if (downloaded) onRemoveDownload() else onDownload() }) {
                    Icon(
                        if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (downloaded) "Remove download" else "Download for offline",
                        tint = if (downloaded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist")
            }
        }
    }
}

/** Docked mini-player: play/pause, skip, expand (shown above the nav bar). */
@Composable
fun MiniPlayer(
    vm: MusicViewModel,
    onExpand: () -> Unit
) {
    val player by vm.player.collectAsState()
    if (player.trackId == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(player.title, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(player.artist, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = { vm.previous() }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
        }
        IconButton(onClick = { vm.togglePlayPause() }) {
            Icon(
                if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (player.isPlaying) "Pause" else "Play"
            )
        }
        IconButton(onClick = { vm.next() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next")
        }
        IconButton(onClick = onExpand) {
            Icon(Icons.Filled.ExpandLess, contentDescription = "Expand player")
        }
    }
}

/**
 * Full player sheet: seek bar (real ExoPlayer position), time/duration,
 * next/prev, shuffle, repeat track / repeat playlist, volume, mute.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val player by vm.player.collectAsState()

    // Poll the real playback position while the sheet is open.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshPosition()
            kotlinx.coroutines.delay(500)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(player.title.ifBlank { "Nothing playing" },
                style = MaterialTheme.typography.titleLarge)
            Text(player.artist, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Original generative composition - CC0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            val duration = player.durationMs.coerceAtLeast(1)
            var sliderPos by remember(player.positionMs, player.trackId) {
                mutableStateOf(player.positionMs.toFloat())
            }
            Slider(
                value = sliderPos.coerceIn(0f, duration.toFloat()),
                onValueChange = { sliderPos = it },
                onValueChangeFinished = { vm.seekTo(sliderPos.toLong()) },
                valueRange = 0f..duration.toFloat()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(player.positionMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatTime(player.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (player.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous",
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.togglePlayPause() }) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next",
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                        else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (player.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleMute() }) {
                    Icon(
                        if (player.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (player.muted) "Unmute" else "Mute"
                    )
                }
                Slider(
                    value = if (player.muted) 0f else player.volume,
                    onValueChange = { vm.setVolume(it) },
                    modifier = Modifier.weight(1f)
                )
                Text("${((if (player.muted) 0f else player.volume) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall)
            }
            Text(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat: this track"
                    Player.REPEAT_MODE_ALL -> "Repeat: whole playlist"
                    else -> "Repeat: off"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
