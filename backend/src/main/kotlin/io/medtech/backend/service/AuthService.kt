package io.medtech.backend.service

import io.medtech.backend.config.AppConfig
import io.medtech.backend.domain.ApiException
import io.medtech.backend.domain.AuthResponse
import io.medtech.backend.domain.ForgotPasswordResponse
import io.medtech.backend.domain.Preferences
import io.medtech.backend.domain.Profile
import io.medtech.backend.repository.AccountDeletionRepository
import io.medtech.backend.repository.PreferencesRepository
import io.medtech.backend.repository.ProfileRepository
import io.medtech.backend.repository.RefreshTokenRepository
import io.medtech.backend.repository.UserRecord
import io.medtech.backend.repository.UserRepository
import io.medtech.backend.security.JwtService
import io.medtech.backend.security.Passwords
import io.medtech.backend.security.Sanitizer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class AuthService(
    private val config: AppConfig,
    private val users: UserRepository = UserRepository(),
    private val refreshTokens: RefreshTokenRepository = RefreshTokenRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
    private val preferences: PreferencesRepository = PreferencesRepository(),
    private val deletion: AccountDeletionRepository = AccountDeletionRepository(),
    private val jwtService: JwtService = JwtService(config),
) {
    companion object {
        const val REFRESH_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val secureRandom = SecureRandom()
        private val passwordResetTokens = ConcurrentHashMap<String, Pair<String, Long>>() // token -> userId to expiresAt
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResponse {
        val cleanEmail = Sanitizer.cleanToken(email, 320).lowercase()
        val cleanName = Sanitizer.clean(displayName, 120)
        if (!emailRegex.matches(cleanEmail)) {
            throw ApiException.validation("Email is not valid", mapOf("email" to "Must be a valid email address"))
        }
        if (cleanName.isBlank()) {
            throw ApiException.validation("Display name is required", mapOf("displayName" to "Must not be blank"))
        }
        Passwords.validateNewPassword(password, cleanEmail, cleanName)
        users.findByEmail(cleanEmail)?.let {
            throw ApiException.conflict("An account with this email already exists")
        }
        val role = if (cleanEmail in config.adminEmails) "ADMIN" else "USER"
        val user = users.create(cleanEmail, Passwords.hash(password), cleanName, role)
        profiles.upsert(user.id, Profile())
        preferences.upsert(user.id, Preferences())
        return issueTokens(user)
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val cleanEmail = Sanitizer.cleanToken(email, 320).lowercase()
        val user = users.findByEmail(cleanEmail)
            ?: throw ApiException.unauthorized("Invalid email or password")
        if (!Passwords.verify(password, user.passwordHash)) {
            throw ApiException.unauthorized("Invalid email or password")
        }
        return issueTokens(user)
    }

    suspend fun refresh(rawRefreshToken: String): AuthResponse {
        val record = refreshTokens.findValid(rawRefreshToken)
            ?: throw ApiException.unauthorized("Refresh token is invalid or expired")
        val user = users.findById(record.userId)
            ?: throw ApiException.unauthorized("Refresh token is invalid or expired")
        val newRaw = newToken()
        val newId = refreshTokens.issue(user.id, REFRESH_TTL_MILLIS, newRaw)
        refreshTokens.revoke(record.id, replacedBy = newId)
        return AuthResponse(
            user = user.toUser(),
            accessToken = jwtService.issueAccessToken(user.id, user.email, user.role),
            refreshToken = newRaw,
            expiresIn = jwtService.accessTtlSeconds,
        )
    }

    suspend fun logout(userId: String, rawRefreshToken: String?) {
        if (rawRefreshToken != null) {
            refreshTokens.revokeByRawToken(rawRefreshToken)
        } else {
            refreshTokens.revokeAllForUser(userId)
        }
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse {
        val cleanEmail = Sanitizer.cleanToken(email, 320).lowercase()
        val user = users.findByEmail(cleanEmail) ?: return ForgotPasswordResponse(status = "ok")
        val token = newToken()
        passwordResetTokens[token] = user.id to (System.currentTimeMillis() + 60 * 60 * 1000)
        // Mail is unconfigured in this deployment: return a dev token so the flow can be completed.
        return ForgotPasswordResponse(status = "ok", devToken = token)
    }

    suspend fun resetPassword(token: String, password: String) {
        val entry = passwordResetTokens.remove(token)
            ?: throw ApiException.validation("Reset token is invalid or expired")
        val (userId, expiresAt) = entry
        if (System.currentTimeMillis() > expiresAt) {
            throw ApiException.validation("Reset token is invalid or expired")
        }
        val user = users.findById(userId) ?: throw ApiException.validation("Reset token is invalid or expired")
        Passwords.validateNewPassword(password, user.email, user.displayName)
        users.updatePasswordHash(user.id, Passwords.hash(password))
        refreshTokens.revokeAllForUser(user.id)
    }

    suspend fun deleteAccount(userId: String) {
        refreshTokens.revokeAllForUser(userId)
        deletion.deleteAllForUser(userId)
    }

    private suspend fun issueTokens(user: UserRecord): AuthResponse {
        val refreshToken = newToken()
        refreshTokens.issue(user.id, REFRESH_TTL_MILLIS, refreshToken)
        return AuthResponse(
            user = user.toUser(),
            accessToken = jwtService.issueAccessToken(user.id, user.email, user.role),
            refreshToken = refreshToken,
            expiresIn = jwtService.accessTtlSeconds,
        )
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
