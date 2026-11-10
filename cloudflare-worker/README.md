# MedTech Review Hub — Cloudflare Worker Backend

Production deployment of the shared API contract (`shared/api/API.md`) running on
**Cloudflare Workers + D1**, with AI tools powered by **Google Gemini**.

Live URL: `https://medtech-review-hub-api.dazoarmando.workers.dev`
(health check: `/api/v1/health`)

The Kotlin backend in `../backend/` remains the self-hostable reference
implementation (PostgreSQL). This Worker implements the same contract with D1
(SQLite) so the API can run entirely on Cloudflare.

## Stack

- Cloudflare Workers (TypeScript, no build step — wrangler bundles `src/`)
- Cloudflare D1 (`schema.sql`, seeded from `shared/content/`)
- Gemini API (`gemini-2.0-flash` by default) — key stored as a Worker secret
- PBKDF2-SHA256 password hashing, HS256 JWTs (Web Crypto)

## Implemented endpoints

Auth (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`,
`DELETE /auth/me`) · `GET/PATCH /me`, `PUT /me/profile`, `GET /me/export` ·
content (`/content/subjects|lessons|lessons/{id}|questions|formulas|memes|glossary`) ·
`GET/PUT /plan`, `POST /plan/regenerate`, `POST/GET /sessions`,
`GET /analytics/overview` · flashcards (CRUD, `/review`, `/queue`, `/import`,
`/export`) · quizzes (`/quizzes/start|{id}/answer|{id}/finish`, list,
`/questions/{id}/report`) · `/habits`, `/habits/log`, `/diary`, `/streak` ·
PDF metadata + annotations sync · AI (`/ai/summarize|explain|flashcards|quiz|
keyterms|outline|diary-summary`).

Not in the Worker (use the Ktor backend if needed): admin CMS routes, PDF binary
file storage (R2 binding stubbed in wrangler.toml), mail-based password reset.

## Local development

Requires Node 22.12+ for wrangler 4 (on this machine: `nvm use 22.17.0`).

```bash
npm install
npx wrangler dev           # http://localhost:8787
```

## Deploy

```bash
npx wrangler deploy
```

## Database

```bash
npx wrangler d1 create medtech-review-hub   # once; copy database_id into wrangler.toml
npx wrangler d1 execute medtech-review-hub --remote --file=schema.sql
npm run db:seed                              # loads shared/content into D1
```

## Secrets (never committed)

| Secret | Set with | Purpose |
|---|---|---|
| `JWT_SECRET` | `npx wrangler secret put JWT_SECRET` | JWT signing (already set) |
| `GEMINI_API_KEY` | `npx wrangler secret put GEMINI_API_KEY` | Gemini AI tools |

Vars in `wrangler.toml`: `GEMINI_MODEL`, `AUTH_PBKDF2_ITERATIONS`,
`AI_DAILY_TOKEN_BUDGET`, `ALLOWED_ORIGINS`.

## AI behavior (enforced in `src/gemini.ts`)

- The Gemini key exists only as a Worker secret — never in the web bundle or APK.
- Prompts require citations to the source text and an explicit
  "Not found in the provided document." instead of invented answers.
- Medical ambiguity is surfaced as `WARNING:` lines in the `warnings` field.
- Per-user daily token budget + in-memory response cache + retry with backoff.
- Without `GEMINI_API_KEY`, all `/ai/*` routes return `503 AI_UNAVAILABLE` and the
  clients remain fully functional.

## Security notes

- Per-isolate in-memory rate limiting (120 req/min general, 20/min `/ai/*`).
  For strict global limits add Durable Objects or an external store.
- Refresh tokens are rotating and stored as SHA-256 hashes.
- Account deletion removes every user row (verified in CI tests of the Ktor
  backend; same table policy here).
- All private routes require a bearer token and filter queries by `user_id`.
