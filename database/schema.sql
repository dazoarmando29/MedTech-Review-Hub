-- MedTech Review Hub — initial schema (V1)
-- Portable across PostgreSQL (prod) and H2 in PostgreSQL mode (tests).
-- Conventions:
--   * primary keys are VARCHAR(36) UUID strings generated in Kotlin
--   * timestamps are stored as BIGINT epoch milliseconds (UTC); the API exposes ISO-8601 strings
--   * date-only values are VARCHAR(10) ISO dates (YYYY-MM-DD)
--   * structured JSON payloads are stored as TEXT (kotlinx-serialization)
--   * no PG-only types (no jsonb, no arrays) so H2 tests pass unchanged

CREATE TABLE users (
    id            VARCHAR(36)  PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    created_at    BIGINT       NOT NULL
);
CREATE UNIQUE INDEX idx_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    created_at BIGINT       NOT NULL,
    expires_at BIGINT       NOT NULL,
    revoked_at BIGINT       NULL,
    replaced_by VARCHAR(36) NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);

CREATE TABLE profiles (
    user_id               VARCHAR(36)  PRIMARY KEY,
    exam_target           VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM',
    target_exam_date      VARCHAR(10)  NULL,
    knowledge_level       VARCHAR(16)  NOT NULL DEFAULT 'BEGINNER',
    strongest_subjects    TEXT         NOT NULL DEFAULT '[]',
    weakest_subjects      TEXT         NOT NULL DEFAULT '[]',
    minutes_per_day       INTEGER      NOT NULL DEFAULT 60,
    preferred_schedule    TEXT         NOT NULL DEFAULT '{}',
    learning_preferences  TEXT         NOT NULL DEFAULT '[]',
    explanation_style     VARCHAR(32)  NOT NULL DEFAULT 'FORMAL_ENGLISH',
    notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    focus_feature_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    onboarding_completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at            BIGINT       NOT NULL
);

CREATE TABLE study_plans (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    generated_at BIGINT       NOT NULL,
    weights_json TEXT         NOT NULL DEFAULT '{}',
    label        VARCHAR(120) NOT NULL DEFAULT ''
);
CREATE INDEX idx_study_plans_user ON study_plans (user_id);

CREATE TABLE study_plan_items (
    id             VARCHAR(36)  PRIMARY KEY,
    plan_id        VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    day_date       VARCHAR(10)  NOT NULL,
    position       INTEGER      NOT NULL DEFAULT 0,
    item_type      VARCHAR(24)  NOT NULL,
    subject_id     VARCHAR(64)  NULL,
    topic_id       VARCHAR(64)  NULL,
    lesson_id      VARCHAR(128) NULL,
    target_minutes INTEGER      NOT NULL DEFAULT 30,
    completed      BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_study_plan_items_user ON study_plan_items (user_id);
CREATE INDEX idx_study_plan_items_plan ON study_plan_items (plan_id);

CREATE TABLE study_sessions (
    id             VARCHAR(36)  PRIMARY KEY,
    user_id        VARCHAR(36)  NOT NULL,
    started_at     BIGINT       NOT NULL,
    ended_at       BIGINT       NOT NULL,
    session_kind   VARCHAR(24)  NOT NULL,
    subject_id     VARCHAR(64)  NULL,
    minutes        INTEGER      NOT NULL DEFAULT 0,
    items_completed INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX idx_study_sessions_user ON study_sessions (user_id);
CREATE INDEX idx_study_sessions_started ON study_sessions (user_id, started_at);

CREATE TABLE subjects (
    id               VARCHAR(64)  PRIMARY KEY,
    name             VARCHAR(160) NOT NULL,
    short_name       VARCHAR(80)  NOT NULL DEFAULT '',
    description      TEXT         NOT NULL DEFAULT '',
    blueprint_note   TEXT         NOT NULL DEFAULT '',
    color_token      VARCHAR(32)  NOT NULL DEFAULT 'gray',
    editorial_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    needs_verification BOOLEAN    NOT NULL DEFAULT FALSE,
    updated_at       BIGINT       NOT NULL
);

CREATE TABLE topics (
    id               VARCHAR(64)  PRIMARY KEY,
    subject_id       VARCHAR(64)  NOT NULL,
    name             VARCHAR(160) NOT NULL,
    editorial_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    needs_verification BOOLEAN    NOT NULL DEFAULT FALSE,
    updated_at       BIGINT       NOT NULL
);
CREATE INDEX idx_topics_subject ON topics (subject_id);

CREATE TABLE lessons (
    id               VARCHAR(128) PRIMARY KEY,
    subject_id       VARCHAR(64)  NOT NULL,
    topic_id         VARCHAR(64)  NOT NULL,
    title            VARCHAR(240) NOT NULL,
    summary          TEXT         NOT NULL DEFAULT '',
    reading_minutes  INTEGER      NOT NULL DEFAULT 10,
    difficulty       VARCHAR(16)  NOT NULL DEFAULT 'INTERMEDIATE',
    keywords_json    TEXT         NOT NULL DEFAULT '[]',
    content_json     TEXT         NOT NULL DEFAULT '{}',
    editorial_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    needs_verification BOOLEAN    NOT NULL DEFAULT FALSE,
    updated_at       BIGINT       NOT NULL
);
CREATE INDEX idx_lessons_subject ON lessons (subject_id);
CREATE INDEX idx_lessons_topic ON lessons (topic_id);

CREATE TABLE lesson_revisions (
    id              VARCHAR(36)  PRIMARY KEY,
    lesson_id       VARCHAR(128) NOT NULL,
    revision_number INTEGER      NOT NULL DEFAULT 1,
    content_json    TEXT         NOT NULL,
    created_by      VARCHAR(36)  NULL,
    note            VARCHAR(240) NOT NULL DEFAULT '',
    created_at      BIGINT       NOT NULL
);
CREATE INDEX idx_lesson_revisions_lesson ON lesson_revisions (lesson_id);

CREATE TABLE source_references (
    id         VARCHAR(36)  PRIMARY KEY,
    lesson_id  VARCHAR(128) NULL,
    user_id    VARCHAR(36)  NULL,
    title      VARCHAR(240) NOT NULL,
    detail     VARCHAR(240) NOT NULL DEFAULT '',
    url        VARCHAR(500) NULL,
    created_at BIGINT       NOT NULL
);
CREATE INDEX idx_source_references_user ON source_references (user_id);
CREATE INDEX idx_source_references_lesson ON source_references (lesson_id);

CREATE TABLE pdf_documents (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(120) NOT NULL DEFAULT 'application/pdf',
    size_bytes    BIGINT       NOT NULL DEFAULT 0,
    storage_path  VARCHAR(500) NOT NULL,
    uploaded_at   BIGINT       NOT NULL
);
CREATE INDEX idx_pdf_documents_user ON pdf_documents (user_id);

CREATE TABLE pdf_annotations (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    pdf_id        VARCHAR(36)  NOT NULL,
    page_number   INTEGER      NOT NULL DEFAULT 1,
    ann_type      VARCHAR(24)  NOT NULL DEFAULT 'HIGHLIGHT',
    color         VARCHAR(16)  NOT NULL DEFAULT 'YELLOW',
    selected_text TEXT         NOT NULL DEFAULT '',
    note_text     TEXT         NOT NULL DEFAULT '',
    rect_json     TEXT         NOT NULL DEFAULT 'null',
    created_at    BIGINT       NOT NULL
);
CREATE INDEX idx_pdf_annotations_user ON pdf_annotations (user_id);
CREATE INDEX idx_pdf_annotations_pdf ON pdf_annotations (pdf_id);

CREATE TABLE glossary_terms (
    id         VARCHAR(36)  PRIMARY KEY,
    term       VARCHAR(160) NOT NULL,
    definition TEXT         NOT NULL,
    subject_id VARCHAR(64)  NULL,
    source     VARCHAR(240) NOT NULL DEFAULT '',
    created_at BIGINT       NOT NULL
);
CREATE INDEX idx_glossary_terms_subject ON glossary_terms (subject_id);

CREATE TABLE flashcard_decks (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    subject_id VARCHAR(64)  NULL,
    created_at BIGINT       NOT NULL,
    updated_at BIGINT       NOT NULL
);
CREATE INDEX idx_flashcard_decks_user ON flashcard_decks (user_id);

CREATE TABLE flashcards (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    deck_id      VARCHAR(36)  NULL,
    card_front   TEXT         NOT NULL,
    card_back    TEXT         NOT NULL,
    card_type    VARCHAR(24)  NOT NULL DEFAULT 'BASIC',
    tags_json    TEXT         NOT NULL DEFAULT '[]',
    subject_id   VARCHAR(64)  NULL,
    topic_id     VARCHAR(64)  NULL,
    difficulty   INTEGER      NOT NULL DEFAULT 1,
    note_text    TEXT         NOT NULL DEFAULT '',
    favorite     BOOLEAN      NOT NULL DEFAULT FALSE,
    image_ref    VARCHAR(500) NULL,
    source_json  TEXT         NOT NULL DEFAULT '{}',
    sched_state  VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    stability    DOUBLE PRECISION NOT NULL DEFAULT 0,
    difficulty_fsrs DOUBLE PRECISION NOT NULL DEFAULT 0,
    due_at       BIGINT       NOT NULL,
    last_review_at BIGINT     NULL,
    reps         INTEGER      NOT NULL DEFAULT 0,
    lapses       INTEGER      NOT NULL DEFAULT 0,
    learning_step INTEGER     NOT NULL DEFAULT 0,
    history_json TEXT         NOT NULL DEFAULT '[]',
    created_at   BIGINT       NOT NULL,
    updated_at   BIGINT       NOT NULL
);
CREATE INDEX idx_flashcards_user ON flashcards (user_id);
CREATE INDEX idx_flashcards_deck ON flashcards (deck_id);
CREATE INDEX idx_flashcards_due ON flashcards (user_id, due_at);

CREATE TABLE flashcard_reviews (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    flashcard_id  VARCHAR(36)  NOT NULL,
    rating        VARCHAR(16)  NOT NULL,
    interval_days DOUBLE PRECISION NOT NULL DEFAULT 0,
    reviewed_at   BIGINT       NOT NULL
);
CREATE INDEX idx_flashcard_reviews_user ON flashcard_reviews (user_id);
CREATE INDEX idx_flashcard_reviews_card ON flashcard_reviews (flashcard_id);

CREATE TABLE quiz_questions (
    id               VARCHAR(128) PRIMARY KEY,
    user_id          VARCHAR(36)  NULL,
    subject_id       VARCHAR(64)  NOT NULL,
    topic_id         VARCHAR(64)  NOT NULL,
    difficulty       VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    content_json     TEXT         NOT NULL,
    editorial_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    needs_verification BOOLEAN    NOT NULL DEFAULT FALSE,
    updated_at       BIGINT       NOT NULL
);
CREATE INDEX idx_quiz_questions_user ON quiz_questions (user_id);
CREATE INDEX idx_quiz_questions_subject ON quiz_questions (subject_id);

CREATE TABLE quiz_attempts (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    quiz_mode    VARCHAR(16)  NOT NULL DEFAULT 'STUDY',
    status       VARCHAR(16)  NOT NULL DEFAULT 'IN_PROGRESS',
    question_ids TEXT         NOT NULL DEFAULT '[]',
    started_at   BIGINT       NOT NULL,
    finished_at  BIGINT       NULL,
    score        DOUBLE PRECISION NULL,
    correct_count INTEGER     NOT NULL DEFAULT 0,
    total_count  INTEGER      NOT NULL DEFAULT 0,
    result_json  TEXT         NULL
);
CREATE INDEX idx_quiz_attempts_user ON quiz_attempts (user_id);

CREATE TABLE quiz_answers (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    attempt_id  VARCHAR(36)  NOT NULL,
    question_id VARCHAR(128) NOT NULL,
    choice_id   VARCHAR(16)  NOT NULL,
    is_correct  BOOLEAN      NOT NULL DEFAULT FALSE,
    answered_at BIGINT       NOT NULL
);
CREATE INDEX idx_quiz_answers_user ON quiz_answers (user_id);
CREATE INDEX idx_quiz_answers_attempt ON quiz_answers (attempt_id);

CREATE TABLE learning_analytics (
    id                 VARCHAR(36)  PRIMARY KEY,
    user_id            VARCHAR(36)  NOT NULL,
    subject_id         VARCHAR(64)  NOT NULL,
    topic_id           VARCHAR(64)  NOT NULL,
    mastery            DOUBLE PRECISION NOT NULL DEFAULT 0,
    questions_answered INTEGER      NOT NULL DEFAULT 0,
    questions_correct  INTEGER      NOT NULL DEFAULT 0,
    updated_at         BIGINT       NOT NULL
);
CREATE INDEX idx_learning_analytics_user ON learning_analytics (user_id);
CREATE UNIQUE INDEX idx_learning_analytics_user_topic ON learning_analytics (user_id, subject_id, topic_id);

CREATE TABLE habits (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    name          VARCHAR(160) NOT NULL,
    target_per_day DOUBLE PRECISION NOT NULL DEFAULT 1,
    habit_kind    VARCHAR(16)  NOT NULL DEFAULT 'COUNT',
    created_at    BIGINT       NOT NULL
);
CREATE INDEX idx_habits_user ON habits (user_id);

CREATE TABLE habit_logs (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    habit_id   VARCHAR(36)  NOT NULL,
    log_date   VARCHAR(10)  NOT NULL,
    amount     DOUBLE PRECISION NOT NULL DEFAULT 1,
    created_at BIGINT       NOT NULL
);
CREATE INDEX idx_habit_logs_user ON habit_logs (user_id);
CREATE INDEX idx_habit_logs_habit ON habit_logs (habit_id);
CREATE UNIQUE INDEX idx_habit_logs_unique ON habit_logs (habit_id, log_date);

CREATE TABLE diary_entries (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    entry_date  VARCHAR(10)  NOT NULL,
    mood        VARCHAR(32)  NOT NULL DEFAULT 'NEUTRAL',
    entry_text  TEXT         NOT NULL DEFAULT '',
    tags_json   TEXT         NOT NULL DEFAULT '[]',
    created_at  BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL
);
CREATE INDEX idx_diary_entries_user ON diary_entries (user_id);
CREATE INDEX idx_diary_entries_date ON diary_entries (user_id, entry_date);

CREATE TABLE mascot_preferences (
    user_id       VARCHAR(36)  PRIMARY KEY,
    avatar_mascot VARCHAR(16)  NOT NULL DEFAULT 'cat',
    locale        VARCHAR(16)  NOT NULL DEFAULT 'en',
    theme         VARCHAR(32)  NOT NULL DEFAULT 'system',
    serious_mode  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at    BIGINT       NOT NULL
);

CREATE TABLE focus_sessions (
    id         VARCHAR(36)  PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    started_at BIGINT       NOT NULL,
    ended_at   BIGINT       NOT NULL,
    minutes    INTEGER      NOT NULL DEFAULT 0,
    tag        VARCHAR(64)  NOT NULL DEFAULT ''
);
CREATE INDEX idx_focus_sessions_user ON focus_sessions (user_id);

CREATE TABLE notification_preferences (
    user_id            VARCHAR(36)  PRIMARY KEY,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    daily_reminder     BOOLEAN      NOT NULL DEFAULT TRUE,
    reminder_time      VARCHAR(5)   NOT NULL DEFAULT '20:00',
    streak_alerts      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at         BIGINT       NOT NULL
);

CREATE TABLE generated_ai_content (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    task         VARCHAR(32)  NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,
    response_json TEXT        NOT NULL,
    input_tokens INTEGER      NOT NULL DEFAULT 0,
    output_tokens INTEGER     NOT NULL DEFAULT 0,
    created_at   BIGINT       NOT NULL
);
CREATE INDEX idx_generated_ai_content_user ON generated_ai_content (user_id);
CREATE INDEX idx_generated_ai_content_hash ON generated_ai_content (user_id, request_hash);

CREATE TABLE content_reports (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL,
    question_id VARCHAR(128) NOT NULL,
    reason      VARCHAR(64)  NOT NULL DEFAULT 'OTHER',
    details     TEXT         NOT NULL DEFAULT '',
    report_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at  BIGINT       NOT NULL
);
CREATE INDEX idx_content_reports_user ON content_reports (user_id);

CREATE TABLE audit_log (
    id            VARCHAR(36)  PRIMARY KEY,
    actor_user_id VARCHAR(36)  NULL,
    action        VARCHAR(80)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(128) NOT NULL DEFAULT '',
    detail_json   TEXT         NOT NULL DEFAULT '{}',
    created_at    BIGINT       NOT NULL
);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_user_id);
CREATE INDEX idx_audit_log_created ON audit_log (created_at);
