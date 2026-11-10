package io.medtech.backend.routes

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.medtech.backend.db.Users
import io.medtech.backend.db.tx
import io.medtech.backend.domain.HealthResponse
import org.jetbrains.exposed.sql.selectAll

fun Application.healthRoutes() {
    routing {
        get("/health") { call.respond(healthPayload()) }
        get("/api/v1/health") { call.respond(healthPayload()) }
    }
}

private suspend fun healthPayload(): HealthResponse {
    val db = runCatching {
        tx { Users.selectAll().limit(1).count() }
    }.fold(onSuccess = { "ok" }, onFailure = { "error" })
    return HealthResponse(status = if (db == "ok") "ok" else "degraded", database = db)
}
