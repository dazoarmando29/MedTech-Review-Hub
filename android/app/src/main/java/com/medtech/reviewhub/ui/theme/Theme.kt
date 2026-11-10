package com.medtech.reviewhub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// User-selectable app themes (shared/DESIGN.md v2 "Themes"). Persisted in
// DataStore as appTheme: "strawberry" | "sakura" | "lavender" | "night" |
// "minimal" | "system" ("system" keeps the legacy ThemeMode behaviour).
// ---------------------------------------------------------------------------

enum class AppTheme(val id: String, val label: String) {
    STRAWBERRY("strawberry", "Strawberry Milk"),
    SAKURA("sakura", "Sakura Blossom"),
    LAVENDER("lavender", "Lavender Dreams"),
    NIGHT("night", "Cozy Night"),
    MINIMAL("minimal", "Minimal Study"),
    SYSTEM("system", "System");

    companion object {
        fun fromId(id: String?): AppTheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STRAWBERRY
    }
}

// Shape tokens (shared/DESIGN.md v2): cards 20dp, buttons pill (Material 3
// buttons resolve to CircleShape already), inputs 14dp, chips pill.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp), // text inputs
    small = RoundedCornerShape(999.dp), // chips (pill)
    medium = RoundedCornerShape(20.dp), // cards / dialogs
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// ---------------------------------------------------------------------------
// Per-theme Material 3 ColorSchemes. Body/heading text on light themes is
// darkText #352735 (>= 12:1 on warmCream). The night theme uses light text
// #FFF0F5 on #241B2E. Small text on white uses berry-700 #A21C5B or darker.
// ---------------------------------------------------------------------------

private fun strawberryScheme() = lightColorScheme(
    primary = StrawberryPrimary,
    onPrimary = Color.White,
    primaryContainer = StrawberryAccent,
    onPrimaryContainer = DarkText,
    secondary = MutedRoseText,
    onSecondary = Color.White,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFFA21C5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD3E0),
    onTertiaryContainer = DarkText,
    error = AppColors.danger,
    onError = Color.White,
    background = StrawberryBg,
    onBackground = DarkText,
    surface = StrawberrySurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFFBE7EF),
    onSurfaceVariant = MutedRoseText,
    outline = MutedRose,
    outlineVariant = AppColors.cardBorder
)

private fun sakuraScheme() = lightColorScheme(
    primary = SakuraPrimary,
    onPrimary = Color.White,
    primaryContainer = SakuraAccent,
    onPrimaryContainer = DarkText,
    secondary = MutedRoseText,
    onSecondary = Color.White,
    secondaryContainer = SoftLavender,
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFFB04A6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD3E0),
    onTertiaryContainer = DarkText,
    error = AppColors.danger,
    onError = Color.White,
    background = SakuraBg,
    onBackground = DarkText,
    surface = SakuraSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFFCEAF0),
    onSurfaceVariant = MutedRoseText,
    outline = MutedRose,
    outlineVariant = Color(0xFFF3D9E2)
)

private fun lavenderScheme() = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderAccent,
    onPrimaryContainer = DarkText,
    secondary = Color(0xFF8A5A70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBD3E0),
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFFDB2777),
    onTertiary = Color.White,
    tertiaryContainer = BabyPink,
    onTertiaryContainer = DarkText,
    error = AppColors.danger,
    onError = Color.White,
    background = LavenderBg,
    onBackground = DarkText,
    surface = LavenderSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFF1E9FF),
    onSurfaceVariant = Color(0xFF6B4A7C),
    outline = Color(0xFFB9A3D6),
    outlineVariant = Color(0xFFE2D4F5)
)

private fun nightScheme() = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color(0xFF2A0E1E),
    primaryContainer = Color(0xFF9D174D),
    onPrimaryContainer = Color(0xFFFFE4F0),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1033),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = NightAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    error = Color(0xFFFDA4AF),
    onError = Color(0xFF3B0710),
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = Color(0xFF3E3152),
    onSurfaceVariant = NightTextDim,
    outline = Color(0xFF9B87AD),
    outlineVariant = AppColors.cardBorderNight
)

private fun minimalScheme() = lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = Color.White,
    primaryContainer = MinimalAccent,
    onPrimaryContainer = DarkText,
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3F4F6),
    onSecondaryContainer = DarkText,
    tertiary = Color(0xFF374151),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E7EB),
    onTertiaryContainer = DarkText,
    error = AppColors.danger,
    onError = Color.White,
    background = MinimalBg,
    onBackground = DarkText,
    surface = MinimalSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB)
)

/**
 * App theme wrapper. Accepts the persisted `appTheme` setting directly:
 *  - "strawberry" | "sakura" | "lavender" | "minimal" -> light scheme of that
 *    theme,
 *  - "night" -> the dark Cozy Night scheme,
 *  - "system" -> the legacy behaviour (follow the ThemeMode light/dark/system
 *    preference via [darkTheme]).
 */
@Composable
fun MedTechTheme(
    appTheme: AppTheme = AppTheme.STRAWBERRY,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.STRAWBERRY -> strawberryScheme()
        AppTheme.SAKURA -> sakuraScheme()
        AppTheme.LAVENDER -> lavenderScheme()
        AppTheme.NIGHT -> nightScheme()
        AppTheme.MINIMAL -> minimalScheme()
        AppTheme.SYSTEM -> if (darkTheme) nightScheme() else strawberryScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
