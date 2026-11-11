# Changelog

All notable changes to MedTech Review Hub are documented here.
Versioning follows Semantic Versioning (`vMAJOR.MINOR.PATCH`).

## [Unreleased]

### Fixed
- **PDF reader freezes after upload and while using AI tools**: the text-selection layer (thousands of spans on textbook pages) was re-rendered through React on every interaction, blocking the main thread. The layer is now built imperatively in small yielding chunks, the viewer is memoized with stable callbacks, and a 'Preparing your PDF…' overlay shows during parsing. Clicks and AI tools respond instantly.

### Changed
- **AI tools never need an account — permanently.** The server recognizes the device (X-Device-Id) instead of a login; accounts are now optional and only used for cross-device sync. Signed-in users get silent token refresh so they are never logged out either.

### Fixed
- **AI tools misreported auth failures as 'No backend configured'/'Could not generate flashcards'**: the real cause was an expired or missing login token (401). Errors are now truthful and specific ('Please sign in to use AI tools'), the client silently refreshes expired tokens and retries, and the PDF AI panel shows a live 'signed in / sign in for AI' badge. 5 new regression tests lock this in.

### Added (website + content; Android keeps parity where noted)
- **Library rebuilt around the Philippine ASCPi review structure**: subject
  names now match what Filipino review centers teach (ACTS: CC / MP / CM / Hema /
  ISBB; Pioneer: Blood Banking, Clinical Chemistry, Clinical Microscopy–AUBF,
  Hematology–Coagulation, Immunology & Serology, Lab Operations, Microbiology,
  Parasitology) — with review-emphasis hints from Pioneer's posted program hours.
- **NEW Parasitology subject** (a distinctly emphasized PH review area): 2 full
  lessons (intestinal protozoa & helminths; malaria & microfilariae), 20 original
  questions, 18 flashcards. Library total: 19 lessons, 230 questions.
- Study planners (web, backend, Android) updated with parasitology weights.

### Changed
- **Calm design is now the official design** (ADHD & beginner friendly) — not a
  settings button. Secondary/technical detail is folded away everywhere; the
  dashboard shows 3 key stats with a quiet "show more stats" link.
- **Official logo lockup in the nav**: mascot + stacked "MedTech / Study
  Companion" wordmark (like the brand artwork), with proper breathing room
  between the logo and the navigation buttons.

## [1.2.1] — 2026-11-11

### Added (website + Worker)
- **Plain-language copy** where jargon lived (e.g. flashcards now say "cards
  come back right before you would forget them" with the FSRS note folded away);
  the library page now opens with a friendly "New here? Start like this" guide.
- **Funny AI explanations**: "Explain it funny" (meme-like but accurate) on
  lessons and in the PDF AI panel; AI styles `FUNNY` and `TAGLISH_FUNNY` on both
  the Worker and the Ktor backend.
- **Post-exam AI coach** on quiz results: ask anything in funny Taglish that a
  non-medtech can understand, with ready-made prompts ("What should I review
  first?", "Explain my mistakes the funny way", "Make the hardest topic easy",
  "Cheer me up + advice") and real advice computed from her actual weak topics.
- **iPad support**: installable via "Add to Home Screen" (apple web-app metadata
  + 180px apple-touch-icon), `viewport-fit=cover` with safe-area padding for
  notch iPads, tablet-width layouts verified at 1024×1366 and 1366×1024.

### Fixed
- Library subject cards: "3 lessons" badges no longer wrap, cards in a row now
  align evenly (progress pinned to the bottom), long outline disclaimers moved
  off the cards to a single friendly page note.
- Global badge/spacing consistency (badges never wrap or squeeze).

## [1.2.0] — 2026-11-11

### Cozy Study Music & Daily Motivation 🌸

### Added
- **Study Music** ("Find your focus, one melody at a time."): a real, fully
  functional player over **22 original generative compositions** across 7
  categories (Piano Dreams, Violin Serenade, Lo-Fi Study Café, Classical Focus,
  Nature and Ambient incl. rain/ocean/wind/white/brown noise/night, Cozy Sleepy
  Study, Deep Focus). Every track is synthesized on-device from the recipes in
  `shared/MUSIC.md` — original CC0 audio, nothing copyrighted is bundled or
  streamed. Real controls: play/pause, next/prev, seek, volume, mute, repeat
  track/playlist, shuffle, progress, duration, title + artist attribution.
- **Mini-player** docked on every page (lessons, quizzes, flashcards, PDFs,
  diary) with Media Session / lock-screen integration where the browser
  supports it; playback never starts without a user gesture.
- **Offline & downloads**: tracks generate locally so everything works offline;
  "Download for offline" pre-composes tracks to cache with storage estimates
  and removable downloads.
- **Personal playlists**: 6 suggested (My Pink Study Playlist, Midnight ASCPi
  Review, Blood Banking Focus, Chemistry Calm, Microbiology Marathon, Cozy
  Rainy Review) plus create/rename/delete custom playlists, favorites, default
  / morning / late-night picks.
- **A Little Motivation for You**: deterministic daily quotes (24 original
  statements — no invented attributions), random quote, favorites, copy, and
  **shareable quote cards exported as real PNG images** (canvas renderer +
  Web Share API with download fallback).
- **Personalized encouragement**: contextual messages after lessons, flashcard
  sessions, wrong answers, long focus sessions, and daily-goal completion.
- **Little Notes for You**: private, user-written encouragement notes —
  add/edit/delete, schedule to specific dates, favorites, random display on the
  dashboard and in the Study Room. Never auto-generated.
- **My Cozy Study Room**: full-focus space combining Pomodoro (25/5, 50/10,
  custom) + music + daily quote + Little Notes + Mochi + study goal + session
  progress, with 5 backgrounds (Sakura Morning, Rainy Pink Café, Cozy Night,
  Lavender Evening, Minimal Focus) and a distraction-free mode. Gentle
  synthesized chime at phase changes (never loud). Music integrates with the
  timer: focus playlist on start, calmer break playlist optional.
- **Player preferences**: default/morning/night playlists, volume, auto-play
  (after user authorization), music-during-quizzes/flashcards/reading/breaks
  toggles, chime, quote frequency, ambient animations. Privacy-respecting
  defaults; device caches never sync.
- **New app icon & logo**: kawaii pink book with a stethoscope heart —
  recreated as scalable SVG (web PWA icon, header logo) and Android adaptive
  launcher icon.

### Changed
- Mochi's idle animation slowed to a gentle 4.5s breathing float (was jumpy).
- Android 1.2.0: Study Music screen with Media3 playback service (notification +
  lock-screen controls, audio focus, headphone handling), motivation widget,
  Little Notes, Cozy Study Room integrated with the existing focus/app-blocker,
  new launcher icon. 99 unit tests (29 new).

### Honest notes
- Audio is generated on-device (shared/MUSIC.md) — original compositions under
  CC0; the catalog is ready for licensed track packs later. Synthetic piano and
  strings are clearly labeled generative ambience, not recordings.
- Website has no app blocking (by design; Android owns that feature).

## [1.1.0] — 2026-11-11

### Pink MedTech Study Companion 🌸
A complete visual redesign and cozy feature expansion — a personal pink study
space for a future MLS, with the reviewer itself kept serious and accurate.

### Added
- **Design system v2** (shared/DESIGN.md): pastel pink palette, five themes —
  Strawberry Milk (default), Sakura Blossom, Lavender Dreams, Cozy Night (dark),
  Minimal Study — with AA-contrast guarantees and a decoration budget that
  disables in Minimal/Serious/low-bandwidth modes. Quicksand/Nunito/Inter
  typography, rounded pill navigation, soft cards, sticky notes.
- **Mochi** — original pink-and-white lab-coat study cat (8 moods) with an
  optional dog companion. Renamable, cosmetic **Cat Room** unlocks (bow,
  glasses, flower crown, grad cap…), all animations disableable.
- **Personalized dashboard**: PH-timezone greeting ("Good morning, future MLS!"),
  daily motivation rotation, customizable display name, personal motivation line,
  and an editable gift note card.
- **Study Garden** — pink flowers grow with every study session
  (seed → sprout → bud → bloom at 75 minutes).
- **Philippine ASCPi Resources** page: verified directory of Pioneer Review
  Center (PRCI), ACTS Review Center, and Legend Review Center (fees/schedules as
  posted by them, Nov 2026 — no endorsements), official ASCP BOC links,
  a verified reading list with licensing notes, and neutral ASCPi prep info.
- **Mistake Notebook** — every wrong answer collected with its teaching
  explanation and misconception note, plus one-click re-practice.
- **Quick Review hub** — all high-yield facts, mnemonics, and exam traps in one
  place with mastery check-off.
- **Visual ID practice** — 12 original SVG laboratory illustrations
  (cells, crystals, casts, organisms; no copyrighted images).
- **Lesson actions**: "Turn this into a flashcard", "Explain this simply" (AI),
  "Quiz me on this!", keyword suggestions with accept/dismiss, sticky-note
  memory hooks.
- **Diary stickers**, cute quiz result banners with an optional funny-commentary
  toggle, "Cute Memory Trick" card backs, Weekly Wrapped recap.
- Highlight colors refreshed: Yellow = important fact, Pink = must memorize,
  Purple = definition, Blue = clinical significance, Green = mastered,
  Rose = needs review (legacy v1.0.0 annotations migrate automatically —
  no data loss; custom labels preserved).

### Changed (web + Android)
- Android 1.1.0: five pink themes, Mochi vector mascot, new dashboard copy,
  PH ASCPi resources screen, new highlight colors with Room migration,
  updated strings and README.
- Content: 10 new scientifically-accurate memes/jokes; 13 textbook citations
  corrected to verified editions (Harmening → *Modern Blood Banking and
  Transfusion Practices*, Tietz → *Fundamentals* 7th ed., Strasinger 7th ed.,
  Bailey & Scott 16th ed.).
- Note for existing users: your study data is preserved — v1.0.0 settings,
  highlights, and annotations are migrated in place on first load.

## [1.0.1] � 2026-11-11

### Added
- **Cloudflare Worker backend + AI tools (live production deployment):**
  `cloudflare-worker/` implements `shared/api/API.md` on Cloudflare Workers + D1
  with Gemini-powered AI tools. Deployed at
  `https://medtech-review-hub-api.dazoarmando.workers.dev` — accounts, cross-device
  sync, and PDF AI tools now work out of the box (website default API URL wired).
  - Auth (PBKDF2-SHA256, rotating refresh tokens, account deletion), profile,
    study plans, sessions, analytics, flashcards with FSRS schedules, quiz
    attempts, habits, diary, PDF metadata/annotations sync.
  - AI endpoints (`/ai/*`) enforce document citations, "not found in document"
    honesty, review warnings, per-user token budgets, caching, and `503
    AI_UNAVAILABLE` fallback. Gemini key stored as a Worker secret only.

## [1.0.0] — 2026-11-11

First stable release.

### Website
- Responsive study platform (Next.js 14 + TypeScript + Tailwind), static-export
  deployable to GitHub Pages / Vercel / Cloudflare Pages, PWA manifest.
- Onboarding wizard generating a personalized 4-week study plan (ASCPi and PH board
  profiles kept separate; weak subjects get 1.5x planned time).
- Home dashboard: goals, streak, minutes, due flashcards, questions answered,
  recent accuracy, weakest subject, exam countdown, weekly chart, continue reading,
  resume quiz, suggested topic.
- ASCPi review library: 17 lessons across 7 subjects (Blood Banking, Clinical
  Chemistry, Hematology, Microbiology, Immunology, Urinalysis, Lab Operations) with
  Quick Review / Deep Study modes, high-yield tables, exam traps, mnemonics,
  comparison tables, references, practice questions.
- FSRS-4.5 smart flashcards: real spaced-repetition scheduling, Again/Hard/Good/Easy
  ratings with next-review previews, cloze cards, favorites, CSV export.
- Quiz engine: study / exam / mock (100 questions, 150 minutes) / adaptive practice
  modes, per-choice rationales, "Why was I wrong?" misconception explanations,
  results analytics. Practice scores are estimates, never ASCP scores.
- Smart PDF reader (pdf.js): search, bookmarks, resume position, 5 semantic
  highlight colors with editable meanings, notes, AI tools panel.
- Habit tracker, study diary with mood tracking, streaks with rest-day support.
- Study planner with mastery map and weakness tracker.
- Educational humor system: memes, memory hooks, guess-the-diagnosis, laboratory
  mystery, impostor, boss battle, mistake of the day (each with a verified
  scientific fact), meme-to-flashcard export. Serious Mode hides it all.
- Glossary (44 seed terms), formula sheet (18 formulas), global search.
- Admin content management workspace with editorial statuses and audit trail.
- Settings: dark mode, text scale 100–150%, reduce motion, TTS, mascot choice
  (cat Mimi / dog Bantay), data export/reset, disclaimers.

### Android (Kotlin + Jetpack Compose + Material 3)
- Offline-first app (Room) mirroring the web experience: onboarding, dashboard,
  library, FSRS flashcards, quiz engine with mock exam, habits and diary.
- PDF reader (PdfRenderer) with bookmarks, resume page, 5-color highlights and
  notes, best-effort text search, AI panel with graceful offline fallback.
- Android-only focus mode: Pomodoro timer (25/5, 50/10, custom), distracting-app
  picker, Usage Access detection + overlay warnings with mascot reactions,
  notification fallback when permissions are missing, focus session stats.
- Mascot study buddies Mimi (cat) and Bantay (dog) with moods and Taglish lines.
- GitHub Releases update checker (semver compare, release notes, dismissible —
  never auto-installs APKs).
- Optional sign-in with WorkManager sync to the shared backend.

### Backend (Ktor 3 + PostgreSQL)
- Full REST API (shared/api/API.md): JWT auth with rotating refresh tokens,
  profiles, study plans, sessions, flashcards + FSRS schedules, quizzes, PDFs and
  annotations, habits, diary, analytics, admin CMS with audit trail.
- Provider-agnostic AI layer (OpenAI/Anthropic/Gemini/local stub) with server-side
  keys, citations, token budgets, caching, retries; `503 AI_UNAVAILABLE` fallback.
- Flyway migrations, per-user authorization, rate limiting, upload validation,
  complete account deletion, `/me/export` data portability.

### Shared
- 210 original practice questions with per-choice rationales and misconception notes.
- 264 flashcards across 14 decks, 18 validated lab formulas, 44 glossary terms,
  17 meme/mini-game entries.
- Contracts: API.md, CONTENT_SCHEMA.md, FSRS.md, DESIGN.md.

### Tooling
- GitHub Actions: CI (content validation, web tests/build, backend tests, Android
  tests/APK), website deploy to GitHub Pages, Android build artifacts, tag-driven
  release automation with APK/AAB attachments.
