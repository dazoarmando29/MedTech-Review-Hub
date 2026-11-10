# Limitations & Unfinished Work

Honest inventory as of v1.0.0.

## Platform limitations

- **Android app blocking is not absolute.** Android does not let normal apps
  guarantee blocking of other apps. Detection uses Usage Access (user-granted);
  the warning overlay is dismissible and OEM battery optimizers may affect
  polling. When permissions are missing, the app falls back to notifications and
  focus reminders. Clearly disclosed in-app and in `android/README.md`.
- **PDF text extraction on Android is best-effort** (custom extractor over PDF
  content streams). Scanned/image-only PDFs render fine but have no extractable
  text ("likely scanned" notice); encrypted PDFs are unsupported. The website's
  pdf.js reader handles text search more completely.
- **Text-to-speech** uses the Web Speech API (browser-dependent voices) and the
  Android system TTS where available.
- **GitHub Pages hosts only the static website.** The Ktor backend + PostgreSQL
  must be deployed separately (`docs/DEPLOYMENT.md`).

## Simplifications

- **Cloudflare Worker backend** (production) vs Ktor backend (reference):
  the Worker implements the same v1 contract but does not include the admin CMS
  routes, mail-based password reset, or PDF binary file storage (metadata +
  annotations do sync; R2 binding is prepared in `cloudflare-worker/wrangler.toml`).
- **Worker rate limiting** is per-isolate in-memory (best-effort); use Durable
  Objects for strict global limits.
- **Backend rate limiting** (Ktor) is an in-memory token bucket (fine for a single
  instance; swap in Redis for horizontal scaling).
- **PDF file storage** on the backend is local disk (`MEDIA_ROOT`); swap `PdfService`
  for S3-compatible object storage in production.
- **AI citation enforcement** is prompt-level + warning flags — it is not a
  fact-verification engine. Human review of AI-generated study content is still
  required before publishing (editorial workflow enforces this).
- **Forgot-password** returns a dev token unless a mail provider is configured.
- **Adaptive practice** uses a documented difficulty-adjustment rule; it is NOT a
  reproduction of ASCP's computer-adaptive testing and says so in the UI.
- **Web "session minutes"** are tracked from explicit study actions; Android focus
  sessions track wall-clock Pomodoro time.
- **Anki import** supports CSV and a documented deck JSON format; full `.apkg`
  binary compatibility is not implemented (licensing/complexity).

## Unfinished / future work

- Real-time multi-device conflict resolution (currently last-write-wins).
- End-to-end browser tests (Playwright) — unit + integration suites exist.
- Localization of the full UI (content is English + optional Taglish notes).
- Image occlusion flashcards (image-based cards exist as a type; no editor UI yet).
- Google Play in-app updates for Play-distributed installs (sideload releases use
  the GitHub Releases checker).
- Shared Kotlin Multiplatform domain module — currently the FSRS/quiz/plan logic is
  ported and cross-tested by contract (`shared/FSRS.md`) rather than shared as a
  KMP library (deliberate: KMP toolchain would add significant build complexity
  for the same guarantees at this stage).

## Test status (v1.0.0)

- Web: 34 vitest tests passing (FSRS formulas + scheduling flow, quiz selection/
  grading/adaptive, plan generation, search).
- Backend: 17 JUnit/ktor tests passing (auth lifecycle, password policy, data
  isolation, flashcards, quiz scoring, content, upload validation, AI fallback,
  admin auth, account deletion).
- Android: 70 JUnit tests passing (FSRS parity, quiz scoring, semver, PDF text
  extractor, plan generator, ViewModels).
