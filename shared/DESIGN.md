# Design System v2 — Pink MedTech Study Companion 🌸

Shared across web (Tailwind tokens in `web/src/app/globals.css`) and Android
(Material 3 themes in `android/.../ui/theme/`). The product is a cozy pastel-pink
study space for a future MLS — cute and comforting, but the reviewer itself stays
serious, accurate, readable, and accessible. Cuteness never costs legibility.

## Core palette (all themes derive from these)

| Token | Hex | Use |
|---|---|---|
| `babyPink` | `#F8BBD0` | Primary soft accent, progress bars, chips |
| `blushPink` | `#FFF0F5` | Page background (light themes) |
| `softLavender` | `#E9D5FF` | Secondary accent, sticky notes |
| `warmCream` | `#FFFDFB` | Card surfaces (light themes) |
| `berryPink` | `#DB2777` | Primary actions, links, key numbers |
| `mutedRose` | `#BE7896` | Secondary text, decorative strokes |
| `darkText` | `#352735` | Body + headings (light themes) |

Contrast rule: body text on light surfaces must meet WCAG AA (≥ 4.5:1).
`darkText #352735` on `#FFFDFB` ≈ 12:1 ✓. `berryPink` is used for large text,
icons, and fills — small text on white uses `#A21C5B` (berry-700) or darker.

## Themes (user-selectable; persisted in settings/`DataStore`)

| id | Name | bg | surface | primary | accent | notes |
|---|---|---|---|---|---|---|
| `strawberry` | Strawberry Milk (default) | `#FFF0F5` | `#FFFDFB` | `#DB2777` | `#F8BBD0` | pink everywhere, gentle |
| `sakura` | Sakura Blossom | `#FFF5F7` | `#FFFBFC` | `#E56B8D` | `#FBD3E0` | softer Japanese pink |
| `lavender` | Lavender Dreams | `#F7F2FF` | `#FFFDFB` | `#8B5CF6` | `#E9D5FF` | pastel purple + pink |
| `night` | Cozy Night | `#241B2E` | `#322741` | `#F472B6` | `#7C3AED` | dark + pink accents, AA-checked |
| `minimal` | Minimal Study | `#FAFAFA` | `#FFFFFF` | `#352735` | `#E5E7EB` | neutral, no decorations |

Implementation: CSS variables set on `<html data-theme="...">` (web) and one
Material 3 `ColorScheme` per theme (Android). Decorative elements (stars, florals,
sticker art, gradients) render **only** in themes ≠ `minimal` and when
"Reduce animation/low-bandwidth" is off.

## Typography

- Headings: **Quicksand** (display moments: greetings, page titles) / **Nunito**
  (section headings, buttons) — rounded, friendly, 600–700.
- Body & reviewer content: **Inter** — 400/500, 16px base, line-height 1.6.
- Numerals (stats): Nunito 700, `berryPink`.
- Web: self-hosted via `next/font` (no layout shift). Android: bundled OFL
  font resources where practical, system sans fallback.
- Text scale 100/115/130/150% honored everywhere. Never below 14px effective.

## Shape, elevation, decoration

- Radii: cards `20px`, buttons `999px` (pill), inputs `14px`, chips `999px`.
- Shadows: soft pink-tinted (`0 4px 16px rgb(219 39 119 / 0.08)`).
- Gradients: hero/greeting banner only (blush → cream, 135°).
- Decoration budget: small stars ✦, tiny florals, hearts in empty states and
  celebration moments only. Never on dense study content. Always optional.
- Navigation: rounded top bar / pill tabs; cute subject icons (emoji-free SVG
  glyph set: 🩸 droplet, ⚗️ flask, 🔬 scope, 🦠 microbe, 🧪 tube, 📋 clipboard,
  💜 heart-shield — drawn as simple original SVG, not emoji, on Android too).

## Components (v2 names)

`SoftCard` (pink paper), `PillButton` (primary berry / secondary blush),
`StickyNote` (rotate ±1.5°, softLavender bg), `ProgressPetal` (ring progress),
`StreakFlame`, `MochiBubble`, `MotivationCard`, `CountdownBadge`, `ThemePicker`,
`StickerPicker`, `EmptyState` (illustrated), `LoadingMochi` (custom spinner),
`HighlightMark` (6 semantic colors, below), `QuizChoice`, `ResultBanner`.

## Highlight colors (user-editable meanings)

| Color | Default meaning | Web class |
|---|---|---|
| Yellow | Important fact | `hl-YELLOW` |
| Pink | Must memorize | `hl-PINK` |
| Purple | Definition | `hl-PURPLE` |
| Blue | Clinical significance | `hl-BLUE` |
| Green | Already mastered | `hl-GREEN` |
| Rose | Needs review (legacy "confusing") | `hl-ROSE` |

Migration from v1.0.0 stored colors (no data loss):
`RED→PINK` (high-priority ≈ must memorize), `YELLOW→YELLOW`,
`BLUE→PURPLE` (definition), `PURPLE→ROSE` (confusing/needs study),
`GREEN→GREEN`. Old meaning labels in settings are replaced by the new defaults.

## Mascot — Mochi 🐾 (original artwork only)

A small fluffy pink-and-white cat in a tiny white lab coat with a miniature
laboratory badge. Large expressive eyes, pink inner ears, tiny paws.
Moods (single parameterized SVG component on web; vector drawables on Android):
`happy · sleepy · studying · celebrating · confused · encouraging ·
disappointed · excited`.

Situational behavior:
- Studying: sits by a stack of books. · Correct answer: stars + confetti.
- Wrong answer: encouraging nudge (never shaming). · Break: sleeps / strawberry milk.
- Focus session: studies beside the student.

Users can rename the mascot (default **"Mochi"**) and switch to the optional dog
companion ("Bantay"). All animations lightweight and disableable (settings +
Serious Mode + `prefers-reduced-motion` + Minimal theme). Serious Mode hides
mascot commentary, memes, and jokes but keeps every study function.

## Voice & copy

- Greeting: "Good morning, future MLS! 🌸" (PH timezone aware) with
  "Another day closer to that ASCPi dream."
- Motivational lines rotate with progress ("Your future self is cheering for you!",
  "One chapter at a time, future MLS.", "Small progress is still progress." —
  never shaming missed days).
- Optional gift note card (editable personal message, default warm wording).
- Quiz results: playful ("You ate that quiz!"), with a toggle to disable funny
  commentary. Incorrect answers always get a real teaching explanation first.
- Formal exam content remains precise medical English; Taglish optional in
  informal/mnemonic text only.

## Accessibility & performance

- All themes AA-contrast checked; focus rings visible on every control.
- Keyboard + screen-reader labels on interactive elements; aria-live for quiz
  feedback. Min touch target 44px.
- Low-bandwidth / Reduce-animation mode: no gradients, no decorative SVG
  animations, static mascot.
- No fabricated data, no fake buttons, no empty placeholder pages — ever.
