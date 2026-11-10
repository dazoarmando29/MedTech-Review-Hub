package io.medtech.backend.repository

import io.medtech.backend.db.MascotPreferences
import io.medtech.backend.db.NotificationPreferences
import io.medtech.backend.db.Profiles
import io.medtech.backend.db.RefreshTokens
import io.medtech.backend.db.Users
import io.medtech.backend.db.newId
import io.medtech.backend.db.nowMillis
import io.medtech.backend.db.toInstant
import io.medtech.backend.db.tx
import io.medtech.backend.domain.Preferences
import io.medtech.backend.domain.Profile
import io.medtech.backend.domain.User
import io.medtech.backend.domain.decodeJson
import io.medtech.backend.domain.encodeJson
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest

data class UserRecord(
    val id: String,
    val email: String,
    val passwordHash: String,
    val displayName: String,
    val role: String,
    val createdAt: Instant,
) {
    fun toUser() = User(id = id, email = email, displayName = displayName, createdAt = createdAt)
}

class UserRepository {

    suspend fun create(email: String, passwordHash: String, displayName: String, role: String = "USER"): UserRecord = tx {
        val id = newId()
        val now = nowMillis()
        Users.insert {
            it[Users.id] = id
            it[Users.email] = email.lowercase()
            it[Users.passwordHash] = passwordHash
            it[Users.displayName] = displayName
            it[Users.role] = role
            it[Users.createdAt] = now
        }
        UserRecord(id, email.lowercase(), passwordHash, displayName, role, now.toInstant())
    }

    suspend fun findByEmail(email: String): UserRecord? = tx {
        Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()?.toRecord()
    }

    suspend fun findById(id: String): UserRecord? = tx {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toRecord()
    }

    suspend fun updateDisplayName(id: String, displayName: String) = tx {
        Users.update({ Users.id eq id }) { it[Users.displayName] = displayName }
    }

    suspend fun updatePasswordHash(id: String, passwordHash: String) = tx {
        Users.update({ Users.id eq id }) { it[Users.passwordHash] = passwordHash }
    }

    suspend fun updateRole(id: String, role: String) = tx {
        Users.update({ Users.id eq id }) { it[Users.role] = role }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toRecord() = UserRecord(
        id = this[Users.id],
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        displayName = this[Users.displayName],
        role = this[Users.role],
        createdAt = this[Users.createdAt].toInstant(),
    )
}

class RefreshTokenRepository {

    companion object {
        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    data class RefreshTokenRecord(
        val id: String,
        val userId: String,
        val tokenHash: String,
        val expiresAt: Instant,
        val revokedAt: Instant?,
    )

    suspend fun issue(userId: String, ttlMillis: Long, rawToken: String, replacedBy: String? = null): String = tx {
        val id = newId()
        RefreshTokens.insert {
            it[RefreshTokens.id] = id
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = hash(rawToken)
            it[RefreshTokens.createdAt] = nowMillis()
            it[RefreshTokens.expiresAt] = nowMillis() + ttlMillis
            it[RefreshTokens.replacedBy] = replacedBy
        }
        id
    }

    suspend fun findValid(rawToken: String): RefreshTokenRecord? = tx {
        RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq hash(rawToken) }
            .singleOrNull()
            ?.takeIf { it[RefreshTokens.revokedAt] == null && it[RefreshTokens.expiresAt] > nowMillis() }
            ?.let {
                RefreshTokenRecord(
                    id = it[RefreshTokens.id],
                    userId = it[RefreshTokens.userId],
                    tokenHash = it[RefreshTokens.tokenHash],
                    expiresAt = it[RefreshTokens.expiresAt].toInstant(),
                    revokedAt = it[RefreshTokens.revokedAt]?.toInstant(),
                )
            }
    }

    suspend fun revoke(id: String, replacedBy: String? = null) = tx {
        RefreshTokens.update({ RefreshTokens.id eq id }) {
            it[revokedAt] = nowMillis()
            it[RefreshTokens.replacedBy] = replacedBy
        }
    }

    suspend fun revokeByRawToken(rawToken: String) = tx {
        RefreshTokens.update({ RefreshTokens.tokenHash eq hash(rawToken) }) { it[revokedAt] = nowMillis() }
    }

    suspend fun revokeAllForUser(userId: String) = tx {
        RefreshTokens.update({ RefreshTokens.userId eq userId }) { it[revokedAt] = nowMillis() }
    }

    suspend fun deleteAllForUser(userId: String) = tx {
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
    }
}

class ProfileRepository {

    suspend fun get(userId: String): Profile? = tx {
        Profiles.selectAll().where { Profiles.userId eq userId }.singleOrNull()?.let { row ->
            Profile(
                examTarget = runCatching { io.medtech.backend.domain.ExamTarget.valueOf(row[Profiles.examTarget]) }
                    .getOrDefault(io.medtech.backend.domain.ExamTarget.CUSTOM),
                targetExamDate = row[Profiles.targetExamDate]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                knowledgeLevel = runCatching { io.medtech.backend.domain.KnowledgeLevel.valueOf(row[Profiles.knowledgeLevel]) }
                    .getOrDefault(io.medtech.backend.domain.KnowledgeLevel.BEGINNER),
                strongestSubjects = decodeJson(row[Profiles.strongestSubjects]),
                weakestSubjects = decodeJson(row[Profiles.weakestSubjects]),
                minutesPerDay = row[Profiles.minutesPerDay],
                preferredSchedule = decodeJson(row[Profiles.preferredSchedule]),
                learningPreferences = decodeJson(row[Profiles.learningPreferences]),
                explanationStyle = runCatching { io.medtech.backend.domain.ExplanationStyle.valueOf(row[Profiles.explanationStyle]) }
                    .getOrDefault(io.medtech.backend.domain.ExplanationStyle.FORMAL_ENGLISH),
                notificationsEnabled = row[Profiles.notificationsEnabled],
                focusFeatureEnabled = row[Profiles.focusFeatureEnabled],
                onboardingCompleted = row[Profiles.onboardingCompleted],
            )
        }
    }

    suspend fun upsert(userId: String, profile: Profile) = tx {
        val exists = Profiles.selectAll().where { Profiles.userId eq userId }.singleOrNull() != null
        if (exists) {
            Profiles.update({ Profiles.userId eq userId }) {
                it[examTarget] = profile.examTarget.name
                it[targetExamDate] = profile.targetExamDate?.toString()
                it[knowledgeLevel] = profile.knowledgeLevel.name
                it[strongestSubjects] = encodeJson(profile.strongestSubjects)
                it[weakestSubjects] = encodeJson(profile.weakestSubjects)
                it[minutesPerDay] = profile.minutesPerDay
                it[preferredSchedule] = encodeJson(profile.preferredSchedule)
                it[learningPreferences] = encodeJson(profile.learningPreferences)
                it[explanationStyle] = profile.explanationStyle.name
                it[notificationsEnabled] = profile.notificationsEnabled
                it[focusFeatureEnabled] = profile.focusFeatureEnabled
                it[onboardingCompleted] = profile.onboardingCompleted
                it[updatedAt] = nowMillis()
            }
        } else {
            Profiles.insert {
                it[Profiles.userId] = userId
                it[examTarget] = profile.examTarget.name
                it[targetExamDate] = profile.targetExamDate?.toString()
                it[knowledgeLevel] = profile.knowledgeLevel.name
                it[strongestSubjects] = encodeJson(profile.strongestSubjects)
                it[weakestSubjects] = encodeJson(profile.weakestSubjects)
                it[minutesPerDay] = profile.minutesPerDay
                it[preferredSchedule] = encodeJson(profile.preferredSchedule)
                it[learningPreferences] = encodeJson(profile.learningPreferences)
                it[explanationStyle] = profile.explanationStyle.name
                it[notificationsEnabled] = profile.notificationsEnabled
                it[focusFeatureEnabled] = profile.focusFeatureEnabled
                it[onboardingCompleted] = profile.onboardingCompleted
                it[updatedAt] = nowMillis()
            }
        }
    }

    suspend fun deleteForUser(userId: String) = tx {
        Profiles.deleteWhere { Profiles.userId eq userId }
    }
}

class PreferencesRepository {

    suspend fun get(userId: String): Preferences = tx {
        val mascot = MascotPreferences.selectAll().where { MascotPreferences.userId eq userId }.singleOrNull()
        Preferences(
            avatarMascot = mascot?.get(MascotPreferences.avatarMascot) ?: "cat",
            locale = mascot?.get(MascotPreferences.locale) ?: "en",
            theme = mascot?.get(MascotPreferences.theme) ?: "system",
            seriousMode = mascot?.get(MascotPreferences.seriousMode) ?: false,
        )
    }

    suspend fun upsert(userId: String, prefs: Preferences) = tx {
        val exists = MascotPreferences.selectAll().where { MascotPreferences.userId eq userId }.singleOrNull() != null
        if (exists) {
            MascotPreferences.update({ MascotPreferences.userId eq userId }) {
                it[avatarMascot] = prefs.avatarMascot
                it[locale] = prefs.locale
                it[theme] = prefs.theme
                it[seriousMode] = prefs.seriousMode
                it[updatedAt] = nowMillis()
            }
        } else {
            MascotPreferences.insert {
                it[MascotPreferences.userId] = userId
                it[avatarMascot] = prefs.avatarMascot
                it[locale] = prefs.locale
                it[theme] = prefs.theme
                it[seriousMode] = prefs.seriousMode
                it[updatedAt] = nowMillis()
            }
        }
    }

    suspend fun notificationFor(userId: String): Boolean = tx {
        NotificationPreferences.selectAll().where { NotificationPreferences.userId eq userId }
            .singleOrNull()?.get(NotificationPreferences.enabled) ?: true
    }

    suspend fun upsertNotifications(userId: String, enabled: Boolean) = tx {
        val exists = NotificationPreferences.selectAll().where { NotificationPreferences.userId eq userId }.singleOrNull() != null
        if (exists) {
            NotificationPreferences.update({ NotificationPreferences.userId eq userId }) {
                it[NotificationPreferences.enabled] = enabled
                it[updatedAt] = nowMillis()
            }
        } else {
            NotificationPreferences.insert {
                it[NotificationPreferences.userId] = userId
                it[NotificationPreferences.enabled] = enabled
                it[updatedAt] = nowMillis()
            }
        }
    }

    suspend fun deleteForUser(userId: String) = tx {
        MascotPreferences.deleteWhere { MascotPreferences.userId eq userId }
        NotificationPreferences.deleteWhere { NotificationPreferences.userId eq userId }
    }
}
