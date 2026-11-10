package io.medtech.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.medtech.backend.domain.PlanReplaceRequest
import io.medtech.backend.domain.SessionCreateRequest

fun Application.planRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            get("/api/v1/plan") {
                call.respond(services.planService.getOrGenerate(call.userId()))
            }
            put("/api/v1/plan") {
                val request = call.receiveOrThrow<PlanReplaceRequest>()
                call.respond(services.planService.replace(call.userId(), request.days))
            }
            post("/api/v1/plan/regenerate") {
                call.respond(services.planService.regenerate(call.userId()))
            }
            post("/api/v1/sessions") {
                val request = call.receiveOrThrow<SessionCreateRequest>()
                call.respond(services.analyticsService.recordSession(call.userId(), request))
            }
            get("/api/v1/sessions") {
                val from = parseInstantParam(call.request.queryParameters["from"])
                val to = parseInstantParam(call.request.queryParameters["to"])
                call.respond(services.analyticsService.listSessions(call.userId(), from, to))
            }
            get("/api/v1/analytics/overview") {
                call.respond(services.analyticsService.overview(call.userId()))
            }
        }
    }
}
