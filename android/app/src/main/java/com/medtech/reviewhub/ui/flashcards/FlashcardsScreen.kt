package com.medtech.reviewhub.ui.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.Rating
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import com.medtech.reviewhub.ui.components.EmptyState
import com.medtech.reviewhub.ui.components.SectionHeader

@Composable
fun FlashcardsScreen(vm: FlashcardsViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Flashcards", style = MaterialTheme.typography.headlineSmall)
        Text("Spaced repetition (FSRS-4.5) — reviews are scheduled by predicted retrievability.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        // Queue summary + review flow
        val queue = state.queueNew + state.queueLearning + state.queueReview
        val card = queue.getOrNull(state.currentIndex)

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row {
                Text("New ${state.queueNew.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Learning ${state.queueLearning.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Review ${state.queueReview.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text("Reviewed this session: ${state.sessionReviewed}", style = MaterialTheme.typography.bodySmall)
            state.lastSchedule?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (card != null) {
            FlashcardView(card, state.flipped, reduceMotion = false, onTap = { vm.flip() })
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rating.entries.forEach { rating ->
                    Button(onClick = { vm.rate(rating) }, modifier = Modifier.weight(1f)) {
                        Text(
                            when (rating) {
                                Rating.AGAIN -> "Again"
                                Rating.HARD -> "Hard"
                                Rating.GOOD -> "Good"
                                Rating.EASY -> "Easy"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { vm.toggleFavorite(card.id) }) {
                    Text(if (card.favorite) "Unfavorite" else "Favorite")
                }
                TextButton(onClick = { vm.deleteCard(card.id) }) { Text("Delete") }
            }
        } else {
            EmptyState("No cards due. Great job — come back later or add new cards.",
                actionLabel = "Create card", onAction = { showCreate = true })
        }

        if (showCreate) {
            Spacer(Modifier.height(12.dp))
            CreateCardForm(
                onCreate = { front, back, tags, type ->
                    vm.createCard(front, back, tags, type)
                    showCreate = false
                },
                onCancel = { showCreate = false }
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Your decks", actionLabel = "Create card", onAction = { showCreate = true })
        state.decks.forEach { deck ->
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(deck.deckId, style = MaterialTheme.typography.titleSmall)
                Text("${deck.total} cards • ${deck.due} due", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (state.favorites.isNotEmpty()) {
            SectionHeader("Favorites")
            state.favorites.take(5).forEach { fav ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(fav.front, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(fav.back, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Import / export")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.exportAll() }) { Text("Export CSV") }
            OutlinedButton(onClick = { showImport = !showImport }) { Text("Import CSV") }
        }
        state.exportCsv?.let { csv ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_TEXT, csv)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share flashcards CSV"))
            }) { Text("Share export via…") }
        }
        if (showImport) {
            Spacer(Modifier.height(8.dp))
            var importText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                label = { Text("Paste CSV rows: front,back,tags") }
            )
            Button(onClick = { vm.importCsv(importText); showImport = false }) { Text("Import") }
        }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Banner(text = it)
        }
    }
}

@Composable
fun FlashcardView(
    card: com.medtech.reviewhub.domain.model.FlashcardRecord,
    flipped: Boolean,
    reduceMotion: Boolean,
    onTap: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(if (reduceMotion) 1 else 250),
        label = "flip"
    )
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onTap)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val text = if (!flipped) renderCloze(card.front) else renderCloze(card.back)
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.graphicsLayer { if (rotation > 90f) rotationZ = 180f }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (flipped) "Tap to see question" else "Tap to flip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (card.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(card.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Cloze rendering: [[1]] becomes [1] with the answer visible on the back. */
fun renderCloze(text: String): String =
    text.replace(Regex("\\[\\[(\\d+)\\]\\]"), "[$1]")

@Composable
private fun CreateCardForm(onCreate: (String, String, List<String>, String) -> Unit, onCancel: () -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BASIC") }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("New flashcard", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(front, { front = it }, label = { Text("Front (use [[1]] for cloze)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(back, { back = it }, label = { Text("Back") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row {
            listOf("BASIC", "CLOZE").forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                Spacer(Modifier.padding(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    onCreate(front, back, tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, type)
                },
                enabled = front.isNotBlank() && back.isNotBlank()
            ) { Text("Save card") }
        }
    }
}
