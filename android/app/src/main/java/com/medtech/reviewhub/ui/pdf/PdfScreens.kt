package com.medtech.reviewhub.ui.pdf

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.model.NormalizedRect
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.EmptyState
import com.medtech.reviewhub.ui.components.SectionHeader
import com.medtech.reviewhub.ui.components.composeColor

// ---------------------------------------------------------------------------
// PDF library (SAF import via ACTION_OPEN_DOCUMENT)
// ---------------------------------------------------------------------------

@Composable
fun PdfLibraryScreen(
    vm: PdfViewModel,
    onOpenPdf: (String) -> Unit
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            vm.importFromUri(uri, name)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("PDF library", style = MaterialTheme.typography.headlineSmall)
        Text("Your PDFs are stored on-device for offline reading.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Open PDF (SAF)") }
        Spacer(Modifier.height(12.dp))

        if (state.documents.isEmpty()) {
            EmptyState("No PDFs yet. Import one with the button above — reading, highlights and search all work offline.")
        } else {
            state.documents.forEach { doc ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { onOpenPdf(doc.id) }) {
                    Text(doc.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${doc.pageCount} pages • ${doc.folder} • ${doc.sizeBytes / 1024} KB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Banner(text = it)
        }
    }
}

// ---------------------------------------------------------------------------
// PDF reader: zoom/pinch, page navigation, highlights, bookmarks, notes,
// text search, AI panel.
// ---------------------------------------------------------------------------

@Composable
fun PdfReaderScreen(
    vm: PdfViewModel,
    pdfId: String,
    apiBaseUrl: String,
    bearerToken: String?,
    highlightMeanings: Map<HighlightColor, String>,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(pdfId) {
        val doc = state.documents.firstOrNull { it.id == pdfId }
        if (doc != null && state.currentDoc?.id != pdfId) vm.open(doc)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var highlightDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "${state.currentDoc?.displayName ?: "PDF"} — page ${state.page + 1}/${state.pageCount}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.toggleBookmark() }) {
                Text(if (state.bookmarks.any { it.page == state.page }) "Bookmarked" else "Bookmark")
            }
        }

        Row {
            OutlinedButton(onClick = { vm.prevPage() }, enabled = state.page > 0) { Text("‹ Prev") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { vm.nextPage() }, enabled = state.page + 1 < state.pageCount) { Text("Next ›") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { highlightDialog = true }) { Text("Highlight") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { noteDialog = true }) { Text("Note") }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset = offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                            val x = (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                            val y = (change.position.y / canvasSize.height).coerceIn(0f, 1f)
                            val draft = state.highlightDraft
                            vm.setHighlightDraft(
                                if (draft == null) NormalizedRect(x, y, 0f, 0f)
                                else NormalizedRect(
                                    minOf(draft.x, x), minOf(draft.y, y),
                                    kotlin.math.abs(x - draft.x), kotlin.math.abs(y - draft.y)
                                )
                            )
                        }
                    }
                }
        ) {
            state.pageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${state.page + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            state.highlightDraft?.let { draft ->
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = HighlightColor.YELLOW.composeColor().copy(alpha = 0.35f),
                        topLeft = Offset(draft.x * size.width, draft.y * size.height),
                        size = androidx.compose.ui.geometry.Size(draft.w * size.width, draft.h * size.height)
                    )
                }
            }
        }

        // Search
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search in PDF (best-effort)") }
            )
            Button(onClick = { vm.search(searchQuery) }) { Text("Find") }
        }
        state.extractionNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.searchResults.take(3).forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall)
        }

        // Annotations + AI
        Column(modifier = Modifier.height(160.dp).verticalScroll(rememberScrollState())) {
            if (state.annotations.isNotEmpty()) {
                SectionHeader("Highlights & notes")
                state.annotations.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .height(14.dp)
                                .padding(end = 6.dp)
                                .then(Modifier.fillMaxWidth(0.05f))
                                .background2(a.color.composeColor())
                        )
                        Text(
                            "p${a.page + 1}: ${a.selectedText.take(60)}${if (a.note.isNotBlank()) " — ${a.note}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.deleteAnnotation(a) }) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AiToolsPdfSection(state, apiBaseUrl, bearerToken)
        }

        state.message?.let { Banner(text = it) }
    }

    if (highlightDialog) {
        HighlightDialog(
            meanings = highlightMeanings,
            onConfirm = { color, text, note ->
                vm.addHighlight(color, text, note)
                highlightDialog = false
            },
            onDismiss = { highlightDialog = false; vm.setHighlightDraft(null) }
        )
    }
    if (noteDialog) {
        NoteDialog(
            onConfirm = { text, note ->
                vm.addNote(text, note)
                noteDialog = false
            },
            onDismiss = { noteDialog = false }
        )
    }
}

private fun Modifier.background2(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.background(color))

@Composable
private fun AiToolsPdfSection(state: PdfViewModel.PdfState, apiBaseUrl: String, bearerToken: String?) {
    val pageText = "Page ${state.page + 1} of ${state.currentDoc?.displayName ?: "document"} " +
        state.searchResults.joinToString(" ").take(2000)
    com.medtech.reviewhub.ui.ai.AiToolsPanel(text = pageText, apiBaseUrl = apiBaseUrl, bearerToken = bearerToken)
}

@Composable
private fun HighlightDialog(
    meanings: Map<HighlightColor, String>,
    onConfirm: (HighlightColor, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var color by remember { mutableStateOf(HighlightColor.YELLOW) }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add highlight") },
        text = {
            Column {
                Text("Semantic color (meanings editable in Settings):", style = MaterialTheme.typography.bodySmall)
                HighlightColor.entries.toList().chunked(3).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        chunk.forEach { c ->
                            FilterChip(
                                selected = color == c,
                                onClick = { color = c },
                                label = { Text(c.label, color = c.composeColor()) }
                            )
                        }
                    }
                }
                Text(meanings[color] ?: "", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, label = { Text("Selected text") })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(color, text, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NoteDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add note") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Quote / context") })
                OutlinedTextField(note, { note = it }, label = { Text("Your note") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(text, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
