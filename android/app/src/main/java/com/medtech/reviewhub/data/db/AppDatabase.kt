package com.medtech.reviewhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FlashcardEntity::class,
        ReviewLogEntity::class,
        StudySessionEntity::class,
        QuizAttemptEntity::class,
        PlanEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        DiaryEntity::class,
        PdfEntity::class,
        PdfAnnotationEntity::class,
        ReadingProgressEntity::class,
        FocusSessionEntity::class,
        BlockedAppEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flashcardDao(): FlashcardDao
    abstract fun studyDao(): StudyDao
    abstract fun quizDao(): QuizDao
    abstract fun habitDao(): HabitDao
    abstract fun pdfDao(): PdfDao
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 -> v2 (Design System v2 highlight colors, shared/DESIGN.md
         * "Migration from v1.0.0 stored colors - no data loss"): rewrite legacy
         * annotation color names in place. RED -> PINK (high-priority fact ->
         * must memorize), PURPLE -> ROSE (needs study -> needs review). YELLOW,
         * BLUE and GREEN stay unchanged. Runs exactly once, so annotations
         * created afterwards (including the new PURPLE "Definition") are never
         * remapped.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE pdf_annotations SET color = 'PINK' WHERE color = 'RED'")
                db.execSQL("UPDATE pdf_annotations SET color = 'ROSE' WHERE color = 'PURPLE'")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtech-review-hub.db"
                ).addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
