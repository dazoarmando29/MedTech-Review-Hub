package com.medtech.reviewhub.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.domain.model.Lesson
import com.medtech.reviewhub.domain.model.Table
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.EmptyState
import com.medtech.reviewhub.ui.components.SectionHeader
import com.medtech.reviewhub.ui.theme.MonoTextStyle

// ---------------------------------------------------------------------------
// Library: subjects -> topics -> lessons
// ---------------------------------------------------------------------------

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenLesson: (String) -> Unit
) {
    val state by vm.library.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Review library", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.search(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search lessons and keywords") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.searchQuery.isNotBlank()) {
                item { SectionHeader("Search results") }
                items(state.searchResults) { lesson ->
                    LessonCard(lesson, bookmarked = lesson.id in state.bookmarkedLessonIds, onClick = { onOpenLesson(lesson.id) })
                }
                if (state.searchResults.isEmpty()) {
                    item { EmptyState("No lessons matched \"${state.searchQuery}\".") }
                }
            } else {
                items(state.subjects) { subject ->
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(subject.name, style = MaterialTheme.typography.titleMedium)
                        Text(subject.blueprintNote, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        subject.topics.forEach { topic ->
                            SectionHeader(topic.name)
                            topic.lessonIds.forEach { lessonId ->
                                val lesson = state.searchResults.firstOrNull { it.id == lessonId }
                                if (lesson != null) {
                                    LessonCard(lesson, onClick = { onOpenLesson(lessonId) })
                                } else {
                                    Text(
                                        "• $lessonId",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )
                                    TextButton2("Open", onClick = { onOpenLesson(lessonId) })
                                }
                            }
                        }
                    }
                }
                item {
                    if (state.formulas.isNotEmpty()) {
                        SectionHeader("Formula sheet")
                        state.formulas.forEach { f ->
                            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(f.title, style = MaterialTheme.typography.titleSmall)
                                Text(f.formula, style = MonoTextStyle)
                                if (f.caveat.isNotBlank()) {
                                    Text(f.caveat, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButton2(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
}

@Composable
fun LessonCard(lesson: Lesson, onClick: () -> Unit, bookmarked: Boolean = false) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                if (lesson.summary.isNotBlank()) {
                    Text(lesson.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                Text(
                    "${lesson.readingMinutes} min • ${lesson.difficulty.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (bookmarked) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarked",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lesson detail: QUICK REVIEW / DEEP STUDY tabs
// ---------------------------------------------------------------------------

@Composable
fun LessonScreen(
    vm: LibraryViewModel,
    apiBaseUrl: String,
    bearerToken: String?,
    seriousMode: Boolean,
    onBack: () -> Unit,
    onPracticeQuestions: (lessonId: String) -> Unit,
    onOpenFlashcards: () -> Unit
) {
    val state by vm.lesson.collectAsState()
    val lesson = state.lesson
    var tab by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()

    // Persist reading progress + resume position as the user scrolls.
    LaunchedEffect(lesson?.id) {
        snapshotFlow { scroll.value }.collect { offset ->
            val max = scroll.maxValue
            if (max > 0) {
                vm.saveProgress(lesson?.id ?: return@collect, offset.toFloat() / max, offset)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row {
            Text("Lesson", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.toggleBookmark(lesson?.id ?: return@IconButton) }) {
                Icon(
                    if (state.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Bookmark"
                )
            }
        }

        if (lesson == null) {
            EmptyState("Loading lesson…")
            return
        }

        Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("QUICK REVIEW") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("DEEP STUDY") })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            if (tab == 0) {
                QuickReviewTab(lesson, seriousMode)
            } else {
                DeepStudyTab(lesson)
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader("Practice")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = { onPracticeQuestions(lesson.id) }) {
                    Text("Practice questions (${lesson.practiceQuestionIds.size})")
                }
                androidx.compose.material3.OutlinedButton(onClick = onOpenFlashcards) {
                    Text("Related flashcards (${lesson.relatedFlashcardIds.size})")
                }
            }

            if (lesson.references.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("References")
                lesson.references.forEach { ref ->
                    Text("${ref.title}${if (ref.detail.isNotBlank()) ", ${ref.detail}" else ""}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            AiToolsPanelSection(lesson, apiBaseUrl, bearerToken)

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(onClick = onBack) { Text("Back to library") }
        }
    }
}

@Composable
private fun AiToolsPanelSection(lesson: Lesson, apiBaseUrl: String, bearerToken: String?) {
    val text = buildString {
        append(lesson.title).append(". ").append(lesson.summary)
        lesson.deepStudy.sections.forEach { append("\n").append(it.heading).append(": ").append(it.body) }
    }.take(4000)
    com.medtech.reviewhub.ui.ai.AiToolsPanel(text = text, apiBaseUrl = apiBaseUrl, bearerToken = bearerToken)
}

@Composable
private fun QuickReviewTab(lesson: Lesson, seriousMode: Boolean) {
    val qr = lesson.quickReview
    if (qr.highYield.isNotEmpty()) {
        SectionHeader("High yield")
        qr.highYield.forEach {
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    qr.mustKnowTable?.let { table ->
        SectionHeader("Must-know table")
        DataTable(table)
    }
    if (qr.mnemonic.isNotBlank()) {
        SectionHeader("Mnemonic")
        Text(qr.mnemonic, style = MaterialTheme.typography.bodyLarge)
    }
    if (!seriousMode) {
        lesson.memoryHooks.forEach { hook ->
            if (hook.funny.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AppCard {
                    Text("Memory hook", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text(hook.text.ifBlank { hook.funny }, style = MaterialTheme.typography.bodyMedium)
                    Text(hook.funny, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (lesson.keywords.isNotEmpty()) {
        SectionHeader("Keywords")
        Text(lesson.keywords.joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
    }
    if (lesson.objectives.isNotEmpty()) {
        SectionHeader("Learning objectives")
        lesson.objectives.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun DeepStudyTab(lesson: Lesson) {
    val ds = lesson.deepStudy
    if (ds.introduction.isNotBlank()) {
        Text(ds.introduction, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
    }
    ds.sections.forEach { section ->
        SectionHeader(section.heading)
        Text(section.body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
    }
    if (ds.clinicalSignificance.isNotBlank()) {
        SectionHeader("Clinical significance")
        Text(ds.clinicalSignificance, style = MaterialTheme.typography.bodyLarge)
    }
    if (ds.labInterpretation.isNotBlank()) {
        SectionHeader("Lab interpretation")
        Text(ds.labInterpretation, style = MaterialTheme.typography.bodyLarge)
    }
    ds.comparisonTables.forEach { table ->
        SectionHeader(table.title.ifBlank { "Comparison" })
        DataTable(table)
    }
    if (ds.examTraps.isNotEmpty()) {
        SectionHeader("Exam traps")
        ds.examTraps.forEach {
            AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
    ds.workedExample?.let {
        SectionHeader("Worked example")
        Text(it, style = MonoTextStyle)
    }
}

@Composable
fun DataTable(table: Table) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            table.columns.forEach { col ->
                Text(col, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(4.dp))
            }
        }
        table.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(cell, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(4.dp))
                }
            }
        }
    }
}
