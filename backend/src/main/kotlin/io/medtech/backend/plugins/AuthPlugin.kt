package io.medtech.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.medtech.backend.config.AppConfig
import io.medtech.backend.domain.ErrorBody
import io.medtech.backend.domain.ErrorCodes
import io.medtech.backend.domain.ErrorResponse
import io.medtech.backend.security.JwtService
import io.medtech.backend.security.RateLimitKeys
import io.medtech.backend.security.TokenBucketLimiter

fun Application.configureAuth(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "medtech-review-hub"
            verifier(jwtService.verifier())
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ErrorBody(ErrorCodes.UNAUTHORIZED, "Invalid or expired access token")),
                )
            }
        }
    }
}

fun Application.configureRateLimit(config: AppConfig) {
    if (!config.rateLimitingEnabled) return
    val general = TokenBucketLimiter(config.generalRatePerMinute)
    val ai = TokenBucketLimiter(config.aiRatePerMinute)
    intercept(ApplicationCallPipeline.Plugins) {
        val appCall = context
        val path = appCall.request.origin.uri.substringBefore('?')
        val limiter = if (path.contains("/api/v1/ai/")) ai else general
        val key = RateLimitKeys.fromTokenOrIp(
            authorizationHeader = appCall.request.headers[HttpHeaders.Authorization],
            remoteHost = runCatching { appCall.request.origin.remoteHost }.getOrDefault("unknown"),
        )
        if (!limiter.tryConsume(key)) {
            appCall.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse(
                    ErrorBody(
                        ErrorCodes.RATE_LIMITED,
                        if (path.contains("/api/v1/ai/")) {
                            "AI rate limit exceeded: ${config.aiRatePerMinute} requests per minute"
                        } else {
                            "Rate limit exceeded: ${config.generalRatePerMinute} requests per minute"
                        },
                    ),
                ),
            )
            finish()
        }
    }
}
