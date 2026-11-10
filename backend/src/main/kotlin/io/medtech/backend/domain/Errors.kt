package io.medtech.backend.domain

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class ErrorResponse(val error: ErrorBody)

object ErrorCodes {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    const val INTERNAL = "INTERNAL"
    const val AI_UNAVAILABLE = "AI_UNAVAILABLE"
}

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message) {
    companion object {
        fun validation(message: String, details: Map<String, String> = emptyMap()) =
            ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION_ERROR, message, details)

        fun unauthorized(message: String = "Authentication required") =
            ApiException(HttpStatusCode.Unauthorized, ErrorCodes.UNAUTHORIZED, message)

        fun forbidden(message: String = "You do not have access to this resource") =
            ApiException(HttpStatusCode.Forbidden, ErrorCodes.FORBIDDEN, message)

        fun notFound(message: String = "Resource not found") =
            ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, message)

        fun conflict(message: String) =
            ApiException(HttpStatusCode.Conflict, ErrorCodes.CONFLICT, message)

        fun rateLimited(message: String = "Too many requests, slow down") =
            ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED, message)

        fun payloadTooLarge(message: String) =
            ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.PAYLOAD_TOO_LARGE, message)

        fun aiUnavailable() =
            ApiException(
                HttpStatusCode.ServiceUnavailable,
                ErrorCodes.AI_UNAVAILABLE,
                "AI features are not configured on this server",
            )

        fun internal(message: String = "Unexpected server error") =
            ApiException(HttpStatusCode.InternalServerError, ErrorCodes.INTERNAL, message)
    }
}
