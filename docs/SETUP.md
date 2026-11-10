# Local Setup

## Prerequisites

| Component | Requirement |
|---|---|
| Website | Node.js 20+ (21 used in CI), npm |
| Android | JDK 17, Android SDK (API 35, build-tools 35+), Gradle wrapper included |
| Backend | JDK 17, PostgreSQL 15+ (or H2 for tests), Gradle wrapper included |

## Website

```bash
cd web
npm install
npm run dev          # http://localhost:3000
npm test             # vitest unit tests
npm run typecheck
npm run build        # static export in web/out
```

Environment variables (optional — the app is fully functional offline):

| Var | Purpose |
|---|---|
| `NEXT_PUBLIC_BASE_PATH` | URL prefix for subpath hosting (GitHub Pages project sites: `/MedTech-Review-Hub`) |
| `NEXT_PUBLIC_API_URL` | Backend URL (can also be set at runtime in Settings → Sync) |

`npm run dev`/`npm run build` automatically sync `shared/content/` into the web
app and copy the pdf.js worker into `public/`.

## Android

```bash
cd android
# create local.properties with: sdk.dir=<path to Android SDK>
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk  (sideload with adb install)
```

Release signing (never commit keys) — either export environment variables
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`) or configure them as GitHub Actions secrets for CI
releases. See `docs/RELEASE.md`.

## Backend

```bash
cd backend
cp .env.example .env    # then edit values
# Start PostgreSQL, e.g.:
#   docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=medtech -e POSTGRES_DB=medtech postgres:15
./gradlew run           # http://localhost:8080/api/v1/health
./gradlew test          # runs against in-memory H2 (PostgreSQL mode)
```

Key environment variables (see `backend/.env.example`): `DATABASE_URL`,
`DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET`, `PORT`, `AI_PROVIDER`,
`OPENAI_API_KEY`/`ANTHROPIC_API_KEY`/`GEMINI_API_KEY`, `CONTENT_DIR`, `MEDIA_ROOT`.

Schema migrations run automatically via Flyway on startup
(`backend/src/main/resources/db/migration`).

## Content library

All review content is JSON in `shared/content/` (schema: `shared/CONTENT_SCHEMA.md`).
The web app, Android app, and backend all consume the same files:

- web: copied to `web/src/data/content` at build time
- Android: copied into assets by the `copyContentAssets` Gradle task
- backend: loaded from `CONTENT_DIR` (defaults to `shared/content`)
