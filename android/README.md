# MedTech Review Hub — Android

Offline-first Android reviewer for Medical Laboratory Science board exams
(ASCPi MLS and Philippine MTLE). Companion to the website and backend in this
monorepo; consumes the same content JSON (`shared/content/`, see
`shared/CONTENT_SCHEMA.md`) and backend API (`shared/api/API.md`) and the same
spaced-repetition scheduler (`shared/FSRS.md`).

**This is an independent review app. It is NOT affiliated with, endorsed by, or
sponsored by ASCP or any examining body.** Adaptive practice is NOT the ASCP
computer-adaptive test (CAT). Practice percentages are study estimates, not
pass guarantees.

## Requirements

- JDK 17 (e.g. `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`)
- Android SDK with `platforms;android-35` and `build-tools;34+`
  (AGP reads `ANDROID_HOME` — no `local.properties` needed in CI; a
  machine-specific `local.properties` with `sdk.dir=...` is supported and is
  already git-ignored)
- Android device/emulator: **Android 8.0 (API 26)** or newer

## Build

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --console=plain   # unit tests
.\gradlew.bat :app:assembleDebug --console=plain       # debug APK
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

Toolchain (pinned): Gradle 8.11.1 (wrapper), AGP 8.7.3, Kotlin 2.0.20,
KSP 2.0.20-1.0.25, compileSdk 35, minSdk 26, targetSdk 35, JVM target 17.

### Content assets

The Gradle task `copyContentAssets` (wired into `preBuild`) copies
`shared/content/**` into `app/src/main/assets/content/` when
`shared/content/manifest.json` exists. If `shared/content/` is missing or
empty, a small embedded sample content set already committed under
`app/src/main/assets/content/` is used so the build always works fully offline.

### Intentional dependency deviation (documented)

The spec pins `activity-compose 1.13.0` and `lifecycle 2.11.0`, but those AARs
declare `minCompileSdk 36` and `minAgpVersion 8.9.1`, which conflicts with the
pinned toolchain (AGP 8.7.3, compileSdk 35 — the android-36 platform is not
installed). AGP fails `checkAarMetadata` with no supported opt-out, so the
build uses the newest compatible lines — `activity-compose 1.9.3`,
`lifecycle 2.8.7` — with identical APIs. Everything else is pinned as
specified.

## Features

1. **Onboarding wizard** — exam target (MLS_ASCPI / PH_BOARD / CUSTOM), target
   date, knowledge level, strong/weak subjects, minutes/day, weekly schedule,
   learning preferences, explanation style (Formal / Simple / **Taglish**),
   notification + focus permission prompts. Generates a local study plan
   (weak subjects get 1.5x time; ASCPi weights ≈ bb 9 / cc 17 / hem 16 /
   micro 18 / imm 10 / ua 10 / ops 12; PH profile uses its own weights),
   stored in Room and editable.
2. **Home dashboard** — today's goals checklist, study streak, total minutes,
   flashcards due, questions answered today, recent quiz accuracy, weakest
   subject, exam countdown, weekly minutes calendar, "Continue reading",
   "Resume quiz", suggested next topic, mascot greeting bubble.
3. **Review library** — subjects → topics → lessons from bundled JSON;
   QUICK REVIEW / DEEP STUDY tabs, high-yield lists, must-know/comparison
   tables, keywords, exam traps, mnemonics + funny memory hooks, practice
   questions, references, related flashcards. Reading progress + bookmark +
   resume position persisted. Formula sheet included.
4. **Flashcards** — full **FSRS-4.5** scheduler (`domain/SpacedRepetition.kt`,
   exactly per `shared/FSRS.md`; verified by unit tests), flip animation,
   new/learning/review queues, daily new limit (20) + review limit (200),
   decks/tags/subjects, favorites, personal notes, cloze `[[1]]` rendering,
   review history, next-review-date display, manual card creation, CSV
   import/export (share via ACTION_SEND), cards auto-generated from missed
   quiz questions. Room persistence.
5. **Quiz engine** — question bank from bundled content; modes: subject, topic,
   random, weak-areas, previously-incorrect, saved, timed, untimed, mixed, and
   full mock exam (100 questions / 150 minutes). Study mode (instant feedback,
   full per-choice rationales, "Why was I wrong?" misconception field) and
   Exam mode (feedback only at the end). Results: score, time, accuracy by
   subject, weak topics, incorrect list, relevant lessons. **Adaptive practice
   (self-adjusting difficulty)** with a documented rule (see
   `domain/QuizScoring.kt`) and an on-screen disclaimer: it is NOT ASCP CAT.
6. **PDF reader** — SAF import (`ACTION_OPEN_DOCUMENT`), rendering via
   `android.graphics.pdf.PdfRenderer`, pinch-zoom, page navigation, bookmarks,
   resume last page, highlights with 5 semantic colors (YELLOW/RED/BLUE/
   GREEN/PURPLE, user-editable meanings in Settings) stored as
   page+rect+color+text in Room, notes, folders, fully offline. Best-effort
   in-app text search via `data/pdf/PdfTextExtractor.kt` (parses content
   streams, FlateDecode via `java.util.zip.Inflater`, Tj/TJ operators).
   AI tools panel (summarize / key terms / flashcards / explain simple
   English-Taglish / outline) calls the backend `/ai/*` endpoints only when a
   server URL is configured; otherwise shows a non-blocking "AI features
   unavailable" state (503 `AI_UNAVAILABLE` handled per API.md).
7. **Habits & diary** — habits (MINUTES/COUNT) with daily checklist,
   weekly/monthly heatmap, 5 moods, private diary (local-only), streak with a
   rest-day rule ("Rest day recorded. Recovery is part of the plan."), weekly
   summary, optional AI diary summary.
8. **Focus mode + app blocker (Android-only flagship)** — Pomodoro timer
   (25/5, 50/10, custom), distraction app picker (`queryIntentActivities`
   CATEGORY_LAUNCHER with per-app icons/labels), `UsageStatsManager`
   foreground-app detection during an active session, blocking overlay
   (`TYPE_APPLICATION_OVERLAY`) with mascot message and "Return to study" /
   "5 more minutes" / emergency pause, graceful fallback to notifications +
   banner when permissions are missing, completed-session stats. Explicit
   toggles only; easy to disable in Settings or by uninstalling. **No hidden
   monitoring.**
9. **Mascot** — cat "Mimi" and dog "Bantay" as original vector drawables
   (one hand-drawn shape set per mood: sleeping, happy, curious, disappointed,
   celebrating, encouraging, dramatic), 12+ original lines each (Taglish
   voice per `shared/DESIGN.md`), reacts during focus sessions and streaks.
   Settings: mute, reduce animation, disable commentary, Serious Mode.
10. **Update checker** — GitHub Releases API
    (`dazoarmando29/MedTech-Review-Hub`) compared against
    `BuildConfig.VERSION_NAME` with semver; dismissible dialog with release
    notes, "Download" link (`ACTION_VIEW`), "Later", "Don't remind for this
    version" (stored in DataStore). **Never auto-installs APKs.**
11. **Settings** — theme (system/light/dark), text scale (100–150%),
    explanation style, notification prefs, focus permission status + deep
    links, API base URL (default empty = fully offline), account sign-in
    against `/auth/*` with best-effort background sync (WorkManager), JSON
    data export (share via SAF), About + disclaimers screen.
12. **Offline-first** — every feature works with zero network. Network is used
    only for optional sync, AI tools, and the update check.

## Release signing (GitHub Actions)

Release builds are signed only when signing environment variables are present;
**keys are never committed to the repository** (`.gitignore` already excludes
`*.keystore`, `*.jks`, `signing.properties`, `local.properties`).

| Secret / env var | Meaning |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Base64 of the `.keystore`/`.jks` file |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

`app/build.gradle.kts` decodes `SIGNING_KEYSTORE_BASE64` into
`build/release.keystore` at build time and signs the release variant. If the
variable is absent, the release build is simply unsigned. Example CI step:

```yaml
- name: Build signed release
  env:
    SIGNING_KEYSTORE_BASE64: ${{ secrets.SIGNING_KEYSTORE_BASE64 }}
    SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
    SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
    SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
  run: cd android && ./gradlew.bat :app:assembleRelease --console=plain
```

## Sideload instructions

1. Build (or download) `app-debug.apk` / `app-release.apk`.
2. Copy the APK to the device.
3. On the device: Settings → Apps → Special access → *Install unknown apps* →
   allow your file manager/browser (Android 8+).
4. Open the APK and install. Updates are installed the same way — the app
   only shows a "Download" link, it never installs APKs by itself.

## Permissions (all explained in-app before use)

| Permission | Why | When |
|---|---|---|
| `INTERNET` | Optional sync, AI tools, update check | Only if a server URL is configured / update check |
| `POST_NOTIFICATIONS` | Study + focus reminders | Optional, user-toggled |
| `USAGE_STATS` (Usage Access) | Detect distracting apps during focus sessions | Special access via Settings, clearly explained first |
| `SYSTEM_ALERT_WINDOW` | Blocking overlay during focus sessions | Special access via Settings, clearly explained first |
| `FOREGROUND_SERVICE(_SPECIAL_USE)` | Focus session service while a session runs | Only during active focus sessions |

## Honest platform limitations

- **No absolute app blocking.** Android does not guarantee that other apps can
  be blocked. The overlay can be dismissed, recents can be used, and some
  devices/OEMs restrict usage stats. The feature is a *commitment aid*, not
  MDM-level blocking. This is documented in `AppBlockerService.kt` and in the
  UI.
- **Usage Access / Overlay are special permissions** — the user must grant
  them in system Settings; the app explains why before sending them there and
  falls back to notifications + banner when they are missing.
- **PDF text extraction is best-effort.** `PdfTextExtractor` parses raw
  content streams (Tj/TJ, FlateDecode). Scanned PDFs have no text operators:
  search then shows *"No extractable text in this PDF (likely scanned)."*
  Font encodings beyond ASCII/Latin-1 and encrypted PDFs are not supported.
  Page rendering always uses `PdfRenderer` and is exact.
- **AI features require a configured backend** and may return 503
  (`AI_UNAVAILABLE`); the app stays fully functional without AI.
- **Fonts:** Plus Jakarta Sans / Inter / JetBrains Mono from `shared/DESIGN.md`
  are not bundled; Android falls back to system sans-serif/monospace.
- **Adaptive practice is not ASCP CAT** and pass percentages are estimates,
  not guarantees.
- The mascot illustrations and meme content are original shapes/text; no
  copyrighted meme images are used.

## Tests

`src/test/kotlin` — plain JUnit 4 (70 tests): FSRS-4.5 scheduler vs
`shared/FSRS.md` formulas (retrievability at t=S is 0.9, Good graduation gives
S=W[2], Again decreases stability, difficulty clamped 1–10, learning steps,
lapse/relearning flow, sub-1-minute reviews ignored), quiz scoring + the
adaptive rule, semver comparison for the update checker, the PDF text
extractor on a tiny embedded sample PDF (uncompressed + Flate-compressed
streams), study-plan generation rules, and three Compose-less ViewModel tests
(`OnboardingViewModelTest`, `QuizViewModelTest`, `FocusTimerViewModelTest`).
