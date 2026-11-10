# MedTech Review Hub — Backend API Contract (v1)

Base URL: `https://<api-host>/api/v1` (configurable; local dev: `http://10.0.2.2:8080/api/v1` from Android emulator).

All private endpoints require `Authorization: Bearer <accessToken>`.
All timestamps are ISO-8601 UTC strings.
All endpoints validate input; unknown fields are ignored. Errors return:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "human readable", "details": {} } }
```

Error codes: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `PAYLOAD_TOO_LARGE`, `INTERNAL`.

API versioning: URL segment `/api/v1`. Additive changes only within v1.

---

## 1. Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | — | Create account |
| POST | `/auth/login` | — | Get tokens |
| POST | `/auth/refresh` | refresh token | Rotate tokens |
| POST | `/auth/logout` | bearer | Revoke refresh token |
| POST | `/auth/forgot-password` | — | Send reset email (or return dev token when mail is unconfigured) |
| POST | `/auth/reset-password` | — | Reset with token |
| DELETE | `/auth/me` | bearer | Delete account + all private data |

`POST /auth/register`
```json
{ "email": "student@example.com", "password": "min10chars", "displayName": "Dazz" }
```
→ `201` `{ "user": User, "accessToken": "...", "refreshToken": "...", "expiresIn": 3600 }`

`POST /auth/login` `{ "email", "password" }` → same shape as register (`200`).

Password rules: min 10 chars, not in a small common-password deny list. Hashing: bcrypt (cost 10+).

Access token TTL 1h, refresh token TTL 30d, refresh tokens are rotating and revocable.

## 2. Users & profile

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/me` | bearer | Get `User` + `Profile` |
| PATCH | `/me` | bearer | Update displayName, avatarMascot (`cat`\|`dog`), locale (`en`\|`taglish`), theme, seriousMode |
| GET | `/me/export` | bearer | Full JSON export of user data (portability) |

```json
User = { "id": "uuid", "email": "...", "displayName": "...", "createdAt": "..." }
Profile = {
  "examTarget": "MLS_ASCPI" | "PH_BOARD" | "CUSTOM",
  "targetExamDate": "2026-11-30" | null,
  "knowledgeLevel": "BEGINNER" | "INTERMEDIATE" | "ADVANCED",
  "strongestSubjects": ["clinical-chemistry"],
  "weakestSubjects": ["blood-banking"],
  "minutesPerDay": 120,
  "preferredSchedule": { "mon": [120, 1260], "tue": [] },
  "learningPreferences": ["reading", "flashcards"],
  "explanationStyle": "FORMAL_ENGLISH" | "SIMPLE_ENGLISH" | "TAGLISH",
  "notificationsEnabled": true,
  "focusFeatureEnabled": true,
  "onboardingCompleted": true
}
```
`preferredSchedule` maps weekday → array of [startMinuteOfDay, endMinuteOfDay] windows.

| Method | Path | Auth | Description |
|---|---|---|---|
| PUT | `/me/profile` | bearer | Replace profile (same shape) |

## 3. Content (public, read-only)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/content/subjects` | — | List subjects with topics |
| GET | `/content/lessons/{id}` | — | Full lesson (see shared/CONTENT_SCHEMA.md) |
| GET | `/content/lessons?subject=&topic=&q=` | — | Lesson summaries (searchable) |
| GET | `/content/questions?subject=&topic=&limit=` | — | Quiz questions |
| GET | `/content/formulas` | — | Formula sheet entries |
| GET | `/content/memes` | — | Meme/memory-hook entries |

Content is also shipped as static JSON in `shared/content/` for offline use; the backend serves the same data so all clients stay consistent.

## 4. Study plan & sessions

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/plan` | bearer | Current study plan (generated if missing) |
| PUT | `/plan` | bearer | Replace/edit plan |
| POST | `/plan/regenerate` | bearer | Regenerate from profile |
| POST | `/sessions` | bearer | Record study session |
| GET | `/sessions?from=&to=` | bearer | List sessions |
| GET | `/analytics/overview` | bearer | Dashboard stats |

```json
StudyPlan = {
  "id": "uuid", "generatedAt": "...",
  "days": [ { "date": "2026-06-02", "items": [
     { "id": "uuid", "type": "READ"|"FLASHCARDS"|"QUIZ"|"REVIEW_WRONG"|"FORMULAS",
       "subjectId": "hematology", "topicId": "cbc", "lessonId": null,
       "targetMinutes": 30, "completed": false } ] } ]
}

StudySession = { "id", "startedAt", "endedAt", "kind": "READ"|"FLASHCARDS"|"QUIZ"|"FOCUS"|"PDF",
                 "subjectId", "minutes", "itemsCompleted" }

AnalyticsOverview = {
  "streakDays": 12, "totalMinutes": 3420, "minutesToday": 45,
  "flashcardsDueToday": 27, "questionsAnsweredToday": 30,
  "recentQuizAccuracy": 0.78, "weakestSubjectId": "blood-banking",
  "examCountdownDays": 58, "weeklyCalendar": [{ "date": "2026-06-01", "minutes": 60 }],
  "mastery": [{ "topicId": "abo", "subjectId": "blood-banking", "mastery": 0.62 }]
}
```

## 5. Flashcards (shared between web + Android)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/flashcards?deck=&tag=&due=true` | bearer | List cards |
| POST | `/flashcards` | bearer | Create card |
| PATCH | `/flashcards/{id}` | bearer | Update card |
| DELETE | `/flashcards/{id}` | bearer | Delete card |
| POST | `/flashcards/{id}/review` | bearer | Submit rating |
| GET | `/flashcards/queue` | bearer | Today's queue (new/learning/review) |
| POST | `/flashcards/import` | bearer | Import CSV/Anki-style rows |
| GET | `/flashcards/export?format=csv` | bearer | Export cards |

```json
Flashcard = {
  "id", "deckId", "front", "back", "type": "BASIC"|"CLOZE"|"IMAGE"|"KEYWORD_RECALL",
  "tags": ["gram-stain"], "subjectId", "topicId", "difficulty": 1|2|3,
  "note", "favorite": false, "imageRef": null,
  "source": { "kind": "MANUAL"|"PDF_HIGHLIGHT"|"QUIZ_MISS"|"AI"|"MEME",
              "lessonId": null, "pdfId": null, "page": null, "needsReview": false },
  "schedule": {
    "state": "NEW"|"LEARNING"|"REVIEW"|"RELEARNING", "stability": 0.0, "difficulty": 0.0,
    "dueAt": "2026-06-03T00:00:00Z", "lastReviewAt": null, "reps": 0, "lapses": 0
  },
  "history": [{ "at": "...", "rating": "AGAIN"|"HARD"|"GOOD"|"EASY", "intervalDays": 2.5 }]
}

POST /flashcards/{id}/review  { "rating": "GOOD", "reviewedAt": "..." }  → { "schedule": {...} }
```

Scheduling algorithm: see `shared/FSRS.md` (FSRS-4.5, default parameters). Web and Android MUST use identical logic (`shared/fsrs` reference implementations kept in sync).

## 6. Quizzes

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/quizzes/start` | bearer | Start quiz session (server-generated or custom) |
| POST | `/quizzes/{id}/answer` | bearer | Submit answer |
| POST | `/quizzes/{id}/finish` | bearer | Finish + get results |
| GET | `/quizzes/{id}` | bearer | Get attempt state |
| GET | `/quizzes?status=` | bearer | List past attempts |
| POST | `/questions/{id}/report` | bearer | Report content issue |

```json
QuizQuestion = {
  "id", "subjectId", "topicId", "vignette": "..." | null,
  "stem": "Which enzyme test ...?",
  "choices": [{ "id": "A", "text": "..." }, ...],
  "correctChoiceId": "B", "difficulty": "EASY"|"MEDIUM"|"HARD",
  "explanation": "full teaching explanation",
  "rationales": { "A": "why wrong", "B": "why right", "C": "why wrong", "D": "why wrong" },
  "misconception": "the trap behind the common wrong answer",
  "reference": "Harmening, Blood Banking ..., 6th ed., Ch. 3",
  "lessonId": "blood-banking/abo-basics", "type": "MCQ"|"IMAGE"|"SCENARIO"
}

QuizResult = {
  "score": 0.82, "correct": 41, "total": 50, "timeSeconds": 1830,
  "bySubject": [{ "subjectId", "correct", "total" }],
  "weakTopics": [{ "topicId", "accuracy" }],
  "incorrectQuestionIds": ["..."]
}
```

Modes: `STUDY` (immediate feedback), `EXAM` (feedback after finish), `ADAPTIVE` (documented difficulty selection; **not** ASCP CAT — see docs/DISCLAIMERS.md).

## 7. PDFs & annotations

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/pdfs` | bearer | Upload PDF (multipart, ≤ 50 MB, `application/pdf` only) |
| GET | `/pdfs` | bearer | List own documents |
| GET | `/pdfs/{id}/file` | bearer | Download own document |
| DELETE | `/pdfs/{id}` | bearer | Delete document + annotations |
| GET | `/pdfs/{id}/annotations` | bearer | List annotations |
| PUT | `/pdfs/{id}/annotations` | bearer | Replace annotation set (sync) |

```json
Annotation = {
  "id", "pdfId", "page": 12, "type": "HIGHLIGHT"|"NOTE"|"BOOKMARK",
  "color": "YELLOW"|"RED"|"BLUE"|"GREEN"|"PURPLE",
  "selectedText": "Staphylococcus aureus is ...", "note": "",
  "rect": { "x": 0.1, "y": 0.2, "w": 0.4, "h": 0.03 }, "createdAt": "..."
}
```
Documents are private to their owner. No endpoint serves another user's document.

## 8. AI tools (server-side keys only)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/ai/summarize` | bearer | `{ "text", "mode": "PAGE"|"PASSAGE"|"CHAPTER", "pdfId"?, "page"? }` |
| POST | `/ai/flashcards` | bearer | Generate flashcards from text |
| POST | `/ai/quiz` | bearer | Generate MCQs from text |
| POST | `/ai/explain` | bearer | `{ "text", "style": "SIMPLE_ENGLISH"|"TAGLISH" }` |
| POST | `/ai/outline` | bearer | Study outline from text |
| POST | `/ai/keyterms` | bearer | Extract key terms |
| POST | `/ai/diary-summary` | bearer | Summarize recent diary entries |

Response envelope for every AI endpoint:
```json
{ "result": { ... }, "citations": [{ "pdfId", "page", "quote" }],
  "warnings": ["Flagged: reference range may be outdated"], "usage": { "cached": true } }
```
Rules enforced server-side: provider-agnostic adapter (`OPENAI`|`ANTHROPIC`|`GEMINI`|`LOCAL`), API keys only from env, per-user rate limit + token budget, response caching, and every generated fact must carry a citation to the user's document or be explicitly marked "not found in document". If AI is unconfigured, endpoints return `503` with code `AI_UNAVAILABLE`; clients must stay fully functional without AI.

## 9. Habits & diary

| Method | Path | Auth | Description |
|---|---|---|---|
| GET/POST/DELETE | `/habits` | bearer | Manage habits `{ "name", "targetPerDay", "kind": "MINUTES"|"COUNT" }` |
| POST | `/habits/log` | bearer | `{ "habitId", "date", "amount" }` |
| GET/POST | `/diary` | bearer | Private diary entries `{ "date", "mood", "text", "tags" }` |
| GET | `/streak` | bearer | `{ "current", "best", "freezeDaysLeft" }` |

Diary entries are private; they never appear in public content endpoints.

## 10. Admin content management

Role: `ADMIN` (column on users). All admin routes require role check.

| Method | Path | Description |
|---|---|---|
| GET/POST/PATCH | `/admin/subjects`, `/admin/topics`, `/admin/lessons`, `/admin/questions` | CRUD with `editorialStatus` |
| POST | `/admin/lessons/{id}/publish` | Publish reviewed draft (creates revision) |
| GET | `/admin/reports` | Content issue reports queue |
| GET | `/admin/audit` | Audit trail of editorial changes |

`editorialStatus`: `DRAFT` → `IN_REVIEW` → `PUBLISHED` → `ARCHIVED` (AI-generated content enters as `DRAFT` with `needsVerification=true`).

## 11. Health

`GET /health` → `{ "status": "ok", "version": "1.0.0", "database": "ok" }`

---

## Sync model (web ↔ Android)

Both clients keep a local store (Room / IndexedDB). Mutations are applied locally first and pushed to the backend; the backend is the source of truth for cross-device state. Conflict rule: last-write-wins per entity using `updatedAt`; flashcard schedules merge by choosing the schedule with the later `lastReviewAt` and the higher `reps`.
