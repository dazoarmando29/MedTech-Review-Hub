package io.medtech.backend.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.medtech.backend.domain.AnnotationReplaceRequest
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.StatusResponse
import io.ktor.utils.io.toByteArray

fun Application.pdfRoutes(services: AppServices) {
    routing {
        authenticate("auth-jwt") {
            post("/api/v1/pdfs") {
                val userId = call.userId()
                val maxBytes = services.config.uploadMaxBytes
                val multipart = call.receiveMultipart()
                var filename = "document.pdf"
                var contentType = "application/pdf"
                var bytes: ByteArray? = null
                var part: PartData? = multipart.readPart()
                while (part != null) {
                    when (val current = part) {
                        is PartData.FileItem -> {
                            filename = current.originalFileName ?: filename
                            current.contentType?.let { contentType = it.toString() }
                            bytes = readLimited(current.provider().toByteArray(), maxBytes)
                        }
                        else -> {}
                    }
                    part.dispose()
                    part = multipart.readPart()
                }
                val payload = bytes ?: throw ApiException.validation("Missing file part in multipart upload")
                call.respond(
                    HttpStatusCode.Created,
                    services.pdfService.upload(userId, filename, contentType, payload),
                )
            }
            get("/api/v1/pdfs") {
                call.respond(services.pdfService.list(call.userId()))
            }
            get("/api/v1/pdfs/{id}/file") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val (meta, content) = services.pdfService.file(call.userId(), id)
                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${meta.filename}\"")
                call.respondBytes(content, ContentType.Application.Pdf)
            }
            delete("/api/v1/pdfs/{id}") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                services.pdfService.delete(call.userId(), id)
                call.respond(StatusResponse())
            }
            get("/api/v1/pdfs/{id}/annotations") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                call.respond(services.pdfService.annotations(call.userId(), id))
            }
            put("/api/v1/pdfs/{id}/annotations") {
                val id = call.parameters["id"] ?: throw ApiException.validation("id is required")
                val request = call.receiveOrThrow<AnnotationReplaceRequest>()
                call.respond(services.pdfService.replaceAnnotations(call.userId(), id, request.annotations))
            }
        }
    }
}

private fun readLimited(bytes: ByteArray, maxBytes: Long): ByteArray {
    if (bytes.size.toLong() > maxBytes) {
        throw ApiException.payloadTooLarge("Upload exceeds the maximum size of $maxBytes bytes")
    }
    return bytes
}
