-- MedTech Review Hub — Cloudflare D1 schema (SQLite).
-- Mirrors the API.md data model (see backend/src/main/resources/db/migration for
-- the PostgreSQL variant). JSON columns hold kotlinx/TS-serialized documents.

CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name  TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'USER',
  created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS profiles (
  user_id    TEXT PRIMARY KEY,
  json       TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plans (
  user_id    TEXT PRIMARY KEY,
  json       TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS study_sessions (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  started_at TEXT NOT NULL,
  kind       TEXT NOT NULL,
  subject_id TEXT,
  minutes    INTEGER NOT NULL DEFAULT 0,
  items      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON study_sessions(user_id, started_at);

CREATE TABLE IF NOT EXISTS flashcards (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  deck_id    TEXT,
  json       TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cards_user ON flashcards(user_id);

CREATE TABLE IF NOT EXISTS quiz_attempts (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  json       TEXT NOT NULL,
  finished   INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_attempts_user ON quiz_attempts(user_id);

CREATE TABLE IF NOT EXISTS habits (
  id      TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  json    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS habit_logs (
  user_id  TEXT NOT NULL,
  habit_id TEXT NOT NULL,
  date     TEXT NOT NULL,
  amount   REAL NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, habit_id, date)
);

CREATE TABLE IF NOT EXISTS diary (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  date       TEXT NOT NULL,
  mood       TEXT,
  text       TEXT NOT NULL,
  tags       TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_diary_user ON diary(user_id, date);

CREATE TABLE IF NOT EXISTS pdf_docs (
  id         TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL,
  name       TEXT NOT NULL,
  subject_id TEXT,
  page_count INTEGER NOT NULL DEFAULT 0,
  last_page  INTEGER NOT NULL DEFAULT 1,
  size_bytes INTEGER NOT NULL DEFAULT 0,
  added_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pdf_annotations (
  id      TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  pdf_id  TEXT NOT NULL,
  json    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ann_user ON pdf_annotations(user_id, pdf_id);

CREATE TABLE IF NOT EXISTS reports (
  id         TEXT PRIMARY KEY,
  user_id    TEXT,
  json       TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS content (
  path       TEXT PRIMARY KEY,
  kind       TEXT NOT NULL,
  id         TEXT,
  subject_id TEXT,
  json       TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_content_kind ON content(kind, subject_id);

CREATE TABLE IF NOT EXISTS ai_usage (
  user_id TEXT NOT NULL,
  day     TEXT NOT NULL,
  tokens  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);
