package io.medtech.backend.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.ErrorBody
import io.medtech.backend.domain.ErrorCodes
import io.medtech.backend.domain.ErrorResponse

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(ErrorBody(cause.code, cause.message, cause.details)))
        }
        exception<Throwable> { call, cause ->
            if (cause is ApiException) {
                call.respond(cause.status, ErrorResponse(ErrorBody(cause.code, cause.message, cause.details)))
                return@exception
            }
            val name = cause::class.qualifiedName ?: ""
            val mapped = when {
                name.endsWith("NotFoundException") ->
                    HttpStatusCode.NotFound to ErrorBody(ErrorCodes.NOT_FOUND, "Resource not found")

                name.endsWith("BadRequestException") ||
                    name.endsWith("ContentTransformationException") ||
                    name.endsWith("JsonConvertException") ||
                    cause is kotlinx.serialization.SerializationException ||
                    cause is IllegalArgumentException ->
                    HttpStatusCode.BadRequest to ErrorBody(
                        ErrorCodes.VALIDATION_ERROR,
                        "Invalid request: ${cause.message ?: "unable to parse input"}",
                    )

                name.endsWith("AuthenticationException") ->
                    HttpStatusCode.Unauthorized to ErrorBody(ErrorCodes.UNAUTHORIZED, "Authentication required")

                name.endsWith("AuthorizationException") ->
                    HttpStatusCode.Forbidden to ErrorBody(ErrorCodes.FORBIDDEN, "You do not have access to this resource")

                else -> {
                    call.application.environment.log.error("Unhandled exception", cause)
                    HttpStatusCode.InternalServerError to ErrorBody(ErrorCodes.INTERNAL, "Unexpected server error")
                }
            }
            call.respond(mapped.first, ErrorResponse(mapped.second))
        }
    }
}
