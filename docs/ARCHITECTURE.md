# Architecture

## Overview

Three independently deployable clients share one contract and one content library:

```
                 ┌────────────────────────────┐
                 │  shared/                   │
                 │  API.md · CONTENT_SCHEMA   │
                 │  FSRS.md · DESIGN.md       │
                 │  content/ (lessons, Qs, FCs)│
                 └─────────┬──────────────────┘
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
│ web/         │   │ android/     │   │ backend/         │
│ Next.js 14   │   │ Kotlin+Comp. │   │ Ktor 3 + Exposed │
│ static export│   │ Room offline │   │ Flyway + Postgres│
│ localStorage │   │ WorkManager  │   │ JWT auth, AI proxy│
│ + IndexedDB  │   │ FSRS engine  │   │ /api/v1          │
└──────┬───────┘   └──────┬───────┘   └────────┬─────────┘
       │  local-first     │                    │
       └──────────── sync (optional) ──────────┘
```

## Sync model

Clients are **local-first**: everything works offline. When a backend URL is
configured and the user signs in, entities sync with last-write-wins per entity
(`updatedAt`); flashcard schedules merge by the later `lastReviewAt`, then higher
`reps`. The backend is the source of truth for cross-device state; PDFs uploaded
to the backend stay private to their owner.

## Spaced repetition

`shared/FSRS.md` specifies FSRS-4.5 (default weights, retrievability model,
learning steps 1/10 min, rating semantics). Implementations:
`web/src/lib/fsrs.ts` and
`android/.../domain/SpacedRepetition.kt` — kept in lockstep and unit-tested on
both platforms. Ratings (Again/Hard/Good/Easy) reschedule cards from predicted
recall; the UI shows the next interval per rating.

## Content pipeline

`shared/content/` is the single source of truth for lessons, questions, decks,
formulas, glossary, and meme/mini-game entries. Editorial states:
`DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED`; AI-generated material enters as
`DRAFT` with `needsVerification` and cannot skip human review. References are
mandatory; reference ranges must state units and lab/method variability.

## AI layer (backend)

Provider-agnostic adapter (`OPENAI` | `ANTHROPIC` | `GEMINI` | `LOCAL`). API keys
exist only in server environment variables — never in the website bundle or APK.
Every AI response carries citations to the user's document pages/passages or an
explicit "not found in document" statement, plus optional review warnings.
Per-user token budgets, response caching, retries with backoff, rate limiting.
If AI is unconfigured, endpoints return `503 AI_UNAVAILABLE` and clients continue
fully functional.

## Data model

Normalized tables for users, profiles, plans, sessions, content + revisions +
references, PDFs + annotations, glossary, decks/cards/reviews, questions/attempts/
answers, analytics, habits, diary, mascot/notification preferences, focus
sessions, generated AI content, issue reports, and an audit log. Migrations:
Flyway (`backend/src/main/resources/db/migration`), reference DDL in
`database/schema.sql`. All private rows are keyed to `user_id`; authorization is
checked on every private resource; account deletion removes all user rows.

## Android-only focus tools

The app blocker lives only in the Android app (Usage Access + optional overlay,
explicit permission flows, notification fallback). The website intentionally
offers only a Pomodoro-friendly environment — no blocking — per the product spec
and platform policies.
