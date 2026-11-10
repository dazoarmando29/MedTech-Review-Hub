package io.medtech.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.ApiException
import io.medtech.backend.service.AdminLessonUpsert
import io.medtech.backend.service.AdminQuestionUpsert
import io.medtech.backend.service.AdminSubjectUpsert
import io.medtech.backend.service.AdminTopicUpsert

fun Application.adminRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/admin/subjects") {
                services.adminService.requireAdmin(call.userId())
                call.respond(services.adminService.listSubjects())
            }
            post("/api/v1/admin/subjects") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                call.respond(HttpStatusCode.Created, services.adminService.createSubject(actor, call.receiveOrThrow()))
            }
            patch("/api/v1/admin/subjects/{id}") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.adminService.updateSubject(actor, id, call.receiveOrThrow()))
            }

            get("/api/v1/admin/topics") {
                services.adminService.requireAdmin(call.userId())
                call.respond(services.adminService.listTopics(call.request.queryParameters["subject"]))
            }
            post("/api/v1/admin/topics") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                call.respond(HttpStatusCode.Created, services.adminService.createTopic(actor, call.receiveOrThrow()))
            }
            patch("/api/v1/admin/topics/{id}") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.adminService.updateTopic(actor, id, call.receiveOrThrow()))
            }

            get("/api/v1/admin/lessons") {
                services.adminService.requireAdmin(call.userId())
                call.respond(
                    services.adminService.listLessons(
                        call.request.queryParameters["subject"],
                        call.request.queryParameters["topic"],
                    ),
                )
            }
            post("/api/v1/admin/lessons") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                call.respond(HttpStatusCode.Created, services.adminService.createLesson(actor, call.receiveOrThrow()))
            }
            patch("/api/v1/admin/lessons/{id}") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.adminService.updateLesson(actor, id, call.receiveOrThrow()))
            }
            post("/api/v1/admin/lessons/{id}/publish") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.adminService.publishLesson(actor, id))
            }

            get("/api/v1/admin/questions") {
                services.adminService.requireAdmin(call.userId())
                call.respond(
                    services.adminService.listQuestions(
                        call.request.queryParameters["subject"],
                        call.request.queryParameters["topic"],
                    ),
                )
            }
            post("/api/v1/admin/questions") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                call.respond(HttpStatusCode.Created, services.adminService.createQuestion(actor, call.receiveOrThrow()))
            }
            patch("/api/v1/admin/questions/{id}") {
                val actor = call.userId()
                services.adminService.requireAdmin(actor)
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.adminService.updateQuestion(actor, id, call.receiveOrThrow()))
            }

            get("/api/v1/admin/reports") {
                services.adminService.requireAdmin(call.userId())
                call.respond(services.adminService.reports())
            }
            get("/api/v1/admin/audit") {
                services.adminService.requireAdmin(call.userId())
                call.respond(services.adminService.auditTrail())
            }
        }
    }
}
