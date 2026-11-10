package com.medtech.reviewhub.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.data.db.LoveNoteEntity
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.SectionHeader

/**
 * "Little Notes for You": private personal notes - create/edit/delete,
 * optional show-on date (yyyy-MM-dd), favorite flag. Never synced publicly.
 */
@Composable
fun NotesScreen(vm: NotesViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<LoveNoteEntity?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var draftDate by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<LoveNoteEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Little Notes for You", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Private personal notes - only you can see them. They stay on this device " +
                "(included in your JSON data export) and are never synced publicly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                editing = null
                draftText = ""
                draftDate = ""
                showDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New note")
            }
        }

        Spacer(Modifier.height(12.dp))
        Banner(
            text = "Notes are written by YOU - the app never auto-generates notes " +
                "pretending to be from a person."
        )
        Spacer(Modifier.height(12.dp))

        SectionHeader("Your notes (${state.notes.size})")
        if (state.notes.isEmpty()) {
            AppCard {
                Text(
                    "No notes yet. Write yourself a little encouragement for a hard study day.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        state.notes.forEach { note ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("Updated ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.updatedAtMs))}")
                                note.showOnDate?.let { append(" · show on $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.toggleFavorite(note.id, !note.favorite) }) {
                        Icon(
                            if (note.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (note.favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        editing = note
                        draftText = note.text
                        draftDate = note.showOnDate.orEmpty()
                        showDialog = true
                    }) {
                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { confirmDelete = note }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "New note" else "Edit note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        label = { Text("Your note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftDate,
                        onValueChange = { draftDate = it },
                        label = { Text("Show on date (yyyy-MM-dd, optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val existing = editing
                    if (existing == null) vm.create(draftText, draftDate)
                    else vm.update(existing, draftText, draftDate)
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }

    confirmDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This private note will be removed from this device.") },
            confirmButton = {
                Button(onClick = {
                    vm.delete(note.id)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep") }
            }
        )
    }
}
