package com.medtech.reviewhub.data.network

import com.medtech.reviewhub.domain.Semver
import com.medtech.reviewhub.data.network.dto.GitHubRelease

/**
 * Update checker: compares the GitHub latest release tag with
 * BuildConfig.VERSION_NAME using semver. Never auto-downloads or installs
 * APKs — the user must sideload manually (documented in README).
 */
class UpdateChecker(
    private val gitHub: GitHubApi = NetworkModule.gitHub,
    private val currentVersion: String
) {

    data class UpdateInfo(
        val version: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val isNewer: Boolean
    )

    sealed class Result {
        data class Available(val info: UpdateInfo) : Result()
        object UpToDate : Result()
        data class Failure(val reason: String) : Result()
    }

    suspend fun check(dismissedVersion: String? = null): Result {
        return try {
            val response = gitHub.latestRelease()
            if (!response.isSuccessful || response.body() == null) {
                return Result.Failure("Update check failed (HTTP ${response.code()})")
            }
            toResult(response.body()!!, dismissedVersion)
        } catch (e: Exception) {
            Result.Failure("Update check failed: ${e.message ?: "network error"}")
        }
    }

    fun toResult(release: GitHubRelease, dismissedVersion: String? = null): Result {
        val tag = release.tagName ?: return Result.Failure("No release tag")
        val version = tag.removePrefix("v").removePrefix("V")
        val isNewer = Semver.isNewer(version, currentVersion)
        val dismissed = dismissedVersion != null && Semver.parse(version) == Semver.parse(dismissedVersion)
        return when {
            !isNewer -> Result.UpToDate
            dismissed -> Result.UpToDate
            else -> Result.Available(
                UpdateInfo(
                    version = version,
                    releaseNotes = release.body ?: release.name ?: "",
                    downloadUrl = release.htmlUrl ?: "",
                    isNewer = true
                )
            )
        }
    }
}
