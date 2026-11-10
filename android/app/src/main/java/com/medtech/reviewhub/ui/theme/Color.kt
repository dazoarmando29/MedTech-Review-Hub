package com.medtech.reviewhub.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Design tokens - shared/DESIGN.md v2 "Pink MedTech Study Companion".
// Core palette (all themes derive from these):
//   babyPink #F8BBD0, blushPink #FFF0F5, softLavender #E9D5FF, warmCream #FFFDFB,
//   berryPink #DB2777, mutedRose #BE7896, darkText #352735.
// Contrast rule: body text on light surfaces >= WCAG AA (4.5:1).
// darkText #352735 on #FFFDFB > 12:1. Small text on white uses berry-700
// #A21C5B or darker.
// ---------------------------------------------------------------------------

val BabyPink = Color(0xFFF8BBD0)
val BlushPink = Color(0xFFFFF0F5)
val SoftLavender = Color(0xFFE9D5FF)
val WarmCream = Color(0xFFFFFDFB)
val BerryPink = Color(0xFFDB2777)
val BerryDeep = Color(0xFFA21C5B) // berry-700 - small text on white surfaces
val MutedRose = Color(0xFFBE7896) // decorative strokes / large accents
val DarkText = Color(0xFF352735)

// Secondary text on light surfaces (AA >= 4.5:1 on warmCream/blushPink).
val MutedRoseText = Color(0xFF8A5A70)

// ---------------------------------------------------------------------------
// Theme palettes (shared/DESIGN.md v2 "Themes" table - exact hex values).
// ---------------------------------------------------------------------------

// strawberry - Strawberry Milk (default): pink everywhere, gentle
val StrawberryBg = Color(0xFFFFF0F5)
val StrawberrySurface = Color(0xFFFFFDFB)
val StrawberryPrimary = Color(0xFFDB2777)
val StrawberryAccent = Color(0xFFF8BBD0)

// sakura - Sakura Blossom: softer Japanese pink
val SakuraBg = Color(0xFFFFF5F7)
val SakuraSurface = Color(0xFFFFFBFC)
val SakuraPrimary = Color(0xFFE56B8D)
val SakuraAccent = Color(0xFFFBD3E0)

// lavender - Lavender Dreams: pastel purple + pink
val LavenderBg = Color(0xFFF7F2FF)
val LavenderSurface = Color(0xFFFFFDFB)
val LavenderPrimary = Color(0xFF8B5CF6)
val LavenderAccent = Color(0xFFE9D5FF)

// night - Cozy Night: dark + pink accents, AA-checked
val NightBg = Color(0xFF241B2E)
val NightSurface = Color(0xFF322741)
val NightPrimary = Color(0xFFF472B6)
val NightAccent = Color(0xFF7C3AED)
val NightText = Color(0xFFFFF0F5) // light text on #241B2E
val NightTextDim = Color(0xFFE9D5FF)

// minimal - Minimal Study: neutral, no decorations
val MinimalBg = Color(0xFFFAFAFA)
val MinimalSurface = Color(0xFFFFFFFF)
val MinimalPrimary = Color(0xFF352735)
val MinimalAccent = Color(0xFFE5E7EB)

// ---------------------------------------------------------------------------
// PDF / keyword highlight colors (6 semantic colors, meanings editable in
// Settings; shared/DESIGN.md v2 "Highlight colors").
// ---------------------------------------------------------------------------
val HighlightYellow = Color(0xFFFBBF24) // Important fact
val HighlightPink = Color(0xFFF472B6) // Must memorize
val HighlightPurple = Color(0xFFC084FC) // Definition
val HighlightBlue = Color(0xFF60A5FA) // Clinical significance
val HighlightGreen = Color(0xFF4ADE80) // Already mastered
val HighlightRose = Color(0xFFF43F5E) // Needs review

/** Extra semantic colors outside Material's scheme. */
object AppColors {
    val accent: Color = BerryPink
    val accentSoft: Color = BabyPink
    val danger: Color = Color(0xFFE11D48)
    val dangerSoft: Color = Color(0xFFFDA4AF)
    val streak: Color = Color(0xFFF59E0B)
    /** Soft pink card border on light surfaces. */
    val cardBorder: Color = Color(0xFFF3D3DF)
    /** Card border for the Cozy Night theme. */
    val cardBorderNight: Color = Color(0xFF4A3A5E)
    /** Soft pink card tint (light themes). */
    val cardTint: Color = Color(0xFFFFF7FA)
}
