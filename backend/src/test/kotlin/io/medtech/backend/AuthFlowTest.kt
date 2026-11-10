package io.medtech.backend

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.medtech.backend.TestApi.bearer
import io.medtech.backend.TestApi.jsonBody
import io.medtech.backend.TestApi.login
import io.medtech.backend.TestApi.register
import io.medtech.backend.TestApi.text
import io.medtech.backend.domain.AuthResponse
import io.medtech.backend.domain.RefreshRequest
import io.medtech.backend.domain.UserResponse
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthFlowTest {

    @Test
    fun `register login get me refresh delete then login fails`() = withApp {
        val auth = register("student@example.com", displayName = "Dazz")
        assertEquals("student@example.com", auth.user.email)
        assertEquals("Dazz", auth.user.displayName)
        assertTrue(auth.accessToken.isNotBlank())
        assertTrue(auth.refreshToken.isNotBlank())
        assertEquals(3600, auth.expiresIn)

        val login = login("student@example.com")
        assertEquals(auth.user.id, login.user.id)

        val me = client.get("/api/v1/me") { bearer(login.accessToken) }
        assertEquals(HttpStatusCode.OK, me.status)
        val meBody: UserResponse = decodeJson(me.text())
        assertEquals("Dazz", meBody.user.displayName)

        // Refresh rotates the refresh token.
        val refreshed = client.post("/api/v1/auth/refresh") {
            jsonBody(encodeJson(RefreshRequest(auth.refreshToken)))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val refreshedBody: AuthResponse = decodeJson(refreshed.text())
        assertEquals(auth.user.id, refreshedBody.user.id)
        assertTrue(refreshedBody.refreshToken != auth.refreshToken, "refresh token must rotate")

        // The rotated-out token is no longer valid.
        val reuse = client.post("/api/v1/auth/refresh") {
            jsonBody(encodeJson(RefreshRequest(auth.refreshToken)))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)

        // Delete account.
        val delete = client.delete("/api/v1/auth/me") { bearer(refreshedBody.accessToken) }
        assertEquals(HttpStatusCode.OK, delete.status)

        // Login must fail after deletion.
        val loginAfter = client.post("/api/v1/auth/login") {
            jsonBody(encodeJson(mapOf("email" to "student@example.com", "password" to TestApi.GOOD_PASSWORD)))
        }
        assertEquals(HttpStatusCode.Unauthorized, loginAfter.status)

        // Access token of deleted user is still cryptographically valid but user lookup fails.
        val meAfter = client.get("/api/v1/me") { bearer(refreshedBody.accessToken) }
        assertTrue(
            meAfter.status == HttpStatusCode.NotFound || meAfter.status == HttpStatusCode.Unauthorized,
            "expected 404/404 envelope after deletion, got ${meAfter.status}",
        )
    }

    @Test
    fun `password validation rejects short and common passwords`() = withApp {
        val short = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(mapOf("email" to "short@example.com", "password" to "short1", "displayName" to "Short")))
        }
        assertEquals(HttpStatusCode.BadRequest, short.status)
        assertTrue(short.text().contains("VALIDATION_ERROR"), short.text())

        val common = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(mapOf("email" to "common@example.com", "password" to "password123", "displayName" to "Common")))
        }
        assertEquals(HttpStatusCode.BadRequest, common.status)
        assertTrue(common.text().contains("VALIDATION_ERROR"), common.text())

        val digitsOnly = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(mapOf("email" to "digits@example.com", "password" to "1234567890", "displayName" to "Digits")))
        }
        assertEquals(HttpStatusCode.BadRequest, digitsOnly.status)

        val valid = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(mapOf("email" to "ok@example.com", "password" to TestApi.GOOD_PASSWORD, "displayName" to "Ok")))
        }
        assertEquals(HttpStatusCode.Created, valid.status)
    }

    @Test
    fun `duplicate email conflicts and forgot-password issues dev token`() = withApp {
        register("dupe@example.com")
        val dupe = client.post("/api/v1/auth/register") {
            jsonBody(encodeJson(mapOf("email" to "dupe@example.com", "password" to TestApi.GOOD_PASSWORD, "displayName" to "Dupe")))
        }
        assertEquals(HttpStatusCode.Conflict, dupe.status)

        val forgot = client.post("/api/v1/auth/forgot-password") {
            jsonBody(encodeJson(mapOf("email" to "dupe@example.com")))
        }
        assertEquals(HttpStatusCode.OK, forgot.status)
        assertTrue(forgot.text().contains("devToken"), forgot.text())
    }
}
