package io.medtech.backend.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.medtech.backend.domain.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val httpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(ContentNegotiation) { json(AppJson) }
    }
}

private fun JsonObject.intOf(vararg path: String): Int {
    var current: Any = this
    for (key in path) {
        current = (current as? JsonObject)?.get(key) ?: return 0
    }
    return (current as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
}

private fun JsonObject.stringAt(key: String): String =
    (get(key) as? JsonPrimitive)?.content ?: ""

class OpenAiProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
) : AiProvider {
    override val name: String = "OPENAI"

    override suspend fun complete(task: AiTask, system: String, prompt: String): AiCompletion {
        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", model)
                put("temperature", 0.2)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                }
            })
        }
        if (response.status != HttpStatusCode.OK) {
            error("OpenAI request failed: ${response.status} ${response.bodyAsText().take(300)}")
        }
        val body = response.body<JsonObject>()
        val text = body["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""
        return AiCompletion(
            text = text,
            inputTokens = body.intOf("usage", "prompt_tokens"),
            outputTokens = body.intOf("usage", "completion_tokens"),
        )
    }
}

class AnthropicProvider(
    private val apiKey: String,
    private val model: String = "claude-3-5-haiku-latest",
) : AiProvider {
    override val name: String = "ANTHROPIC"

    override suspend fun complete(task: AiTask, system: String, prompt: String): AiCompletion {
        val response = httpClient.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", model)
                put("max_tokens", 2048)
                put("system", system)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "user"); put("content", prompt) })
                }
            })
        }
        if (response.status != HttpStatusCode.OK) {
            error("Anthropic request failed: ${response.status} ${response.bodyAsText().take(300)}")
        }
        val body = response.body<JsonObject>()
        val text = body["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        return AiCompletion(
            text = text,
            inputTokens = body.intOf("usage", "input_tokens"),
            outputTokens = body.intOf("usage", "output_tokens"),
        )
    }
}

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-1.5-flash",
) : AiProvider {
    override val name: String = "GEMINI"

    override suspend fun complete(task: AiTask, system: String, prompt: String): AiCompletion {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val response = httpClient.post(url) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonObject("system_instruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                }
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) }
                    })
                }
            })
        }
        if (response.status != HttpStatusCode.OK) {
            error("Gemini request failed: ${response.status} ${response.bodyAsText().take(300)}")
        }
        val body = response.body<JsonObject>()
        val text = body["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        return AiCompletion(
            text = text,
            inputTokens = body.intOf("usageMetadata", "promptTokenCount"),
            outputTokens = body.intOf("usageMetadata", "candidatesTokenCount"),
        )
    }
}
