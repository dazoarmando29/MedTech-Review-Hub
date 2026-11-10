package io.medtech.backend.security

import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory token bucket rate limiter, keyed per user (when a bearer token is present)
 * or per client IP. Suitable for a single instance; replace with Redis for multi-instance
 * deployments (see backend/README.md).
 */
class TokenBucketLimiter(private val ratePerMinute: Int) {
    private class Bucket(var tokens: Double, var lastRefillMs: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun tryConsume(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (buckets.size > 10_000) {
            val cutoff = now - 10 * 60 * 1000
            buckets.entries.removeIf { it.value.lastRefillMs < cutoff }
        }
        val bucket = buckets.compute(key) { _, existing ->
            if (existing == null) {
                Bucket(ratePerMinute.toDouble(), now)
            } else {
                val elapsedSeconds = (now - existing.lastRefillMs) / 1000.0
                existing.tokens = minOf(
                    ratePerMinute.toDouble(),
                    existing.tokens + elapsedSeconds * ratePerMinute / 60.0,
                )
                existing.lastRefillMs = now
                existing
            }
        }!!
        synchronized(bucket) {
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                return true
            }
            return false
        }
    }
}

object RateLimitKeys {
    fun fromTokenOrIp(authorizationHeader: String?, remoteHost: String): String {
        val subject = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring(7)
            ?.let { parseSubjectUnverified(it) }
        return if (subject != null) "user:$subject" else "ip:$remoteHost"
    }

    fun parseUnverified(token: String): Pair<String?, String?> {
        val subject = parseSubjectUnverified(token)
        val role = parseClaimUnverified(token, "role")
        return subject to role
    }

    private fun parseSubjectUnverified(token: String): String? = parseClaimUnverified(token, "sub")

    private fun parseClaimUnverified(token: String, claim: String): String? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        val marker = "\"$claim\":\""
        val start = payload.indexOf(marker)
        if (start < 0) return null
        val begin = start + marker.length
        val end = payload.indexOf('"', begin)
        if (end < 0) return null
        payload.substring(begin, end)
    }.getOrNull()
}
