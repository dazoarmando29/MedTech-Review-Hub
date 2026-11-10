package com.medtech.reviewhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medtech-review-hub.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
