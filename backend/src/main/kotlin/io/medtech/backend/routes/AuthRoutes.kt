package io.medtech.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.medtech.backend.domain.ForgotPasswordRequest
import io.medtech.backend.domain.LoginRequest
import io.medtech.backend.domain.LogoutRequest
import io.medtech.backend.domain.RefreshRequest
import io.medtech.backend.domain.RegisterRequest
import io.medtech.backend.domain.ResetPasswordRequest
import io.medtech.backend.domain.StatusResponse

fun Application.authRoutes(services: AppServices) {
    routing {
        post("/api/v1/auth/register") {
            val request = call.receiveOrThrow<RegisterRequest>()
            call.respond(HttpStatusCode.Created, services.authService.register(request.email, request.password, request.displayName))
        }
        post("/api/v1/auth/login") {
            val request = call.receiveOrThrow<LoginRequest>()
            call.respond(services.authService.login(request.email, request.password))
        }
        post("/api/v1/auth/refresh") {
            val request = call.receiveOrThrow<RefreshRequest>()
            call.respond(services.authService.refresh(request.refreshToken))
        }
        post("/api/v1/auth/forgot-password") {
            val request = call.receiveOrThrow<ForgotPasswordRequest>()
            call.respond(services.authService.forgotPassword(request.email))
        }
        post("/api/v1/auth/reset-password") {
            val request = call.receiveOrThrow<ResetPasswordRequest>()
            services.authService.resetPassword(request.token, request.password)
            call.respond(StatusResponse())
        }
        authenticate("auth-jwt") {
            post("/api/v1/auth/logout") {
                val userId = call.userId()
                val request = runCatching { call.receive<LogoutRequest>() }.getOrDefault(LogoutRequest())
                services.authService.logout(userId, request.refreshToken)
                call.respond(StatusResponse())
            }
            delete("/api/v1/auth/me") {
                val userId = call.userId()
                services.authService.deleteAccount(userId)
                call.respond(StatusResponse())
            }
        }
    }
}
