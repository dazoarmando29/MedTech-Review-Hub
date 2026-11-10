package com.medtech.reviewhub.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Design tokens — shared/DESIGN.md "Palette". Kept identical to the web app.
// ---------------------------------------------------------------------------

// Primary (blue)
val Blue600 = Color(0xFF2563EB)
val Blue400 = Color(0xFF60A5FA)

// Secondary (teal) — study progress, success accents
val Teal600 = Color(0xFF0D9488)
val Teal400 = Color(0xFF2DD4BF)

// Tertiary (violet) — flashcards, gamification
val Violet600 = Color(0xFF7C3AED)
val Violet400 = Color(0xFFA78BFA)

// Accent (amber) — streaks, achievements
val Amber500 = Color(0xFFF59E0B)
val Amber400 = Color(0xFFFBBF24)

// Danger — errors, "Again" rating
val Red600 = Color(0xFFDC2626)
val Red400 = Color(0xFFF87171)

// Surfaces
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF8FAFC)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF0F172A)
val SurfaceVariantDark = Color(0xFF1E293B)
val OnSurfaceDark = Color(0xFFE2E8F0)

// Outline / border tones
val OutlineLight = Color(0xFFCBD5E1)
val OutlineDark = Color(0xFF475569)

// PDF / keyword highlight colors (user-editable meanings in Settings)
val HighlightYellow = Color(0xFFFBBF24)
val HighlightRed = Color(0xFFF87171)
val HighlightBlue = Color(0xFF60A5FA)
val HighlightGreen = Color(0xFF4ADE80)
val HighlightPurple = Color(0xFFC084FC)

/** Extra semantic colors outside Material's scheme. */
object AppColors {
    val accent: Color = Amber500
    val accentDark: Color = Amber400
    val danger: Color = Red600
    val dangerDark: Color = Red400
    val streak: Color = Amber500
}
