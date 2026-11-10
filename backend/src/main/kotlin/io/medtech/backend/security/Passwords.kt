package io.medtech.backend.security

import io.medtech.backend.domain.ApiException
import org.mindrot.jbcrypt.BCrypt

object Passwords {
    private const val BCRYPT_COST = 10

    private val COMMON_PASSWORDS = setOf(
        "password123", "password1234", "password12345", "passw0rd123", "1234567890",
        "12345678901", "qwertyuiop", "qwerty12345", "letmein123", "welcome123",
        "iloveyou12", "admin12345", "administrator", "changeme123", "medtech123",
        "reviewhub123", "abcd123456", "football123", "baseball123", "monkey12345",
        "sunshine123", "princess123", "dragon12345", "trustno1234", "superman123",
        "starwars123", "whatever123", "computer123", "michael1234", "shadow12345",
        "master12345", "jennifer123", "jordan12345", "hunter12345", "harley12345",
        "ranger12345", "buster12345", "thomas12345", "robert12345", "daniel12345",
    )

    fun hash(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt(BCRYPT_COST))

    fun verify(plain: String, hash: String): Boolean = runCatching { BCrypt.checkpw(plain, hash) }.getOrDefault(false)

    fun validateNewPassword(password: String, email: String = "", displayName: String = "") {
        val problems = mutableMapOf<String, String>()
        if (password.length < 10) {
            problems["password"] = "Password must be at least 10 characters long"
        }
        if (password.length > 200) {
            problems["password"] = "Password must be at most 200 characters long"
        }
        val normalized = password.lowercase()
        if (normalized in COMMON_PASSWORDS) {
            problems["password"] = "Password is too common; choose something less guessable"
        }
        val localPart = email.substringBefore("@").lowercase()
        if (localPart.length >= 4 && normalized.contains(localPart)) {
            problems["password"] = "Password must not contain your email name"
        }
        if (displayName.isNotBlank() && displayName.length >= 4 && normalized.contains(displayName.lowercase())) {
            problems["password"] = "Password must not contain your display name"
        }
        if (normalized.matches(Regex("^[a-z]+$")) && password.length < 12) {
            problems["password"] = "Password must mix in digits or symbols"
        }
        if (problems.isNotEmpty()) {
            throw ApiException.validation("Password does not meet requirements", problems)
        }
    }
}
