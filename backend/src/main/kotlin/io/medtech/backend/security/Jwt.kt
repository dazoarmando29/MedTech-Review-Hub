package io.medtech.backend.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.medtech.backend.config.AppConfig
import java.util.Date

class JwtService(private val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    val accessTtlSeconds: Long = 3600

    fun verifier() = JWT.require(algorithm).withIssuer(config.jwtIssuer).build()

    fun issueAccessToken(userId: String, email: String, role: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(config.jwtIssuer)
            .withSubject(userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + accessTtlSeconds * 1000))
            .sign(algorithm)
    }
}
