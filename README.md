# MedTech Review Hub — ASCPi Board Exam Study Platform

An independent, medically grounded review platform for Filipino Medical Technology
graduates and international MLS(ASCPi) candidates: a responsive website, a Kotlin
Android app, and a shared sync backend.

> **Not affiliated with or endorsed by ASCP.** Practice scores are your own study
> estimates — never official ASCP scores or guarantees of passing. See
> `docs/DISCLAIMERS.md`.

## Repository layout

```
medtech-review-hub/
├── android/        Kotlin + Jetpack Compose app (offline-first, FSRS flashcards,
│                   quizzes, PDF reader, Android-only focus/app blocker, mascot)
├── web/            Next.js 14 + TypeScript website (static export, PWA)
├── backend/        Ktor 3 + PostgreSQL API (auth, sync, AI proxy, admin CMS)
├── shared/         Contracts: API.md, CONTENT_SCHEMA.md, FSRS.md, DESIGN.md
│   └── content/    Verified review library (lessons, questions, flashcards, formulas)
├── database/       Reference schema (migrations live in backend resources)
├── docs/           Setup, deployment, release, architecture, limitations
└── .github/        CI, website deploy, Android build, release workflows
```

## Quick start

**Website**
```bash
cd web
npm install
npm run dev        # http://localhost:3000
npm test           # 34 unit tests (FSRS, quiz engine, planner, search)
npm run build      # static export -> web/out
```

**Android**
```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug     # android/app/build/outputs/apk/debug/app-debug.apk
```

**Backend**
```bash
cd backend
./gradlew test
./gradlew run      # http://localhost:8080/api/v1/health (needs PostgreSQL, see backend/README.md)
```

Full instructions: `docs/SETUP.md`.

## Feature highlights

- **Review library** — 17 lessons across the 7 ASCPi content areas with Quick
  Review (high-yield) and Deep Study modes, exam traps, mnemonics, references.
- **FSRS-4.5 flashcards** — real spaced repetition (Again/Hard/Good/Easy), shared
  scheduling spec (`shared/FSRS.md`) implemented identically on web and Android.
- **Quiz & mock exam** — 210 original questions with per-choice rationales,
  study/exam/adaptive modes, 100-question 150-minute mock exam.
- **Smart PDF reader** — upload your own reviewers; search, highlights in 5
  semantic colors, notes, bookmarks, AI summarize/explain/flashcard tools.
- **Habits, diary, streaks, mastery map, weakness tracker, study planner.**
- **Memes & mini-games** — educational humor with mandatory scientific facts,
  Serious Mode to hide it all.
- **Android-only focus mode** — Pomodoro + app blocker with mascot reactions and
  honest permission handling (the website deliberately has no app blocking).
- **Cross-device sync** — same accounts and data on web + Android via the backend;
  everything works offline without an account.

## Deployment & releases

- Website: every push to `main` deploys to GitHub Pages (see `docs/DEPLOYMENT.md`
  for Vercel/Cloudflare alternatives and rollback).
- Android: `git tag v1.0.0 && git push origin v1.0.0` builds and attaches APK/AAB
  to a GitHub Release (see `docs/RELEASE.md`). Signing keys live in GitHub Actions
  secrets — never in the repository.

## Documentation

| Doc | Contents |
|---|---|
| `docs/SETUP.md` | Local environment setup for all three components |
| `docs/DEPLOYMENT.md` | Website + backend deployment, env vars, rollback |
| `docs/RELEASE.md` | Versioning, APK signing, release checklist |
| `docs/ARCHITECTURE.md` | System design, data model, sync model |
| `docs/LIMITATIONS.md` | Honest list of platform limits and unfinished work |
| `docs/DISCLAIMERS.md` | Educational/legal disclaimers |
| `CHANGELOG.md` | Version history |
