package com.medtech.reviewhub.data.music

import android.content.res.AssetManager
import com.medtech.reviewhub.domain.StudySynth
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Music catalog models for shared/music/catalog.json (categories, tracks with
 * on-device synth params, suggested playlists). Loaded from assets/music/ at
 * runtime; the Gradle task `copyMusicAssets` keeps the asset in sync with
 * shared/music/. If the asset is missing a tiny embedded fallback catalog
 * (4 tracks) is used so the build and the app never fail.
 */
@Serializable
data class MusicCatalogJson(
    val note: String = "",
    val categories: List<MusicCategoryJson> = emptyList(),
    val tracks: List<MusicTrackJson> = emptyList(),
    val suggestedPlaylists: List<SuggestedPlaylistJson> = emptyList()
)

@Serializable
data class MusicCategoryJson(
    val id: String,
    val name: String,
    val art: String = "",
    val description: String = "",
    val mood: String = ""
)

@Serializable
data class MusicTrackJson(
    val id: String,
    val title: String,
    val categoryId: String,
    val durationSec: Int = 90,
    val artist: String = ARTIST,
    val license: String = LICENSE,
    val synth: SynthSpecJson
) {
    companion object {
        const val ARTIST = "MedTech Companion Studio"
        const val LICENSE = "Original generative composition - CC0"
    }

    fun toSynthSpec(): StudySynth.SynthSpec = StudySynth.SynthSpec(
        recipe = synth.recipe,
        seed = synth.seed,
        tempoBpm = synth.tempoBpm ?: 66,
        rootMidi = synth.rootMidi ?: 60,
        progression = synth.progression,
        chords = synth.chords,
        secondsPerChord = synth.secondsPerChord ?: 8
    )
}

@Serializable
data class SynthSpecJson(
    val recipe: String,
    val seed: Long,
    val tempoBpm: Int? = null,
    val rootMidi: Int? = null,
    val progression: List<List<Int>> = emptyList(),
    val chords: List<List<Int>> = emptyList(),
    val secondsPerChord: Int? = null
)

@Serializable
data class SuggestedPlaylistJson(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList()
)

/** Loaded, queryable catalog. */
class MusicLibrary(val catalog: MusicCatalogJson) {
    val categories: List<MusicCategoryJson> get() = catalog.categories
    val tracks: List<MusicTrackJson> get() = catalog.tracks
    val suggestedPlaylists: List<SuggestedPlaylistJson> get() = catalog.suggestedPlaylists

    fun track(id: String): MusicTrackJson? = catalog.tracks.firstOrNull { it.id == id }
    fun tracksIn(categoryId: String): List<MusicTrackJson> = catalog.tracks.filter { it.categoryId == categoryId }
    fun category(id: String): MusicCategoryJson? = catalog.categories.firstOrNull { it.id == id }

    /** Illustration drawable for a category art id (see res/drawable/art_*.xml). */
    fun artDrawable(art: String): Int = when (art) {
        "pink-piano" -> com.medtech.reviewhub.R.drawable.art_pink_piano
        "cat-violin" -> com.medtech.reviewhub.R.drawable.art_cat_violin
        "desk-headphones" -> com.medtech.reviewhub.R.drawable.art_desk_headphones
        "quartet" -> com.medtech.reviewhub.R.drawable.art_quartet
        "rainy-window" -> com.medtech.reviewhub.R.drawable.art_rainy_window
        "sleepy-moon" -> com.medtech.reviewhub.R.drawable.art_sleepy_moon
        "focus-orb" -> com.medtech.reviewhub.R.drawable.art_focus_orb
        else -> com.medtech.reviewhub.R.drawable.art_focus_orb
    }
}

object MusicCatalogLoader {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Tiny fallback so the app works even when shared/music/ is not bundled. */
    val FALLBACK: MusicCatalogJson = MusicCatalogJson(
        note = "Embedded fallback catalog (shared/music/catalog.json not bundled).",
        categories = listOf(
            MusicCategoryJson("piano-dreams", "Piano Dreams", "pink-piano", "Relaxing solo piano.", "calm"),
            MusicCategoryJson("violin-serenade", "Violin Serenade", "cat-violin", "Soft string melodies.", "tender"),
            MusicCategoryJson("nature-ambient", "Nature and Ambient", "rainy-window", "Rain, forest, ocean, night.", "ambient"),
            MusicCategoryJson("deep-focus", "Deep Focus", "focus-orb", "Minimal ambient soundscapes.", "deep")
        ),
        tracks = listOf(
            MusicTrackJson(
                "pd-001", "Sakura Keys", "piano-dreams", 90,
                synth = SynthSpecJson(
                    "piano-arpeggio", 12001, 66, 60,
                    progression = listOf(listOf(60, 64, 67), listOf(55, 59, 62), listOf(57, 60, 64), listOf(53, 57, 60))
                )
            ),
            MusicTrackJson(
                "vs-001", "Strings for a Future MLS", "violin-serenade", 90,
                synth = SynthSpecJson(
                    "violin-pad", 22001,
                    chords = listOf(listOf(57, 60, 64), listOf(53, 57, 60), listOf(60, 64, 67), listOf(55, 59, 62))
                )
            ),
            MusicTrackJson("na-001", "Rain on the Window", "nature-ambient", 90, synth = SynthSpecJson("rain", 52001)),
            MusicTrackJson("df-001", "Deep Focus Drift", "deep-focus", 120, synth = SynthSpecJson("deep-drone", 72001))
        ),
        suggestedPlaylists = listOf(
            SuggestedPlaylistJson("pink-study", "My Pink Study Playlist", listOf("pd-001", "vs-001")),
            SuggestedPlaylistJson("midnight-review", "Midnight ASCPi Review", listOf("vs-001", "df-001"))
        )
    )

    fun load(assets: AssetManager): MusicLibrary {
        val text = runCatching {
            assets.open("music/catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        val catalog = text?.let {
            runCatching { json.decodeFromString<MusicCatalogJson>(it) }.getOrNull()
        } ?: FALLBACK
        return MusicLibrary(
            if (catalog.tracks.isEmpty() || catalog.categories.isEmpty()) FALLBACK else catalog
        )
    }
}
