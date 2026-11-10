# Deployment

## Website (GitHub Pages — default)

Workflow: `.github/workflows/website-deploy.yml`. On every push to `main` that
touches `web/` or `shared/`, it runs tests, builds the static export with
`NEXT_PUBLIC_BASE_PATH=/MedTech-Review-Hub`, and publishes to GitHub Pages.

- **URL:** `https://dazoarmando29.github.io/MedTech-Review-Hub/`
- **Traceability:** every deployment is tied to a commit SHA (written to
  `.deployed-commit` in the published artifact and visible in the Actions run).
- **Logs:** GitHub → Actions → "Website Deploy" run → build + deploy steps.

### Rollback

1. Identify the last good commit: `git log --oneline` or the Actions history.
2. Option A (fast): Actions → "Website Deploy" → select the run for the good
   commit → "Re-run all jobs" (redeploys that commit's build).
3. Option B: `git revert <bad commit>` (or `git checkout <good> -- web/ shared/`),
   push to `main` — the workflow redeploys automatically.

## Website (Vercel / Cloudflare Pages)

Import the repository, set project root `web/`, build `npm run build`, output
directory `out`. Set `NEXT_PUBLIC_BASE_PATH` only if serving from a subpath.
Rollback = promote a previous deployment in the provider's dashboard (each one
maps to a commit).

## Backend

The backend deploys separately (the website is static; GitHub Pages cannot host
Ktor/PostgreSQL). Recommended: Render / Railway / Fly.io / any JVM host.

1. Provision PostgreSQL 15+ and note the JDBC URL.
2. Set the environment variables from `backend/.env.example` (use a strong
   `JWT_SECRET`; set `ALLOW_ALL_ORIGINS=false` + `ALLOWED_ORIGINS` in production).
3. Build: `./gradlew build` → run `java -jar backend/build/libs/...-all.jar`
   (or `./gradlew run` via your platform's start command).
4. Flyway migrates the schema on startup.

**Rollback:** redeploy the previous artifact/image; migrations are additive.
Restore from your scheduled `pg_dump` backups if a migration must be reverted.

**Backups:** schedule `pg_dump` (or your provider's automated backups). Uploaded
PDFs live in `MEDIA_ROOT` — back it up alongside the database.

## Android

Distributed as APKs via GitHub Releases (see `docs/RELEASE.md`). The app also
checks `https://api.github.com/repos/dazoarmando29/MedTech-Review-Hub/releases/latest`
and notifies users about new versions (download link only — never silent installs).
