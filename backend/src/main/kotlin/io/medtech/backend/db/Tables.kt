package io.medtech.backend.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 320)
    val passwordHash = varchar("password_hash", 100)
    val displayName = varchar("display_name", 120)
    val role = varchar("role", 16).default("USER")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val tokenHash = varchar("token_hash", 64)
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()
    val replacedBy = varchar("replaced_by", 36).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Profiles : Table("profiles") {
    val userId = varchar("user_id", 36)
    val examTarget = varchar("exam_target", 32).default("CUSTOM")
    val targetExamDate = varchar("target_exam_date", 10).nullable()
    val knowledgeLevel = varchar("knowledge_level", 16).default("BEGINNER")
    val strongestSubjects = text("strongest_subjects").default("[]")
    val weakestSubjects = text("weakest_subjects").default("[]")
    val minutesPerDay = integer("minutes_per_day").default(60)
    val preferredSchedule = text("preferred_schedule").default("{}")
    val learningPreferences = text("learning_preferences").default("[]")
    val explanationStyle = varchar("explanation_style", 32).default("FORMAL_ENGLISH")
    val notificationsEnabled = bool("notifications_enabled").default(true)
    val focusFeatureEnabled = bool("focus_feature_enabled").default(true)
    val onboardingCompleted = bool("onboarding_completed").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object StudyPlans : Table("study_plans") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val generatedAt = long("generated_at")
    val weightsJson = text("weights_json").default("{}")
    val label = varchar("label", 120).default("")
    override val primaryKey = PrimaryKey(id)
}

object StudyPlanItems : Table("study_plan_items") {
    val id = varchar("id", 36)
    val planId = varchar("plan_id", 36)
    val userId = varchar("user_id", 36)
    val dayDate = varchar("day_date", 10)
    val position = integer("position").default(0)
    val itemType = varchar("item_type", 24)
    val subjectId = varchar("subject_id", 64).nullable()
    val topicId = varchar("topic_id", 64).nullable()
    val lessonId = varchar("lesson_id", 128).nullable()
    val targetMinutes = integer("target_minutes").default(30)
    val completed = bool("completed").default(false)
    override val primaryKey = PrimaryKey(id)
}

object StudySessions : Table("study_sessions") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val startedAt = long("started_at")
    val endedAt = long("ended_at")
    val sessionKind = varchar("session_kind", 24)
    val subjectId = varchar("subject_id", 64).nullable()
    val minutes = integer("minutes").default(0)
    val itemsCompleted = integer("items_completed").default(0)
    override val primaryKey = PrimaryKey(id)
}

object Subjects : Table("subjects") {
    val id = varchar("id", 64)
    val name = varchar("name", 160)
    val shortName = varchar("short_name", 80).default("")
    val description = text("description").default("")
    val blueprintNote = text("blueprint_note").default("")
    val colorToken = varchar("color_token", 32).default("gray")
    val editorialStatus = varchar("editorial_status", 16).default("DRAFT")
    val needsVerification = bool("needs_verification").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Topics : Table("topics") {
    val id = varchar("id", 64)
    val subjectId = varchar("subject_id", 64)
    val name = varchar("name", 160)
    val editorialStatus = varchar("editorial_status", 16).default("DRAFT")
    val needsVerification = bool("needs_verification").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Lessons : Table("lessons") {
    val id = varchar("id", 128)
    val subjectId = varchar("subject_id", 64)
    val topicId = varchar("topic_id", 64)
    val title = varchar("title", 240)
    val summary = text("summary").default("")
    val readingMinutes = integer("reading_minutes").default(10)
    val difficulty = varchar("difficulty", 16).default("INTERMEDIATE")
    val keywordsJson = text("keywords_json").default("[]")
    val contentJson = text("content_json").default("{}")
    val editorialStatus = varchar("editorial_status", 16).default("DRAFT")
    val needsVerification = bool("needs_verification").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object LessonRevisions : Table("lesson_revisions") {
    val id = varchar("id", 36)
    val lessonId = varchar("lesson_id", 128)
    val revisionNumber = integer("revision_number").default(1)
    val contentJson = text("content_json")
    val createdBy = varchar("created_by", 36).nullable()
    val note = varchar("note", 240).default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SourceReferences : Table("source_references") {
    val id = varchar("id", 36)
    val lessonId = varchar("lesson_id", 128).nullable()
    val userId = varchar("user_id", 36).nullable()
    val title = varchar("title", 240)
    val detail = varchar("detail", 240).default("")
    val url = varchar("url", 500).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PdfDocuments : Table("pdf_documents") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val filename = varchar("filename", 255)
    val contentType = varchar("content_type", 120).default("application/pdf")
    val sizeBytes = long("size_bytes").default(0)
    val storagePath = varchar("storage_path", 500)
    val uploadedAt = long("uploaded_at")
    override val primaryKey = PrimaryKey(id)
}

object PdfAnnotations : Table("pdf_annotations") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val pdfId = varchar("pdf_id", 36)
    val pageNumber = integer("page_number").default(1)
    val annType = varchar("ann_type", 24).default("HIGHLIGHT")
    val color = varchar("color", 16).default("YELLOW")
    val selectedText = text("selected_text").default("")
    val noteText = text("note_text").default("")
    val rectJson = text("rect_json").default("null")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object GlossaryTerms : Table("glossary_terms") {
    val id = varchar("id", 36)
    val term = varchar("term", 160)
    val definition = text("definition")
    val subjectId = varchar("subject_id", 64).nullable()
    val sourceLabel = varchar("source", 240).default("")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object FlashcardDecks : Table("flashcard_decks") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val title = varchar("title", 200)
    val subjectId = varchar("subject_id", 64).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Flashcards : Table("flashcards") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val deckId = varchar("deck_id", 36).nullable()
    val cardFront = text("card_front")
    val cardBack = text("card_back")
    val cardType = varchar("card_type", 24).default("BASIC")
    val tagsJson = text("tags_json").default("[]")
    val subjectId = varchar("subject_id", 64).nullable()
    val topicId = varchar("topic_id", 64).nullable()
    val difficulty = integer("difficulty").default(1)
    val noteText = text("note_text").default("")
    val favorite = bool("favorite").default(false)
    val imageRef = varchar("image_ref", 500).nullable()
    val sourceJson = text("source_json").default("{}")
    val schedState = varchar("sched_state", 16).default("NEW")
    val stability = double("stability").default(0.0)
    val difficultyFsrs = double("difficulty_fsrs").default(0.0)
    val dueAt = long("due_at")
    val lastReviewAt = long("last_review_at").nullable()
    val reps = integer("reps").default(0)
    val lapses = integer("lapses").default(0)
    val learningStep = integer("learning_step").default(0)
    val historyJson = text("history_json").default("[]")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object FlashcardReviews : Table("flashcard_reviews") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val flashcardId = varchar("flashcard_id", 36)
    val rating = varchar("rating", 16)
    val intervalDays = double("interval_days").default(0.0)
    val reviewedAt = long("reviewed_at")
    override val primaryKey = PrimaryKey(id)
}

object QuizQuestions : Table("quiz_questions") {
    val id = varchar("id", 128)
    val userId = varchar("user_id", 36).nullable()
    val subjectId = varchar("subject_id", 64)
    val topicId = varchar("topic_id", 64)
    val difficulty = varchar("difficulty", 16).default("MEDIUM")
    val contentJson = text("content_json")
    val editorialStatus = varchar("editorial_status", 16).default("DRAFT")
    val needsVerification = bool("needs_verification").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object QuizAttempts : Table("quiz_attempts") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val quizMode = varchar("quiz_mode", 16).default("STUDY")
    val status = varchar("status", 16).default("IN_PROGRESS")
    val questionIds = text("question_ids").default("[]")
    val startedAt = long("started_at")
    val finishedAt = long("finished_at").nullable()
    val score = double("score").nullable()
    val correctCount = integer("correct_count").default(0)
    val totalCount = integer("total_count").default(0)
    val resultJson = text("result_json").nullable()
    override val primaryKey = PrimaryKey(id)
}

object QuizAnswers : Table("quiz_answers") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val attemptId = varchar("attempt_id", 36)
    val questionId = varchar("question_id", 128)
    val choiceId = varchar("choice_id", 16)
    val isCorrect = bool("is_correct").default(false)
    val answeredAt = long("answered_at")
    override val primaryKey = PrimaryKey(id)
}

object LearningAnalytics : Table("learning_analytics") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val subjectId = varchar("subject_id", 64)
    val topicId = varchar("topic_id", 64)
    val mastery = double("mastery").default(0.0)
    val questionsAnswered = integer("questions_answered").default(0)
    val questionsCorrect = integer("questions_correct").default(0)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Habits : Table("habits") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val name = varchar("name", 160)
    val targetPerDay = double("target_per_day").default(1.0)
    val habitKind = varchar("habit_kind", 16).default("COUNT")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object HabitLogs : Table("habit_logs") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val habitId = varchar("habit_id", 36)
    val logDate = varchar("log_date", 10)
    val amount = double("amount").default(1.0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object DiaryEntries : Table("diary_entries") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val entryDate = varchar("entry_date", 10)
    val mood = varchar("mood", 32).default("NEUTRAL")
    val entryText = text("entry_text").default("")
    val tagsJson = text("tags_json").default("[]")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object MascotPreferences : Table("mascot_preferences") {
    val userId = varchar("user_id", 36)
    val avatarMascot = varchar("avatar_mascot", 16).default("cat")
    val locale = varchar("locale", 16).default("en")
    val theme = varchar("theme", 32).default("system")
    val seriousMode = bool("serious_mode").default(false)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object FocusSessions : Table("focus_sessions") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val startedAt = long("started_at")
    val endedAt = long("ended_at")
    val minutes = integer("minutes").default(0)
    val tag = varchar("tag", 64).default("")
    override val primaryKey = PrimaryKey(id)
}

object NotificationPreferences : Table("notification_preferences") {
    val userId = varchar("user_id", 36)
    val enabled = bool("enabled").default(true)
    val dailyReminder = bool("daily_reminder").default(true)
    val reminderTime = varchar("reminder_time", 5).default("20:00")
    val streakAlerts = bool("streak_alerts").default(true)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object GeneratedAiContent : Table("generated_ai_content") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val task = varchar("task", 32)
    val requestHash = varchar("request_hash", 64)
    val responseJson = text("response_json")
    val inputTokens = integer("input_tokens").default(0)
    val outputTokens = integer("output_tokens").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ContentReports : Table("content_reports") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36)
    val questionId = varchar("question_id", 128)
    val reason = varchar("reason", 64).default("OTHER")
    val details = text("details").default("")
    val reportStatus = varchar("report_status", 16).default("OPEN")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AuditLog : Table("audit_log") {
    val id = varchar("id", 36)
    val actorUserId = varchar("actor_user_id", 36).nullable()
    val action = varchar("action", 80)
    val entityType = varchar("entity_type", 64)
    val entityId = varchar("entity_id", 128).default("")
    val detailJson = text("detail_json").default("{}")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
