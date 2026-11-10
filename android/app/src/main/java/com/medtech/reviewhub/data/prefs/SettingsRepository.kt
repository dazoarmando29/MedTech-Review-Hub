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
    val mascotName: String = DEFAULT_MASCOT_NAME,
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
    val customBreakMinutes: Int = 5,
    /** User-selectable theme (shared/DESIGN.md v2): strawberry (default), sakura, lavender, night, minimal, system. */
    val appTheme: String = DEFAULT_APP_THEME,
    /** Personalized dashboard greeting name. */
    val displayName: String = DEFAULT_DISPLAY_NAME,
    /** Optional gift note card text. Blank = show the default gift note. */
    val giftNote: String = "",
    // ---- Study Music (v1.2.0) ----
    /** Default playlist id (suggested playlist id or user playlist id). */
    val defaultPlaylistId: String = "",
    /** Preferred music volume 0..1. */
    val musicVolume: Float = 0.8f,
    /** Auto-play music when a study session starts - only after the user has pressed play at least once. */
    val autoPlayMusicOnSession: Boolean = false,
    /** True once the user has ever pressed play (never force audio without interaction). */
    val musicUserInteracted: Boolean = false,
    val musicDuringQuizzes: Boolean = false,
    val musicDuringFlashcards: Boolean = false,
    val musicDuringReading: Boolean = true,
    val musicDuringBreaks: Boolean = true,
    /** Gentle synthesized chime at Pomodoro phase ends. */
    val timerChime: Boolean = true,
    /** Contextual encouragement banners (mascot + quote). */
    val motivationalBanners: Boolean = true,
    /** Quote frequency: "daily" | "session" | "off". */
    val quoteFrequency: String = "daily",
    /** Current study goal shown in the Study Room. */
    val studyRoomGoal: String = "",
    /** Study Room background scene id. */
    val studyRoomScene: String = "sakura"
) {
    companion object {
        const val DEFAULT_APP_THEME = "strawberry"
        const val DEFAULT_DISPLAY_NAME = "future MLS"
        const val DEFAULT_MASCOT_NAME = "Mochi"

        /** New default meanings per shared/DESIGN.md v2 "Highlight colors". */
        fun defaultHighlightMeanings(): Map<HighlightColor, String> = mapOf(
            HighlightColor.YELLOW to "Important fact",
            HighlightColor.PINK to "Must memorize",
            HighlightColor.PURPLE to "Definition",
            HighlightColor.BLUE to "Clinical significance",
            HighlightColor.GREEN to "Already mastered",
            HighlightColor.ROSE to "Needs review"
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
        val APP_THEME = stringPreferencesKey("app_theme")
        val TEXT_SCALE = intPreferencesKey("text_scale")
        val MASCOT = stringPreferencesKey("mascot_kind")
        val MASCOT_NAME = stringPreferencesKey("mascot_name")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val GIFT_NOTE = stringPreferencesKey("gift_note")
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
        val HL_PINK = stringPreferencesKey("hl_pink")
        val HL_PURPLE = stringPreferencesKey("hl_purple")
        val HL_BLUE = stringPreferencesKey("hl_blue")
        val HL_GREEN = stringPreferencesKey("hl_green")
        val HL_ROSE = stringPreferencesKey("hl_rose")
        val POM_WORK = intPreferencesKey("pomodoro_work")
        val POM_BREAK = intPreferencesKey("pomodoro_break")
        val CUSTOM_WORK = intPreferencesKey("custom_work")
        val CUSTOM_BREAK = intPreferencesKey("custom_break")
        val PROFILE = stringPreferencesKey("profile_json")
        val DEFAULT_PLAYLIST = stringPreferencesKey("music_default_playlist")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val AUTOPLAY_SESSION = booleanPreferencesKey("music_autoplay_session")
        val MUSIC_INTERACTED = booleanPreferencesKey("music_user_interacted")
        val MUSIC_QUIZZES = booleanPreferencesKey("music_during_quizzes")
        val MUSIC_FLASHCARDS = booleanPreferencesKey("music_during_flashcards")
        val MUSIC_READING = booleanPreferencesKey("music_during_reading")
        val MUSIC_BREAKS = booleanPreferencesKey("music_during_breaks")
        val TIMER_CHIME = booleanPreferencesKey("timer_chime")
        val MOTIVATION_BANNERS = booleanPreferencesKey("motivational_banners")
        val QUOTE_FREQUENCY = stringPreferencesKey("quote_frequency")
        val STUDY_ROOM_GOAL = stringPreferencesKey("study_room_goal")
        val STUDY_ROOM_SCENE = stringPreferencesKey("study_room_scene")
        val FAVORITE_QUOTES = stringPreferencesKey("favorite_quotes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            appTheme = p[Keys.APP_THEME]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_APP_THEME,
            textScalePercent = (p[Keys.TEXT_SCALE] ?: 100).coerceIn(100, 150),
            mascotKind = runCatching { MascotKind.valueOf(p[Keys.MASCOT] ?: "CAT") }.getOrDefault(MascotKind.CAT),
            mascotName = p[Keys.MASCOT_NAME]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_MASCOT_NAME,
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
                HighlightColor.YELLOW to (p[Keys.HL_YELLOW] ?: "Important fact"),
                HighlightColor.PINK to (p[Keys.HL_PINK] ?: "Must memorize"),
                HighlightColor.PURPLE to (p[Keys.HL_PURPLE] ?: "Definition"),
                HighlightColor.BLUE to (p[Keys.HL_BLUE] ?: "Clinical significance"),
                HighlightColor.GREEN to (p[Keys.HL_GREEN] ?: "Already mastered"),
                HighlightColor.ROSE to (p[Keys.HL_ROSE] ?: "Needs review")
            ),
            pomodoroWorkMinutes = p[Keys.POM_WORK] ?: 25,
            pomodoroBreakMinutes = p[Keys.POM_BREAK] ?: 5,
            customWorkMinutes = p[Keys.CUSTOM_WORK] ?: 30,
            customBreakMinutes = p[Keys.CUSTOM_BREAK] ?: 5,
            displayName = p[Keys.DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_DISPLAY_NAME,
            giftNote = p[Keys.GIFT_NOTE] ?: "",
            defaultPlaylistId = p[Keys.DEFAULT_PLAYLIST] ?: "",
            musicVolume = (p[Keys.MUSIC_VOLUME] ?: 0.8f).coerceIn(0f, 1f),
            autoPlayMusicOnSession = p[Keys.AUTOPLAY_SESSION] ?: false,
            musicUserInteracted = p[Keys.MUSIC_INTERACTED] ?: false,
            musicDuringQuizzes = p[Keys.MUSIC_QUIZZES] ?: false,
            musicDuringFlashcards = p[Keys.MUSIC_FLASHCARDS] ?: false,
            musicDuringReading = p[Keys.MUSIC_READING] ?: true,
            musicDuringBreaks = p[Keys.MUSIC_BREAKS] ?: true,
            timerChime = p[Keys.TIMER_CHIME] ?: true,
            motivationalBanners = p[Keys.MOTIVATION_BANNERS] ?: true,
            quoteFrequency = when (p[Keys.QUOTE_FREQUENCY]) {
                "session", "off" -> p[Keys.QUOTE_FREQUENCY]!!
                else -> "daily"
            },
            studyRoomGoal = p[Keys.STUDY_ROOM_GOAL] ?: "",
            studyRoomScene = p[Keys.STUDY_ROOM_SCENE]?.takeIf { it.isNotBlank() } ?: "sakura"
        )
    }

    /** Favorite quote texts (shared/content/motivation.json strings). */
    val favoriteQuotes: Flow<Set<String>> = context.dataStore.data.map { p ->
        val raw = p[Keys.FAVORITE_QUOTES] ?: ""
        if (raw.isBlank()) emptySet() else raw.split("\u0001").filter { it.isNotBlank() }.toSet()
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
    suspend fun setAppTheme(themeId: String) = edit {
        it[Keys.APP_THEME] = themeId.lowercase()
    }
    suspend fun setTextScale(percent: Int) = edit { it[Keys.TEXT_SCALE] = percent.coerceIn(100, 150) }
    suspend fun setMascotKind(kind: MascotKind) = edit { it[Keys.MASCOT] = kind.name }
    suspend fun setMascotName(name: String) = edit {
        it[Keys.MASCOT_NAME] = name.trim().ifBlank { AppSettings.DEFAULT_MASCOT_NAME }
    }
    suspend fun setDisplayName(name: String) = edit {
        it[Keys.DISPLAY_NAME] = name.trim().ifBlank { AppSettings.DEFAULT_DISPLAY_NAME }
    }
    suspend fun setGiftNote(note: String) = edit { it[Keys.GIFT_NOTE] = note.trim() }
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
            HighlightColor.PINK -> it[Keys.HL_PINK] = meaning
            HighlightColor.PURPLE -> it[Keys.HL_PURPLE] = meaning
            HighlightColor.BLUE -> it[Keys.HL_BLUE] = meaning
            HighlightColor.GREEN -> it[Keys.HL_GREEN] = meaning
            HighlightColor.ROSE -> it[Keys.HL_ROSE] = meaning
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

    // ---- Study Music / Study Room / Motivation (v1.2.0) -------------------

    suspend fun setDefaultPlaylist(id: String) = edit { it[Keys.DEFAULT_PLAYLIST] = id }
    suspend fun setMusicVolume(value: Float) = edit { it[Keys.MUSIC_VOLUME] = value.coerceIn(0f, 1f) }
    suspend fun setAutoPlayMusicOnSession(enabled: Boolean) = edit { it[Keys.AUTOPLAY_SESSION] = enabled }
    suspend fun setMusicUserInteracted(enabled: Boolean) = edit { it[Keys.MUSIC_INTERACTED] = enabled }
    suspend fun setMusicDuringQuizzes(enabled: Boolean) = edit { it[Keys.MUSIC_QUIZZES] = enabled }
    suspend fun setMusicDuringFlashcards(enabled: Boolean) = edit { it[Keys.MUSIC_FLASHCARDS] = enabled }
    suspend fun setMusicDuringReading(enabled: Boolean) = edit { it[Keys.MUSIC_READING] = enabled }
    suspend fun setMusicDuringBreaks(enabled: Boolean) = edit { it[Keys.MUSIC_BREAKS] = enabled }
    suspend fun setTimerChime(enabled: Boolean) = edit { it[Keys.TIMER_CHIME] = enabled }
    suspend fun setMotivationalBanners(enabled: Boolean) = edit { it[Keys.MOTIVATION_BANNERS] = enabled }
    suspend fun setQuoteFrequency(frequency: String) = edit {
        it[Keys.QUOTE_FREQUENCY] = when (frequency) {
            "session", "off" -> frequency
            else -> "daily"
        }
    }
    suspend fun setStudyRoomGoal(goal: String) = edit { it[Keys.STUDY_ROOM_GOAL] = goal.trim() }
    suspend fun setStudyRoomScene(scene: String) = edit { it[Keys.STUDY_ROOM_SCENE] = scene }

    suspend fun toggleFavoriteQuote(text: String) {
        val current = favoriteQuotes.first()
        val next = if (text in current) current - text else current + text
        edit { it[Keys.FAVORITE_QUOTES] = next.sorted().joinToString("\u0001") }
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
