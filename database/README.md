# database/ — schema reference

This directory holds a **reference copy** of the database schema:

- `schema.sql` — snapshot of the current schema (identical to the Flyway migration below).

## Migrations live in the backend

The authoritative, versioned migrations are **not** in this directory. They are Flyway
migrations shipped inside the backend artifact:

```
backend/src/main/resources/db/migration/
└── V1__init.sql          # initial schema (all tables)
```

The backend runs `Flyway.migrate()` on startup (see
`backend/src/main/kotlin/io/medtech/backend/db/Database.kt`), so a fresh deployment only
needs an empty database and the correct `DATABASE_URL` / `DATABASE_USER` /
`DATABASE_PASSWORD` environment variables.

To evolve the schema: add `V2__<description>.sql`, `V3__<description>.sql`, … to
`backend/src/main/resources/db/migration/`, then copy the resulting final schema back to
`schema.sql` here so this directory stays in sync.

The `migrations/` subdirectory is legacy/empty and is kept only so existing links do not
break.

## Portability rules (please keep)

The schema is written to run unchanged on **PostgreSQL (production)** and **H2 in
PostgreSQL compatibility mode (tests)**:

- primary keys are `VARCHAR(36)` UUID strings generated in Kotlin (`java.util.UUID`),
- timestamps are `BIGINT` epoch milliseconds (UTC) — the JSON API exposes ISO-8601 strings,
- date-only values are `VARCHAR(10)` ISO dates (`YYYY-MM-DD`),
- structured JSON payloads are stored as `TEXT` (kotlinx-serialization),
- **no PostgreSQL-only types** (`jsonb`, arrays, `SERIAL`, partial indexes, …).

All per-user ("private") tables carry a `user_id` column with an index; account deletion
removes every row referencing the user (enforced by
`AccountDeletionRepository.deleteAllForUser` and covered by tests).
