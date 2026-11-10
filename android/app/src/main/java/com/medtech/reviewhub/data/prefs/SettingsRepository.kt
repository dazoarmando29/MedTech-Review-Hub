package com.medtech.reviewhub.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medtech.reviewhub.domain.model.HighlightColor
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScalePercent: Int = 100,
    val mascotKind: MascotKind = MascotKind.CAT,
    val seriousMode: Boolean = false,
    val reduceAnimation: Boolean = false,
    val muteMascot: Boolean = false,
    val disableCommentary: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val focusFeatureEnabled: Boolean = true,
    val apiBaseUrl: String = "",
    val signedInEmail: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val dismissedUpdateVersion: String? = null,
    val dailyNewLimit: Int = 20,
    val dailyReviewLimit: Int = 200,
    val requestedRetention: Float = 0.9f,
    val highlightMeanings: Map<HighlightColor, String> = defaultHighlightMeanings(),
    val pomodoroWorkMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val customWorkMinutes: Int = 30,
    val customBreakMinutes: Int = 5
) {
    companion object {
        fun defaultHighlightMeanings(): Map<HighlightColor, String> = mapOf(
            HighlightColor.YELLOW to "Must memorize",
            HighlightColor.RED to "High-priority exam fact",
            HighlightColor.BLUE to "Definition",
            HighlightColor.GREEN to "Understood",
            HighlightColor.PURPLE to "Needs study"
        )
    }
}

/**
 * All user settings live in DataStore. Everything works offline; the API base
 * URL defaults to empty which means "fully offline" (no network calls except
 * the optional GitHub update check).
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val TEXT_SCALE = intPreferencesKey("text_scale")
        val MASCOT = stringPreferencesKey("mascot_kind")
        val SERIOUS = booleanPreferencesKey("serious_mode")
        val REDUCE_ANIM = booleanPreferencesKey("reduce_animation")
        val MUTE = booleanPreferencesKey("mute_mascot")
        val NO_COMMENTARY = booleanPreferencesKey("disable_commentary")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val FOCUS_ENABLED = booleanPreferencesKey("focus_feature_enabled")
        val API_BASE = stringPreferencesKey("api_base_url")
        val EMAIL = stringPreferencesKey("signed_in_email")
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val DISMISSED_UPDATE = stringPreferencesKey("dismissed_update_version")
        val NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val RETENTION = floatPreferencesKey("requested_retention")
        val HL_YELLOW = stringPreferencesKey("hl_yellow")
        val HL_RED = stringPreferencesKey("hl_red")
        val HL_BLUE = stringPreferencesKey("hl_blue")
        val HL_GREEN = stringPreferencesKey("hl_green")
        val HL_PURPLE = stringPreferencesKey("hl_purple")
        val POM_WORK = intPreferencesKey("pomodoro_work")
        val POM_BREAK = intPreferencesKey("pomodoro_break")
        val CUSTOM_WORK = intPreferencesKey("custom_work")
        val CUSTOM_BREAK = intPreferencesKey("custom_break")
        val PROFILE = stringPreferencesKey("profile_json")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            textScalePercent = (p[Keys.TEXT_SCALE] ?: 100).coerceIn(100, 150),
            mascotKind = runCatching { MascotKind.valueOf(p[Keys.MASCOT] ?: "CAT") }.getOrDefault(MascotKind.CAT),
            seriousMode = p[Keys.SERIOUS] ?: false,
            reduceAnimation = p[Keys.REDUCE_ANIM] ?: false,
            muteMascot = p[Keys.MUTE] ?: false,
            disableCommentary = p[Keys.NO_COMMENTARY] ?: false,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            focusFeatureEnabled = p[Keys.FOCUS_ENABLED] ?: true,
            apiBaseUrl = p[Keys.API_BASE] ?: "",
            signedInEmail = p[Keys.EMAIL],
            accessToken = p[Keys.ACCESS],
            refreshToken = p[Keys.REFRESH],
            dismissedUpdateVersion = p[Keys.DISMISSED_UPDATE],
            dailyNewLimit = p[Keys.NEW_LIMIT] ?: 20,
            dailyReviewLimit = p[Keys.REVIEW_LIMIT] ?: 200,
            requestedRetention = p[Keys.RETENTION] ?: 0.9f,
            highlightMeanings = mapOf(
                HighlightColor.YELLOW to (p[Keys.HL_YELLOW] ?: "Must memorize"),
                HighlightColor.RED to (p[Keys.HL_RED] ?: "High-priority exam fact"),
                HighlightColor.BLUE to (p[Keys.HL_BLUE] ?: "Definition"),
                HighlightColor.GREEN to (p[Keys.HL_GREEN] ?: "Understood"),
                HighlightColor.PURPLE to (p[Keys.HL_PURPLE] ?: "Needs study")
            ),
            pomodoroWorkMinutes = p[Keys.POM_WORK] ?: 25,
            pomodoroBreakMinutes = p[Keys.POM_BREAK] ?: 5,
            customWorkMinutes = p[Keys.CUSTOM_WORK] ?: 30,
            customBreakMinutes = p[Keys.CUSTOM_BREAK] ?: 5
        )
    }

    suspend fun current(): AppSettings = settings.first()

    val profile: Flow<UserProfile> = context.dataStore.data.map { p ->
        runCatching { json.decodeFromString<UserProfile>(p[Keys.PROFILE] ?: "") }
            .getOrDefault(UserProfile())
    }

    suspend fun currentProfile(): UserProfile = profile.first()

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { it[Keys.PROFILE] = json.encodeToString(UserProfile.serializer(), profile) }
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setTextScale(percent: Int) = edit { it[Keys.TEXT_SCALE] = percent.coerceIn(100, 150) }
    suspend fun setMascotKind(kind: MascotKind) = edit { it[Keys.MASCOT] = kind.name }
    suspend fun setSeriousMode(enabled: Boolean) = edit { it[Keys.SERIOUS] = enabled }
    suspend fun setReduceAnimation(enabled: Boolean) = edit { it[Keys.REDUCE_ANIM] = enabled }
    suspend fun setMuteMascot(enabled: Boolean) = edit { it[Keys.MUTE] = enabled }
    suspend fun setDisableCommentary(enabled: Boolean) = edit { it[Keys.NO_COMMENTARY] = enabled }
    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS] = enabled }
    suspend fun setFocusFeatureEnabled(enabled: Boolean) = edit { it[Keys.FOCUS_ENABLED] = enabled }
    suspend fun setApiBaseUrl(url: String) = edit { it[Keys.API_BASE] = url.trim().trimEnd('/') }
    suspend fun setAuth(email: String?, access: String?, refresh: String?) = edit {
        if (email == null) it.remove(Keys.EMAIL) else it[Keys.EMAIL] = email
        if (access == null) it.remove(Keys.ACCESS) else it[Keys.ACCESS] = access
        if (refresh == null) it.remove(Keys.REFRESH) else it[Keys.REFRESH] = refresh
    }
    suspend fun setDismissedUpdateVersion(version: String) = edit { it[Keys.DISMISSED_UPDATE] = version }
    suspend fun setDailyLimits(newLimit: Int, reviewLimit: Int) = edit {
        it[Keys.NEW_LIMIT] = newLimit.coerceIn(0, 999)
        it[Keys.REVIEW_LIMIT] = reviewLimit.coerceIn(0, 999)
    }
    suspend fun setRequestedRetention(value: Float) = edit { it[Keys.RETENTION] = value.coerceIn(0.80f, 0.95f) }
    suspend fun setHighlightMeaning(color: HighlightColor, meaning: String) = edit {
        when (color) {
            HighlightColor.YELLOW -> it[Keys.HL_YELLOW] = meaning
            HighlightColor.RED -> it[Keys.HL_RED] = meaning
            HighlightColor.BLUE -> it[Keys.HL_BLUE] = meaning
            HighlightColor.GREEN -> it[Keys.HL_GREEN] = meaning
            HighlightColor.PURPLE -> it[Keys.HL_PURPLE] = meaning
        }
    }
    suspend fun setPomodoro(work: Int, breakMin: Int) = edit {
        it[Keys.POM_WORK] = work.coerceIn(5, 120)
        it[Keys.POM_BREAK] = breakMin.coerceIn(1, 60)
    }
    suspend fun setCustomPomodoro(work: Int, breakMin: Int) = edit {
        it[Keys.CUSTOM_WORK] = work.coerceIn(5, 120)
        it[Keys.CUSTOM_BREAK] = breakMin.coerceIn(1, 60)
    }

    suspend fun signOut() = edit {
        it.remove(Keys.EMAIL)
        it.remove(Keys.ACCESS)
        it.remove(Keys.REFRESH)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
