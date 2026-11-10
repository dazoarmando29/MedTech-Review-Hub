package com.medtech.reviewhub.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medtech.reviewhub.data.network.NetworkModule
import com.medtech.reviewhub.data.network.dto.AiRequest
import com.medtech.reviewhub.ui.components.AppCard
import com.medtech.reviewhub.ui.components.Banner
import kotlinx.coroutines.launch

/**
 * AI tools panel (shared/api/API.md §8): summarize, key terms, flashcards from
 * selection, explain simple English / Taglish, outline. Calls the backend
 * AI endpoints (ai/summarize, ai/keyterms, ai/explain, ai/outline,
 * ai/flashcards) only when a server URL is configured in Settings. When the
 * server is missing or returns 503 (AI_UNAVAILABLE), a clear non-blocking
 * "AI features unavailable" state is shown — the app stays fully functional.
 */
@Composable
fun AiToolsPanel(
    text: String,
    apiBaseUrl: String,
    bearerToken: String?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val busy = remember { mutableStateOf(false) }
    val output = remember { mutableStateOf<String?>(null) }
    val unavailable = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }

    val configured = apiBaseUrl.isNotBlank()

    AppCard(modifier = modifier.fillMaxWidth()) {
        Text("AI tools", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Summarize, extract key terms, generate flashcards, explain simply (English/Taglish), outline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (!configured) {
            Banner(text = "AI features unavailable — set an API base URL in Settings to enable. Everything else works offline.")
        } else if (unavailable.value) {
            Banner(text = "AI features unavailable right now (server returned 503 AI_UNAVAILABLE). The rest of the app keeps working.")
        }

        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedButton(
                onClick = {
                    busy.value = true; error.value = null; unavailable.value = false
                    scope.launch {
                        val result = callAi(apiBaseUrl, bearerToken, "summarize", text)
                        handleAi(result, output, unavailable, error)
                        busy.value = false
                    }
                },
                enabled = configured && text.isNotBlank() && !busy.value
            ) { Text("Summarize") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(
                onClick = {
                    busy.value = true; error.value = null; unavailable.value = false
                    scope.launch {
                        val result = callAi(apiBaseUrl, bearerToken, "keyterms", text)
                        handleAi(result, output, unavailable, error)
                        busy.value = false
                    }
                },
                enabled = configured && text.isNotBlank() && !busy.value
            ) { Text("Key terms") }
        }
        Row {
            OutlinedButton(
                onClick = {
                    busy.value = true; error.value = null; unavailable.value = false
                    scope.launch {
                        val result = callAi(apiBaseUrl, bearerToken, "explain", text)
                        handleAi(result, output, unavailable, error)
                        busy.value = false
                    }
                },
                enabled = configured && text.isNotBlank() && !busy.value
            ) { Text("Explain simply") }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(
                onClick = {
                    busy.value = true; error.value = null; unavailable.value = false
                    scope.launch {
                        val result = callAi(apiBaseUrl, bearerToken, "outline", text)
                        handleAi(result, output, unavailable, error)
                        busy.value = false
                    }
                },
                enabled = configured && text.isNotBlank() && !busy.value
            ) { Text("Outline") }
        }
        Row {
            OutlinedButton(
                onClick = {
                    busy.value = true; error.value = null; unavailable.value = false
                    scope.launch {
                        val result = callAi(apiBaseUrl, bearerToken, "flashcards", text)
                        handleAi(result, output, unavailable, error)
                        busy.value = false
                    }
                },
                enabled = configured && text.isNotBlank() && !busy.value
            ) { Text("Flashcards from text") }
        }

        if (busy.value) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.height(24.dp))
        }
        error.value?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        output.value?.let {
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.height(200.dp).verticalScroll(rememberScrollState())) {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private sealed class AiResult {
    data class Ok(val text: String) : AiResult()
    object Unavailable : AiResult()
    data class Failed(val message: String) : AiResult()
}

private suspend fun callAi(baseUrl: String, token: String?, action: String, text: String): AiResult {
    return try {
        val api = NetworkModule.api(baseUrl)
        val bearer = "Bearer ${token.orEmpty()}"
        val request = AiRequest(text = text, mode = "PASSAGE", style = "SIMPLE_ENGLISH")
        val response = when (action) {
            "summarize" -> api.aiSummarize(bearer, request)
            "keyterms" -> api.aiKeyTerms(bearer, request)
            "explain" -> api.aiExplain(bearer, request)
            "outline" -> api.aiOutline(bearer, request)
            else -> api.aiFlashcards(bearer, request)
        }
        when {
            response.code() == 503 -> AiResult.Unavailable
            response.isSuccessful -> {
                val body = response.body()
                val resultText = body?.result?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }
                val warnings = body?.warnings?.joinToString("\n") ?: ""
                AiResult.Ok((resultText ?: "Done.") + if (warnings.isNotBlank()) "\n\nWarnings: $warnings" else "")
            }
            else -> AiResult.Failed("AI request failed (HTTP ${response.code()})")
        }
    } catch (e: Exception) {
        AiResult.Failed("AI request failed: ${e.message ?: "network error"}")
    }
}

private fun handleAi(
    result: AiResult,
    output: androidx.compose.runtime.MutableState<String?>,
    unavailable: androidx.compose.runtime.MutableState<Boolean>,
    error: androidx.compose.runtime.MutableState<String?>
) {
    when (result) {
        is AiResult.Ok -> { output.value = result.text; unavailable.value = false; error.value = null }
        AiResult.Unavailable -> { unavailable.value = true; output.value = null }
        is AiResult.Failed -> { error.value = result.message; output.value = null }
    }
}
