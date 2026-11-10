package com.medtech.reviewhub.domain

/**
 * Semantic version comparison used by the update checker.
 * Accepts an optional leading "v" (GitHub releases tag style), ignores build
 * metadata (+...), treats a missing pre-release as newer than any pre-release.
 */
data class Semver(val major: Int, val minor: Int, val patch: Int, val preRelease: String? = null) : Comparable<Semver> {

    override fun compareTo(other: Semver): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        val a = preRelease
        val b = other.preRelease
        return when {
            a == null && b == null -> 0
            a == null -> 1   // release > pre-release
            b == null -> -1
            else -> a.compareTo(b)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        fun parse(raw: String): Semver? {
            var s = raw.trim()
            if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)
            s = s.substringBefore('+')
            if (s.isEmpty()) return null
            val pre = s.substringAfter('-', missingDelimiterValue = "").ifEmpty { null }
            val core = s.substringBefore('-')
            val parts = core.split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            val major = nums.getOrElse(0) { 0 }
            val minor = nums.getOrElse(1) { 0 }
            val patch = nums.getOrElse(2) { 0 }
            return Semver(major, minor, patch, pre)
        }

        /** true when [candidate] is strictly newer than [current]. */
        fun isNewer(candidate: String, current: String): Boolean {
            val c = parse(candidate) ?: return false
            val cur = parse(current) ?: return true
            return c > cur
        }
    }
}
