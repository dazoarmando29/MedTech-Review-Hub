# Design System — MedTech Review Hub

Shared across web (Tailwind tokens in `web/src/app/globals.css`) and Android (Material 3
theme in `android/app/src/main/java/.../ui/theme/`).

## Palette

| Token | Light | Dark | Use |
|---|---|---|---|
| `primary` | `#2563EB` (blue-600) | `#60A5FA` | Primary actions, links |
| `secondary` | `#0D9488` (teal-600) | `#2DD4BF` | Study progress, success accents |
| `tertiary` | `#7C3AED` (violet-600) | `#A78BFA` | Flashcards, gamification |
| `accent` | `#F59E0B` (amber-500) | `#FBBF24` | Streaks, achievements |
| `danger` | `#DC2626` | `#F87171` | Errors, "Again" rating |
| `surface` | `#FFFFFF` / `#F8FAFC` | `#0F172A` / `#1E293B` | Cards / background |
| `onSurface` | `#0F172A` | `#E2E8F0` | Body text |

Highlight colors (PDF/keyword system — user-editable meanings):
yellow `#FBBF24` must memorize · red `#F87171` high-priority exam fact ·
blue `#60A5FA` definition · green `#4ADE80` understood · purple `#C084FC` needs study.

## Typography

- Headings: `"Plus Jakarta Sans", "Inter", system-ui` — 700/600.
- Body: `"Inter", system-ui` — 400/500, base 16px, line-height 1.6 (long-read friendly).
- Monospace (formulas, lab values): `"JetBrains Mono", ui-monospace`.
- Text scale: 100 / 115 / 130 / 150% (accessibility), honored by both clients.

## Shape & elevation

Radii: `sm 8px, md 12px, lg 16px, xl 24px`. Shadows: soft, low-contrast (`0 1px 3px rgb(15 23 42 / 0.08)`).
Large touch targets (min 44×44 dp/px). Card padding 16–24.

## Components

`AppCard`, `SubjectChip`, `StatTile`, `ProgressRing`, `StreakFlame`, `LessonCard`,
`Flashcard (flip 3D, 250ms, respects reduced-motion)`, `QuizChoice`, `MascotBubble`,
`HighlightMark`, `EmptyState`, `ErrorState`, `OfflineBanner`, `Skeleton`.

## Motion

Defaults: 150–250 ms ease-out; streak flame and mascot idle animations 2–4 s loops.
`prefers-reduced-motion` / "Reduce animation" setting disables flips, fades, and loops
(content remains fully usable).

## Mascot

Cat ("Mimi") and dog ("Bantay") — original SVG illustrations (`web/public/mascot/`,
Android vector drawables). Moods: `sleeping, happy, curious, disappointed,
celebrating, encouraging, dramatic`. Mascot speech may use Taglish; a global
**Serious Mode** setting hides jokes, mascot commentary, memes, and mini-games.

## Voice

- Formal/exam content: precise medical English.
- Microcopy, empty states, achievements: warm and playful; Taglish optional
  (e.g. "Kaya mo 'to, future RMT!").
- Never shame users for missed days ("Rest day recorded. Recovery is part of the plan.").
- No claim of ASCP endorsement anywhere in UI strings.
