package com.medtech.reviewhub.di

import android.content.Context
import com.medtech.reviewhub.data.content.ContentAssetsLoader
import com.medtech.reviewhub.data.db.AppDatabase
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.data.repo.AuthRepository
import com.medtech.reviewhub.data.repo.FlashcardRepository
import com.medtech.reviewhub.data.repo.FocusRepository
import com.medtech.reviewhub.data.repo.HabitRepository
import com.medtech.reviewhub.data.repo.PdfRepository
import com.medtech.reviewhub.data.repo.QuizRepository
import com.medtech.reviewhub.data.repo.StudyRepository

/**
 * Manual DI / service locator (no Hilt). A single [AppContainer] is created in
 * [com.medtech.reviewhub.ReviewHubApp] and handed to ViewModels through the
 * Activity's defaultViewModelProviderFactory override.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val content: ContentAssetsLoader by lazy { ContentAssetsLoader(appContext.assets) }

    val flashcardRepository: FlashcardRepository by lazy { FlashcardRepository(database.flashcardDao()) }

    val studyRepository: StudyRepository by lazy { StudyRepository(database.studyDao(), settings) }

    val quizRepository: QuizRepository by lazy { QuizRepository(database.quizDao(), flashcardRepository) }

    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao()) }

    val pdfRepository: PdfRepository by lazy { PdfRepository(appContext, database.pdfDao()) }

    val focusRepository: FocusRepository by lazy { FocusRepository(appContext, database.focusDao()) }

    val authRepository: AuthRepository by lazy { AuthRepository(appContext, settings) }
}
