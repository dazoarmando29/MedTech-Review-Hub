package io.medtech.backend

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.medtech.backend.config.AppConfig
import io.medtech.backend.domain.AuthResponse
import io.medtech.backend.domain.RegisterRequest
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import java.util.UUID

object TestApi {

    const val GOOD_PASSWORD = "supersecret99"

    fun newDbUrl(): String =
        "jdbc:h2:mem:t${UUID.randomUUID().toString().replace("-", "")};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"

    fun config(
        dbUrl: String = newDbUrl(),
        uploadMaxBytes: Long = 50L * 1024 * 1024,
        aiProvider: String = "none",
    ): AppConfig = AppConfig.forTests(databaseUrl = dbUrl, uploadMaxBytes = uploadMaxBytes, aiProvider = aiProvider)

    suspend fun ApplicationTestBuilder.register(
        email: String,
        password: String = GOOD_PASSWORD,
        displayName: String = "Test User",
    ): AuthResponse {
        val response = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(RegisterRequest(email, password, displayName)))
        }
        check(response.status == HttpStatusCode.Created) {
            "register failed: ${response.status} ${response.bodyAsText()}"
        }
        return decodeJson(response.bodyAsText())
    }

    suspend fun ApplicationTestBuilder.login(email: String, password: String = GOOD_PASSWORD): AuthResponse {
        val response = client.post("/api/v1/auth/login") {
            jsonBody(encodeJson(mapOf("email" to email, "password" to password)))
        }
        check(response.status == HttpStatusCode.OK) {
            "login failed: ${response.status} ${response.bodyAsText()}"
        }
        return decodeJson(response.bodyAsText())
    }

    suspend fun ApplicationTestBuilder.promoteToAdmin(userId: String) {
        io.medtech.backend.db.tx {
            io.medtech.backend.db.Users.update({ io.medtech.backend.db.Users.id eq userId }) {
                it[io.medtech.backend.db.Users.role] = "ADMIN"
            }
        }
    }

    fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    fun HttpRequestBuilder.jsonBody(content: String) {
        contentType(ContentType.Application.Json)
        setBody(content)
    }

    suspend fun HttpResponse.text(): String = bodyAsText()

    suspend inline fun <reified T> HttpResponse.json(): T = decodeJson(bodyAsText())

    fun pdfBytes(size: Int = 256): ByteArray {
        val header = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
        val body = ByteArray(size - header.size) { 'a'.code.toByte() }
        return header + body
    }

    fun multipartPdf(filename: String, bytes: ByteArray, contentType: String = "application/pdf"): OutgoingContent =
        MultiPartFormDataContent(
            formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    },
                )
            },
        )
}

fun <T> withApp(
    config: AppConfig = TestApi.config(),
    block: suspend ApplicationTestBuilder.() -> T,
): T {
    var result: T? = null
    testApplication {
        application {
            module(config)
        }
        result = block()
    }
    return result!!
}
