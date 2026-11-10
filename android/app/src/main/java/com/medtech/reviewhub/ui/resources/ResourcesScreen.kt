package com.medtech.reviewhub.ui.resources

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.R
import com.medtech.reviewhub.data.content.ResourcesLoader
import com.medtech.reviewhub.domain.model.ResourcesFile
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.EmptyState
import com.medtech.reviewhub.ui.components.SectionHeader

/**
 * Philippine ASCPi resources (shared/content/resources.json): review centers,
 * official ASCP links (opened via ACTION_VIEW), reading list, exam info and the
 * disclaimer. When the content file is not bundled in this build the screen
 * shows an empty state - content is never invented.
 */
@Composable
fun ResourcesScreen() {
    val context = LocalContext.current
    val resources by produceState(initialValue = ResourcesFile.EMPTY) {
        value = ResourcesLoader(context.assets).load()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(stringResource(R.string.resources_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.resources_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (resources.isEmpty) {
            EmptyState(stringResource(R.string.resources_empty))
            return@Column
        }

        if (resources.reviewCenters.isNotEmpty()) {
            SectionHeader(stringResource(R.string.resources_review_centers))
            resources.reviewCenters.forEach { center ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(center.name, style = MaterialTheme.typography.titleSmall)
                    if (center.location.isNotBlank()) {
                        Text(center.location, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (center.program.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(center.program, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (center.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(center.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (center.feeNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(center.feeNote, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (center.inclusions.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        center.inclusions.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (center.contact.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(center.contact, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (resources.ascpLinks.isNotEmpty()) {
            SectionHeader(stringResource(R.string.resources_ascp_links))
            resources.ascpLinks.forEach { link ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(link.title, style = MaterialTheme.typography.titleSmall)
                    if (link.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(link.description, style = MaterialTheme.typography.bodySmall)
                    }
                    if (link.url.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                            }
                        }) { Text(stringResource(R.string.resources_open_link)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (resources.readingList.isNotEmpty()) {
            SectionHeader(stringResource(R.string.resources_reading_list))
            resources.readingList.forEach { book ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall)
                    if (book.authors.isNotEmpty()) {
                        Text(book.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val imprint = listOf(
                        book.edition.takeIf { it.isNotBlank() },
                        book.publisher.takeIf { it.isNotBlank() },
                        book.year.takeIf { it > 0 }?.toString()
                    ).filterNotNull().joinToString(" · ")
                    if (imprint.isNotBlank()) {
                        Text(imprint, style = MaterialTheme.typography.bodySmall)
                    }
                    if (book.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(book.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (book.licenseNote.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(book.licenseNote, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (resources.examInfo.isNotEmpty()) {
            SectionHeader(stringResource(R.string.resources_exam_info))
            resources.examInfo.forEach { info ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleSmall)
                    if (info.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(info.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (resources.disclaimer.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(resources.disclaimer, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
