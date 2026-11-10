package io.medtech.backend.config

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val databaseUrl: String = "jdbc:postgresql://localhost:5432/medtech",
    val databaseUser: String = "medtech",
    val databasePassword: String = "",
    val jwtSecret: String = "dev-only-change-me",
    val jwtIssuer: String = "medtech-review-hub",
    val aiProvider: String = "none",
    val aiDailyTokenBudget: Int = 20000,
    val contentDir: String = "",
    val corsAllowAll: Boolean = true,
    val corsAllowedOrigins: List<String> = emptyList(),
    val mediaRoot: String = "media",
    val uploadMaxBytes: Long = 50L * 1024 * 1024,
    val generalRatePerMinute: Int = 100,
    val aiRatePerMinute: Int = 20,
    val adminEmails: Set<String> = emptySet(),
    val rateLimitingEnabled: Boolean = true,
) {
    companion object {
        fun load(config: ApplicationConfig? = null): AppConfig {
            val hocon = runCatching { ConfigFactory.load() }.getOrNull()
            fun str(path: String, env: String, default: String): String =
                config?.propertyOrNull(path)?.getString()
                    ?: hocon?.let { if (it.hasPath(path)) it.getString(path) else null }
                    ?: System.getenv(env)?.takeIf { it.isNotBlank() }
                    ?: default

            fun int(path: String, env: String, default: Int): Int =
                config?.propertyOrNull(path)?.getString()?.toIntOrNull()
                    ?: hocon?.let { if (it.hasPath(path)) it.getInt(path) else null }
                    ?: System.getenv(env)?.toIntOrNull()
                    ?: default

            fun long(path: String, env: String, default: Long): Long =
                config?.propertyOrNull(path)?.getString()?.toLongOrNull()
                    ?: hocon?.let { if (it.hasPath(path)) it.getLong(path) else null }
                    ?: System.getenv(env)?.toLongOrNull()
                    ?: default

            fun bool(path: String, env: String, default: Boolean): Boolean =
                config?.propertyOrNull(path)?.getString()?.toBooleanStrictOrNull()
                    ?: hocon?.let { if (it.hasPath(path)) it.getBoolean(path) else null }
                    ?: System.getenv(env)?.toBooleanStrictOrNull()
                    ?: default

            return AppConfig(
                port = int("ktor.deployment.port", "PORT", 8080),
                host = str("ktor.deployment.host", "HOST", "0.0.0.0"),
                databaseUrl = str("database.url", "DATABASE_URL", "jdbc:postgresql://localhost:5432/medtech"),
                databaseUser = str("database.user", "DATABASE_USER", "medtech"),
                databasePassword = str("database.password", "DATABASE_PASSWORD", ""),
                jwtSecret = str("jwt.secret", "JWT_SECRET", "dev-only-change-me"),
                jwtIssuer = str("jwt.issuer", "JWT_ISSUER", "medtech-review-hub"),
                aiProvider = str("ai.provider", "AI_PROVIDER", "none"),
                aiDailyTokenBudget = int("ai.dailyTokenBudget", "AI_DAILY_TOKEN_BUDGET", 20000),
                contentDir = str("content.dir", "CONTENT_DIR", ""),
                corsAllowAll = bool("cors.allowAll", "ALLOW_ALL_ORIGINS", true),
                corsAllowedOrigins = str("cors.allowedOrigins", "ALLOWED_ORIGINS", "")
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() },
                mediaRoot = str("media.root", "MEDIA_ROOT", "media"),
                uploadMaxBytes = long("uploads.maxBytes", "UPLOAD_MAX_BYTES", 50L * 1024 * 1024),
                generalRatePerMinute = int("rateLimit.generalPerMinute", "RATE_LIMIT_GENERAL", 100),
                aiRatePerMinute = int("rateLimit.aiPerMinute", "RATE_LIMIT_AI", 20),
                adminEmails = str("adminEmails", "ADMIN_EMAILS", "")
                    .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
            )
        }

        fun forTests(
            databaseUrl: String,
            mediaRoot: String = "build/test-media",
            uploadMaxBytes: Long = 50L * 1024 * 1024,
            aiProvider: String = "none",
        ): AppConfig = AppConfig(
            databaseUrl = databaseUrl,
            databaseUser = "sa",
            databasePassword = "",
            mediaRoot = mediaRoot,
            uploadMaxBytes = uploadMaxBytes,
            aiProvider = aiProvider,
            corsAllowAll = true,
            rateLimitingEnabled = true,
        )
    }
}
