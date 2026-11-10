package io.medtech.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.contentRoutes(services: AppServices) {
    routing {
        get("/api/v1/content/subjects") {
            call.respond(services.contentService.subjects())
        }
        get("/api/v1/content/lessons") {
            val subject = call.request.queryParameters["subject"]
            val topic = call.request.queryParameters["topic"]
            val q = call.request.queryParameters["q"]
            call.respond(services.contentService.lessonSummaries(subject, topic, q))
        }
        get("/api/v1/content/lessons/{id}") {
            val id = call.parameters["id"] ?: ""
            val lesson = services.contentService.lesson(id) ?: throw io.medtech.backend.domain.ApiException.notFound("Lesson not found")
            call.respond(lesson)
        }
        get("/api/v1/content/lessons/{subject}/{lesson}") {
            val id = "${call.parameters["subject"]}/${call.parameters["lesson"]}"
            val lesson = services.contentService.lesson(id) ?: throw io.medtech.backend.domain.ApiException.notFound("Lesson not found")
            call.respond(lesson)
        }
        get("/api/v1/content/questions") {
            val subject = call.request.queryParameters["subject"]
            val topic = call.request.queryParameters["topic"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            call.respond(services.contentService.questions(subject, topic, limit))
        }
        get("/api/v1/content/formulas") {
            call.respond(services.contentService.formulas())
        }
        get("/api/v1/content/memes") {
            call.respond(services.contentService.memes())
        }
    }
}
