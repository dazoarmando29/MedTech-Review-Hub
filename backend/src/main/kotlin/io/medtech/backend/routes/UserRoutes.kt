package io.medtech.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.UpdateMeRequest

fun Application.userRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/me") {
                call.respond(services.userService.getMe(call.userId()))
            }
            patch("/api/v1/me") {
                val request = call.receiveOrThrow<UpdateMeRequest>()
                call.respond(services.userService.updateMe(call.userId(), request))
            }
            get("/api/v1/me/export") {
                call.respond(services.userService.export(call.userId()))
            }
            put("/api/v1/me/profile") {
                val profile = call.receiveOrThrow<Profile>()
                call.respond(services.userService.putProfile(call.userId(), profile))
            }
        }
    }
}
