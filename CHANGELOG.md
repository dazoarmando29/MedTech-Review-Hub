# Changelog

All notable changes to MedTech Review Hub are documented here.
Versioning follows Semantic Versioning (`vMAJOR.MINOR.PATCH`).

## [Unreleased]

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
