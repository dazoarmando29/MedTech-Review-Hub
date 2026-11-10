# MedTech Review Hub — Web Client

Responsive Next.js 14 + React + TypeScript study platform for the ASCPi MLS board exam.
Fully functional offline (local-first); optionally syncs with the Ktor backend.

## Stack

- Next.js 14 (App Router, static export), React 18, TypeScript, Tailwind CSS
- pdf.js (`pdfjs-dist`) for the smart PDF reader
- FSRS-4.5 spaced repetition (`src/lib/fsrs.ts`, identical to the Android implementation)
- vitest unit tests

## Develop

```bash
npm install
npm run dev        # http://localhost:3000
```

## Build & test

```bash
npm test           # vitest unit tests (FSRS, quiz engine, planner, search)
npm run typecheck  # tsc --noEmit
npm run build      # static export -> web/out (deployable to GitHub Pages, Vercel, Cloudflare Pages)
```

## Configuration

| Env var | Purpose | Default |
|---|---|---|
| `NEXT_PUBLIC_BASE_PATH` | URL prefix (GitHub Pages project sites need `/MedTech-Review-Hub`) | `''` |
| `NEXT_PUBLIC_API_URL` | Backend base URL (or set it in Settings at runtime) | `''` = offline mode |

The site works with **no backend configured** — all study data lives in `localStorage`
(PDFs in IndexedDB). Set the API URL in Settings to enable accounts + cross-device sync
and server-side AI tools (summarize, flashcards generation, simple-English explanations).

## Feature map

| Route | Feature |
|---|---|
| `/` | Dashboard: goals, streak, due cards, countdown, weak subject, continue reading |
| `/onboarding/` | Personalized study plan wizard |
| `/library/` | 7-subject ASCPi review library (Quick Review / Deep Study modes) |
| `/flashcards/` | FSRS-4.5 spaced repetition, cloze cards, CSV export |
| `/quiz/` | Study / exam / mock (100Q · 150 min) / adaptive practice modes |
| `/pdf/` | PDF reader: search, bookmarks, 5-color highlights, AI tools |
| `/habits/` | Habit tracker, diary, mood, streak, weekly summary |
| `/planner/` | Study plan, mastery map, weakness tracker |
| `/games/` | Memes, mnemonics, guess-the-diagnosis, impostor, boss battle |
| `/glossary/` `/formulas/` `/search/` | Glossary, formula sheet, global search |
| `/admin/` | Content editorial workflow + audit trail |
| `/settings/` | Theme, text scale, serious mode, mascot, data export, disclaimers |

## Deployment

`npm run build` produces a static site in `out/`. GitHub Actions workflow
`.github/workflows/website-deploy.yml` publishes it to GitHub Pages on every push to `main`.
For Vercel/Cloudflare Pages: import the repo, set the project root to `web/`, build command
`npm run build`, output directory `out` (set `NEXT_PUBLIC_BASE_PATH` for subpath hosts).
Rollback = redeploy any previous commit (each deployment is tied to a commit SHA).
