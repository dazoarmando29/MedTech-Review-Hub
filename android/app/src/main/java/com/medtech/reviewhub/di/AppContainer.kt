package com.medtech.reviewhub.di

import android.content.Context
import com.medtech.reviewhub.data.content.ContentAssetsLoader
import com.medtech.reviewhub.data.db.AppDatabase
import com.medtech.reviewhub.data.music.AudioEngine
import com.medtech.reviewhub.data.music.ChimePlayer
import com.medtech.reviewhub.data.music.MusicCatalogLoader
import com.medtech.reviewhub.data.music.MusicLibrary
import com.medtech.reviewhub.data.music.MusicRepository
import com.medtech.reviewhub.data.prefs.SettingsRepository
import com.medtech.reviewhub.data.repo.AuthRepository
import com.medtech.reviewhub.data.repo.FlashcardRepository
import com.medtech.reviewhub.data.repo.FocusRepository
import com.medtech.reviewhub.data.repo.HabitRepository
import com.medtech.reviewhub.data.repo.LoveNoteRepository
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

    val loveNoteRepository: LoveNoteRepository by lazy { LoveNoteRepository(database.loveNoteDao()) }

    val musicLibrary: MusicLibrary by lazy { MusicCatalogLoader.load(appContext.assets) }

    val audioEngine: AudioEngine by lazy { AudioEngine(appContext) }

    val musicRepository: MusicRepository by lazy { MusicRepository(appContext) }

    val chimePlayer: ChimePlayer by lazy { ChimePlayer() }
}
