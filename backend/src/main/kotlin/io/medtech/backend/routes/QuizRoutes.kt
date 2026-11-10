package io.medtech.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.QuizAnswerRequest
import io.medtech.backend.domain.QuizStartRequest
import io.medtech.backend.domain.QuizStatus
import io.medtech.backend.domain.ReportRequest

fun Application.quizRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            post("/api/v1/quizzes/start") {
                val request = call.receiveOrThrow<QuizStartRequest>()
                call.respond(HttpStatusCode.Created, services.quizService.start(call.userId(), request))
            }
            post("/api/v1/quizzes/{id}/answer") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val request = call.receiveOrThrow<QuizAnswerRequest>()
                call.respond(services.quizService.answer(call.userId(), id, request))
            }
            post("/api/v1/quizzes/{id}/finish") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.quizService.finish(call.userId(), id))
            }
            get("/api/v1/quizzes/{id}") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.quizService.get(call.userId(), id))
            }
            get("/api/v1/quizzes") {
                val status = call.request.queryParameters["status"]?.let {
                    runCatching { QuizStatus.valueOf(it.uppercase()) }.getOrNull()
                }
                call.respond(services.quizService.list(call.userId(), status))
            }
            post("/api/v1/questions/{id}/report") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val request = call.receiveOrThrow<ReportRequest>()
                call.respond(HttpStatusCode.Created, services.quizService.report(call.userId(), id, request))
            }
        }
    }
}
