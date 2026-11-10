package com.medtech.reviewhub.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.medtech.reviewhub.data.network.ApiService
import com.medtech.reviewhub.data.network.NetworkModule
import com.medtech.reviewhub.data.network.dto.LoginRequest
import com.medtech.reviewhub.data.network.dto.RegisterRequest
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.data.sync.SyncWorker

/**
 * Optional account sign-in (backend /auth/ endpoints). When no API base URL is
 * configured or the network is down, everything still works locally — sync is
 * best-effort and queued through WorkManager.
 */
class AuthRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {

    sealed class AuthResult {
        data class Success(val email: String) : AuthResult()
        data class Failure(val message: String) : AuthResult()
        object Offline : AuthResult()
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        val base = settings.current().apiBaseUrl
        if (base.isBlank()) return AuthResult.Offline
        return try {
            val response = NetworkModule.api(base).login(LoginRequest(email, password))
            if (response.isSuccessful && response.body()?.accessToken != null) {
                val body = response.body()!!
                settings.setAuth(email, body.accessToken, body.refreshToken)
                requestSync()
                AuthResult.Success(email)
            } else {
                AuthResult.Failure("Sign-in failed (HTTP ${response.code()}). Check your credentials.")
            }
        } catch (e: Exception) {
            AuthResult.Failure("Sign-in failed: ${e.message ?: "network error"}")
        }
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResult {
        val base = settings.current().apiBaseUrl
        if (base.isBlank()) return AuthResult.Offline
        return try {
            val response = NetworkModule.api(base).register(RegisterRequest(email, password, displayName))
            if (response.isSuccessful && response.body()?.accessToken != null) {
                val body = response.body()!!
                settings.setAuth(email, body.accessToken, body.refreshToken)
                requestSync()
                AuthResult.Success(email)
            } else {
                AuthResult.Failure("Registration failed (HTTP ${response.code()}). Password needs 10+ characters.")
            }
        } catch (e: Exception) {
            AuthResult.Failure("Registration failed: ${e.message ?: "network error"}")
        }
    }

    suspend fun signOut() {
        settings.signOut()
    }

    suspend fun isSignedIn(): Boolean = !settings.current().accessToken.isNullOrBlank()

    /** Queues a best-effort background sync (no-op when signed out). */
    fun requestSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
