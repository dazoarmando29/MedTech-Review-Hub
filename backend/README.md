# MedTech Review Hub — Backend

Ktor 3 service implementing `shared/api/API.md` (REST contract) and serving the review
library defined in `shared/CONTENT_SCHEMA.md`.

- Kotlin 2.0.20, Ktor 3.0.3 (Netty), Exposed 0.56.0, Flyway 10.20.1, kotlinx-serialization 1.7.3
- PostgreSQL in production, H2 (PostgreSQL mode) for tests
- Gradle wrapper 8.11.1 (no local Gradle install needed)

## Run

```powershell
# from MedTech-Review-Hub/backend
copy .env.example .env        # then edit values (or export env vars another way)
.\gradlew.bat run             # starts on http://localhost:8080
```

Minimum viable local setup: just set `DATABASE_URL`/`DATABASE_USER`/`DATABASE_PASSWORD`
for a Postgres database, or run against H2 without any database server:

```
DATABASE_URL=jdbc:h2:mem:medtech;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
DATABASE_USER=sa
```

Environment variables (full list and defaults in `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` / `HOST` | `8080` / `0.0.0.0` | Netty bind |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/medtech` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `medtech` / empty | DB credentials |
| `JWT_SECRET` / `JWT_ISSUER` | `dev-only-change-me` / `medtech-review-hub` | JWT signing |
| `AI_PROVIDER` | `none` | `none\|local\|openai\|anthropic\|gemini` |
| `AI_DAILY_TOKEN_BUDGET` | `20000` | per-user AI tokens/day |
| `OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `GEMINI_API_KEY` | — | provider keys (server-side only) |
| `CONTENT_DIR` | empty | `shared/content/` directory; falls back to bundled sample |
| `ALLOW_ALL_ORIGINS` / `ALLOWED_ORIGINS` | `true` / empty | CORS policy |
| `MEDIA_ROOT` / `UPLOAD_MAX_BYTES` | `media` / `52428800` | PDF storage dir and 50 MB cap |
| `RATE_LIMIT_GENERAL` / `RATE_LIMIT_AI` | `100` / `20` | requests per minute |
| `ADMIN_EMAILS` | empty | auto-ADMIN at registration |

The first start runs Flyway (`backend/src/main/resources/db/migration`) and seeds glossary
and source references from the content library.

## Test

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat build --console=plain
```

17 tests (JUnit 5 + ktor-server-test-host, each on its own in-memory H2 database) cover:
auth lifecycle (register → login → `/me` → refresh rotation → delete → login fails),
password policy, cross-user data isolation, flashcard CRUD + FSRS-4.5 review roundtrip,
quiz start/answer/finish with `bySubject` scoring math (and EXAM answer masking),
bundled content endpoints, PDF upload validation (non-PDF + oversize), AI 503
`AI_UNAVAILABLE` when unconfigured, admin authorization + editorial transitions + audit
log, plan generation weighting (weak subjects ×1.5), and full account deletion (every
table checked at 0 rows afterwards).

## API tour

Base path `/api/v1` (contract: `shared/api/API.md`). All private routes take
`Authorization: Bearer <accessToken>`. Errors always use the envelope
`{ "error": { "code", "message", "details" } }` with codes `VALIDATION_ERROR`,
`UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`,
`PAYLOAD_TOO_LARGE`, `INTERNAL` (plus `AI_UNAVAILABLE` for unconfigured AI).

```
Auth      POST /auth/register | /auth/login | /auth/refresh | /auth/logout
          POST /auth/forgot-password | /auth/reset-password   DELETE /auth/me
Users     GET/PATCH /me   PUT /me/profile   GET /me/export
Content   GET /content/subjects   GET /content/lessons[?subject=&topic=&q=]
          GET /content/lessons/{id}   GET /content/questions[?subject=&topic=&limit=]
          GET /content/formulas   GET /content/memes                       (public)
Plan      GET/PUT /plan   POST /plan/regenerate   POST /sessions
          GET /sessions?from=&to=   GET /analytics/overview
Cards     GET/POST /flashcards   PATCH/DELETE /flashcards/{id}
          POST /flashcards/{id}/review   GET /flashcards/queue
          POST /flashcards/import   GET /flashcards/export?format=csv
Quizzes   POST /quizzes/start   POST /quizzes/{id}/answer
          POST /quizzes/{id}/finish   GET /quizzes/{id}   GET /quizzes?status=
          POST /questions/{id}/report
PDFs      POST /pdfs (multipart)   GET /pdfs   GET /pdfs/{id}/file
          DELETE /pdfs/{id}   GET/PUT /pdfs/{id}/annotations
AI        POST /ai/summarize | /ai/flashcards | /ai/quiz | /ai/explain
          POST /ai/outline | /ai/keyterms | /ai/diary-summary
Habits    GET/POST /habits   DELETE /habits/{id}   POST /habits/log
          GET/POST /diary   GET /streak
Admin     GET/POST /admin/{subjects,topics,lessons,questions}
          PATCH /admin/{subjects,topics,lessons,questions}/{id}
          POST /admin/lessons/{id}/publish   GET /admin/reports   GET /admin/audit
Health    GET /health (also at /api/v1/health)
```

Quick smoke test:

```powershell
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/content/subjects
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" `
  -d '{"email":"me@example.com","password":"supersecret99","displayName":"Me"}'
```

## Content library

On startup the server loads `shared/content/` (set `CONTENT_DIR`) — otherwise the small
bundled fallback sample in `src/main/resources/content/` (2 subjects, 2 lessons,
6 questions, 1 deck + formulas/memes/glossary) so it works standalone. Question objects
always include `correctChoiceId`, per-choice `rationales`, `misconception`, and
`reference`. Admin-managed rows reach the public endpoints once they are `PUBLISHED`.

## Study plan generator

`POST /plan/regenerate` builds a 4-week (28-day) daily plan from the profile: exam
target, target date, weak/strong subjects, minutes/day and preferred schedule windows.
Subject time uses ASCPi style content-outline weights (blood-banking 9%,
clinical-chemistry 17%, hematology 16%, microbiology 18%, immunology 10%, urinalysis 10%,
lab-operations 12%) labeled **"approximate content outline weights, not official
percentages"**; `PH_BOARD` uses a separate PH MTLE oriented allocation. Weak subjects get
1.5× time. Empty weekdays in `preferredSchedule` become rest days.

## Honest notes / intentional simplifications

- **File storage is local disk** under `MEDIA_ROOT/<userId>/<pdfId>.pdf`. For production,
  swap `PdfService`/`PdfRepository` storage for S3 (or similar) — the service boundary is
  already isolated behind `PdfService`.
- **Rate limiting is in-memory** (token bucket per user/IP: 100 req/min general,
  20 req/min for `/ai/*`). Replace with Redis (or an API gateway) before running multiple
  instances, or per-instance limits will each be independent.
- **AI is optional.** With `AI_PROVIDER=none` (default) every `/ai/*` route returns
  `503 {"error":{"code":"AI_UNAVAILABLE",...}}` and clients must work without it. The
  provider adapter (`LOCAL`/`OPENAI`/`ANTHROPIC`/`GEMINI`) retries twice with backoff,
  caches responses in an in-memory LRU, enforces a per-user daily token budget, and the
  prompts require `[cite]quote[/cite]` citations or the phrase "not found in document".
  Post-generation, responses lacking citations are flagged in `warnings` — this is a
  prompt-and-flag level enforcement, not a formal claim-verification engine.
- **FSRS-4.5** is implemented server-side per `shared/FSRS.md` (learning steps 1/10 min,
  relearning 10 min); web/Android must stay in sync with the shared reference.
- **Adaptive quizzes** use a documented easy/medium/hard interleave — they are *not* ASCP
  CAT (see `docs/DISCLAIMERS.md`).
- **Upload size enforcement** buffers the multipart part in memory before the 50 MB check;
  for very large limits, stream to disk with an early abort instead.
- **Forgot-password** returns a `devToken` in the response because no mail provider is
  configured; wire an email service before production and stop returning the token.
- **Timestamps** are stored as `BIGINT` epoch milliseconds (UTC) and serialized as
  ISO-8601 strings at the API boundary; JSON blobs live in `TEXT` columns (no `jsonb`) so
  the H2 test suite runs the exact same migrations as Postgres.
- **Role management** is by `users.role` plus `ADMIN_EMAILS`; there is no admin UI or
  role-change endpoint yet.
