package io.medtech.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.DiaryCreateRequest
import io.medtech.backend.domain.HabitCreateRequest
import io.medtech.backend.domain.HabitLogRequest
import io.medtech.backend.domain.StatusResponse

fun Application.habitRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/habits") {
                call.respond(services.habitService.habits(call.userId()))
            }
            post("/api/v1/habits") {
                val request = call.receiveOrThrow<HabitCreateRequest>()
                call.respond(HttpStatusCode.Created, services.habitService.createHabit(call.userId(), request))
            }
            delete("/api/v1/habits/{id}") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                services.habitService.deleteHabit(call.userId(), id)
                call.respond(StatusResponse())
            }
            post("/api/v1/habits/log") {
                val request = call.receiveOrThrow<HabitLogRequest>()
                call.respond(HttpStatusCode.Created, services.habitService.logHabit(call.userId(), request))
            }
            get("/api/v1/diary") {
                call.respond(services.habitService.diary(call.userId()))
            }
            post("/api/v1/diary") {
                val request = call.receiveOrThrow<DiaryCreateRequest>()
                call.respond(HttpStatusCode.Created, services.habitService.createDiary(call.userId(), request))
            }
            get("/api/v1/streak") {
                call.respond(services.habitService.streak(call.userId()))
            }
        }
    }
}
