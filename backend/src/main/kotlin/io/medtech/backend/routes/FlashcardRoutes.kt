package io.medtech.backend.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.FlashcardCreateRequest
import io.medtech.backend.domain.FlashcardUpdateRequest
import io.medtech.backend.domain.ImportRequest
import io.medtech.backend.domain.ReviewRequest

fun Application.flashcardRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/flashcards") {
                val deck = call.request.queryParameters["deck"]
                val tag = call.request.queryParameters["tag"]
                val dueOnly = call.request.queryParameters["due"] == "true"
                call.respond(services.flashcardService.list(call.userId(), deck, tag, dueOnly))
            }
            post("/api/v1/flashcards") {
                val request = call.receiveOrThrow<FlashcardCreateRequest>()
                call.respond(HttpStatusCode.Created, services.flashcardService.create(call.userId(), request))
            }
            get("/api/v1/flashcards/queue") {
                call.respond(services.flashcardService.queue(call.userId()))
            }
            post("/api/v1/flashcards/import") {
                val request = call.receiveOrThrow<ImportRequest>()
                call.respond(services.flashcardService.importRows(call.userId(), request))
            }
            get("/api/v1/flashcards/export") {
                val format = call.request.queryParameters["format"] ?: "csv"
                if (format != "csv") throw ApiException.validation("Only format=csv is supported")
                call.respondText(services.flashcardService.exportCsv(call.userId()), ContentType.Text.CSV)
            }
            patch("/api/v1/flashcards/{id}") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val request = call.receiveOrThrow<FlashcardUpdateRequest>()
                call.respond(services.flashcardService.update(call.userId(), id, request))
            }
            delete("/api/v1/flashcards/{id}") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                services.flashcardService.delete(call.userId(), id)
                call.respond(io.medtech.backend.domain.StatusResponse())
            }
            post("/api/v1/flashcards/{id}/review") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val request = call.receiveOrThrow<ReviewRequest>()
                call.respond(services.flashcardService.review(call.userId(), id, request))
            }
        }
    }
}
