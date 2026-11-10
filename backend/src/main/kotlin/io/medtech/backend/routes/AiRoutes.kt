package io.medtech.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.AiCountRequest
import io.medtech.backend.domain.AiExplainRequest
import io.medtech.backend.domain.AiSummarizeRequest
import io.medtech.backend.domain.AiTextRequest
import io.medtech.backend.domain.DiarySummaryRequest

fun Application.aiRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            post("/api/v1/ai/summarize") {
                val request = call.receiveOrThrow<AiSummarizeRequest>()
                call.respond(services.aiService.summarize(call.userId(), request.text, request.mode.name, request.pdfId, request.page))
            }
            post("/api/v1/ai/flashcards") {
                val request = call.receiveOrThrow<AiCountRequest>()
                call.respond(services.aiService.flashcards(call.userId(), request.text, request.count))
            }
            post("/api/v1/ai/quiz") {
                val request = call.receiveOrThrow<AiCountRequest>()
                call.respond(services.aiService.quiz(call.userId(), request.text, request.count))
            }
            post("/api/v1/ai/explain") {
                val request = call.receiveOrThrow<AiExplainRequest>()
                call.respond(services.aiService.explain(call.userId(), request.text, request.style.name))
            }
            post("/api/v1/ai/outline") {
                val request = call.receiveOrThrow<AiTextRequest>()
                call.respond(services.aiService.outline(call.userId(), request.text))
            }
            post("/api/v1/ai/keyterms") {
                val request = call.receiveOrThrow<AiTextRequest>()
                call.respond(services.aiService.keyterms(call.userId(), request.text))
            }
            post("/api/v1/ai/diary-summary") {
                val request = runCatching { call.receiveOrThrow<DiarySummaryRequest>() }.getOrDefault(DiarySummaryRequest())
                call.respond(services.aiService.diarySummary(call.userId(), request.days))
            }
        }
    }
}
