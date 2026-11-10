package com.medtech.reviewhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medtech.reviewhub.data.network.NetworkModule
import com.medtech.reviewhub.data.network.dto.AiRequest
import com.medtech.reviewhub.data.prefs.ThemeMode
import com.medtech.reviewhub.di.AppContainer
import com.medtech.reviewhub.domain.MascotKind
import com.medtech.reviewhub.domain.model.QuizQuestion
import com.medtech.reviewhub.ui.focus.FocusAppsViewModel
import com.medtech.reviewhub.ui.focus.FocusScreen
import com.medtech.reviewhub.ui.focus.FocusTimerViewModel
import com.medtech.reviewhub.ui.flashcards.FlashcardsScreen
import com.medtech.reviewhub.ui.flashcards.FlashcardsViewModel
import com.medtech.reviewhub.ui.habits.HabitsScreen
import com.medtech.reviewhub.ui.habits.HabitsViewModel
import com.medtech.reviewhub.ui.home.HomeScreen
import com.medtech.reviewhub.ui.home.HomeViewModel
import com.medtech.reviewhub.ui.library.LessonScreen
import com.medtech.reviewhub.ui.library.LibraryScreen
import com.medtech.reviewhub.ui.library.LibraryViewModel
import com.medtech.reviewhub.ui.onboarding.OnboardingScreen
import com.medtech.reviewhub.ui.onboarding.OnboardingViewModel
import com.medtech.reviewhub.ui.pdf.PdfLibraryScreen
import com.medtech.reviewhub.ui.pdf.PdfReaderScreen
import com.medtech.reviewhub.ui.pdf.PdfViewModel
import com.medtech.reviewhub.ui.quiz.QuizPersistence
import com.medtech.reviewhub.ui.quiz.QuizResultScreen
import com.medtech.reviewhub.ui.quiz.QuizRunScreen
import com.medtech.reviewhub.ui.quiz.QuizSetupScreen
import com.medtech.reviewhub.ui.quiz.QuizViewModel
import com.medtech.reviewhub.ui.resources.ResourcesScreen
import com.medtech.reviewhub.ui.settings.AboutScreen
import com.medtech.reviewhub.ui.settings.SettingsScreen
import com.medtech.reviewhub.ui.settings.SettingsViewModel
import com.medtech.reviewhub.ui.theme.AppTheme
import com.medtech.reviewhub.ui.theme.MedTechTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ReviewHubApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val appSettings = settings
            // Design System v2: the persisted appTheme setting selects one of
            // the five themes; "system" keeps the legacy ThemeMode behaviour
            // (follow system / light / dark) with the new palette.
            val appTheme = AppTheme.fromId(appSettings?.appTheme)
            val dark = when (appTheme) {
                AppTheme.NIGHT -> true
                AppTheme.SYSTEM -> when (appSettings?.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    else -> isSystemInDarkTheme()
                }
                else -> false
            }
            MedTechTheme(appTheme = appTheme, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density.density,
                        density.fontScale * ((appSettings?.textScalePercent ?: 100) / 100f)
                    )
                ) {
                    ReviewHubNavGraph(container)
                }
            }
        }
    }
}

/** Manual-DI ViewModel factory backed by the [AppContainer]. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            container.content, container.studyRepository, container.flashcardRepository,
            container.quizRepository, container.habitRepository, container.settings
        )
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(container.content, container.studyRepository)
        modelClass.isAssignableFrom(FlashcardsViewModel::class.java) ->
            FlashcardsViewModel(container.flashcardRepository, container.settings)
        modelClass.isAssignableFrom(HabitsViewModel::class.java) ->
            HabitsViewModel(container.habitRepository)
        modelClass.isAssignableFrom(PdfViewModel::class.java) ->
            PdfViewModel(container.pdfRepository)
        modelClass.isAssignableFrom(FocusAppsViewModel::class.java) ->
            FocusAppsViewModel(container.appContext, container.focusRepository, container.settings)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            container.settings, container.authRepository,
            container.flashcardRepository, container.studyRepository
        )
        else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    } as T
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val LESSON = "lesson/{lessonId}"
    const val FLASHCARDS = "flashcards"
    const val QUIZ_SETUP = "quiz"
    const val QUIZ_RUN = "quiz/run"
    const val QUIZ_RESULT = "quiz/result"
    const val PDFS = "pdfs"
    const val PDF_READER = "pdf/{pdfId}"
    const val HABITS = "habits"
    const val FOCUS = "focus"
    const val RESOURCES = "resources"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun lesson(id: String) = "lesson/$id"
    fun pdfReader(id: String) = "pdf/$id"
}

private val bottomBarRoutes = setOf(
    Routes.HOME, Routes.LIBRARY, Routes.FLASHCARDS, Routes.QUIZ_SETUP,
    Routes.HABITS, Routes.FOCUS, Routes.RESOURCES, Routes.SETTINGS
)

@Composable
fun ReviewHubNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val factory = remember(container) { AppViewModelFactory(container) }

    val profile by container.settings.profile.collectAsState(initial = null)
    val settingsState by container.settings.settings.collectAsState(initial = null)

    // Quiz session state shared across quiz destinations.
    var quizVm by remember { mutableStateOf<QuizViewModel?>(null) }
    var questionBank by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var quizSubjects by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var quizTopics by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var subjectNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        container.content.load()
        questionBank = container.content.allQuestions()
        val subjects = container.content.subjects()
        quizSubjects = subjects.map { it.id to it.shortName }
        quizTopics = subjects.flatMap { s -> s.topics.map { t -> t.id to t.name } }
        subjectNames = subjects.associate { it.id to it.shortName }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    listOf(
                        Triple(Routes.HOME, "Home", "Home"),
                        Triple(Routes.LIBRARY, "Library", "Library"),
                        Triple(Routes.FLASHCARDS, "Cards", "Cards"),
                        Triple(Routes.QUIZ_SETUP, "Quiz", "Quiz"),
                        Triple(Routes.HABITS, "Habits", "Habits"),
                        Triple(Routes.FOCUS, "Focus", "Focus"),
                        Triple(Routes.RESOURCES, "Resources", "Resources"),
                        Triple(Routes.SETTINGS, "Settings", "Settings")
                    ).forEach { (route, label, iconLabel) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = { Text(label) },
                            icon = { Text(iconLabel.take(1)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {

                composable(Routes.ONBOARDING) {
                    val vm = remember {
                        OnboardingViewModel(
                            topicProvider = {
                                container.content.load()
                                container.content.subjects().associate { s -> s.id to s.topics }
                            },
                            onFinish = { p, plan ->
                                container.settings.saveProfile(p)
                                container.studyRepository.savePlan(plan)
                            }
                        )
                    }
                    OnboardingScreen(
                        subjectNames = subjectNames,
                        onDone = {
                            navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                        },
                        vm = vm
                    )
                }

                composable(Routes.HOME) {
                    val vm: HomeViewModel = viewModel(factory = factory)
                    HomeScreen(
                        vm = vm,
                        onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                        onOpenFlashcards = { navController.navigate(Routes.FLASHCARDS) },
                        onOpenQuiz = { navController.navigate(Routes.QUIZ_SETUP) },
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) }
                    )
                }

                composable(Routes.LIBRARY) {
                    val vm: LibraryViewModel = viewModel(factory = factory)
                    LibraryScreen(vm = vm, onOpenLesson = { navController.navigate(Routes.lesson(it)) })
                }

                composable(
                    Routes.LESSON,
                    arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
                ) { entry ->
                    val lessonId = entry.arguments?.getString("lessonId") ?: return@composable
                    val vm: LibraryViewModel = viewModel(factory = factory)
                    LaunchedEffect(lessonId) { vm.openLesson(lessonId) }
                    LessonScreen(
                        vm = vm,
                        apiBaseUrl = settingsState?.apiBaseUrl ?: "",
                        bearerToken = settingsState?.accessToken,
                        seriousMode = settingsState?.seriousMode ?: false,
                        onBack = { navController.popBackStack() },
                        onPracticeQuestions = { navController.navigate(Routes.QUIZ_SETUP) },
                        onOpenFlashcards = { navController.navigate(Routes.FLASHCARDS) }
                    )
                }

                composable(Routes.FLASHCARDS) {
                    val vm: FlashcardsViewModel = viewModel(factory = factory)
                    FlashcardsScreen(vm = vm)
                }

                composable(Routes.QUIZ_SETUP) {
                    QuizSetupScreen(
                        subjects = quizSubjects,
                        topics = quizTopics,
                        onBack = { navController.popBackStack() },
                        onStart = { mode, style, subjectId, topicId ->
                            val persistence = object : QuizPersistence {
                                override suspend fun onQuizStart(questionIds: List<String>, modeName: String) {
                                    container.quizRepository.startAttempt(modeName, questionIds)
                                }

                                override suspend fun onQuizFinish(
                                    result: com.medtech.reviewhub.domain.model.QuizResult,
                                    answers: Map<String, String>,
                                    questions: List<QuizQuestion>,
                                    timeSeconds: Int
                                ) {
                                    val attempt = container.quizRepository.unfinishedAttempt()
                                    if (attempt != null) {
                                        container.quizRepository.finishAttempt(
                                            attempt.id, answers, questions, timeSeconds, result
                                        )
                                    }
                                    container.authRepository.requestSync()
                                }

                                override suspend fun onMissed(
                                    questions: List<QuizQuestion>,
                                    answers: Map<String, String>
                                ): Int = container.quizRepository.generateCardsForMissed(questions, answers)
                            }
                            val vm = QuizViewModel(questionBank, persistence)
                            val incorrect = kotlinx.coroutines.runBlocking {
                                container.quizRepository.previouslyIncorrectIds()
                            }
                            val picked = QuizViewModel.select(
                                bank = questionBank,
                                mode = mode,
                                subjectId = subjectId,
                                topicId = topicId,
                                incorrectIds = incorrect,
                                limit = if (mode == com.medtech.reviewhub.ui.quiz.QuizMode.MOCK_EXAM) 100 else 20
                            ).ifEmpty { questionBank.take(10) }
                            kotlinx.coroutines.runBlocking { vm.start(picked, mode, style) }
                            quizVm = vm
                            navController.navigate(Routes.QUIZ_RUN)
                        }
                    )
                }

                composable(Routes.QUIZ_RUN) {
                    val vm = quizVm
                    if (vm != null) {
                        QuizRunScreen(
                            vm = vm,
                            onAnswer = { vm.answer(it) },
                            onNext = { vm.next() },
                            onFinish = {
                                kotlinx.coroutines.runBlocking { vm.finish() }
                                navController.navigate(Routes.QUIZ_RESULT)
                            },
                            onExit = { navController.popBackStack() }
                        )
                    }
                }

                composable(Routes.QUIZ_RESULT) {
                    val vm = quizVm
                    val state = vm?.state?.value
                    if (state?.result != null) {
                        QuizResultScreen(
                            result = state.result,
                            questions = state.questions,
                            answers = state.answers,
                            mascotKind = settingsState?.mascotKind ?: MascotKind.CAT,
                            onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                            onReviewIncorrect = { navController.navigate(Routes.QUIZ_SETUP) },
                            onDone = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                        )
                    }
                }

                composable(Routes.PDFS) {
                    val vm: PdfViewModel = viewModel(factory = factory)
                    PdfLibraryScreen(vm = vm, onOpenPdf = { navController.navigate(Routes.pdfReader(it)) })
                }

                composable(
                    Routes.PDF_READER,
                    arguments = listOf(navArgument("pdfId") { type = NavType.StringType })
                ) { entry ->
                    val pdfId = entry.arguments?.getString("pdfId") ?: return@composable
                    val vm: PdfViewModel = viewModel(factory = factory)
                    PdfReaderScreen(
                        vm = vm,
                        pdfId = pdfId,
                        apiBaseUrl = settingsState?.apiBaseUrl ?: "",
                        bearerToken = settingsState?.accessToken,
                        highlightMeanings = settingsState?.highlightMeanings
                            ?: com.medtech.reviewhub.data.prefs.AppSettings.defaultHighlightMeanings(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.HABITS) {
                    val vm: HabitsViewModel = viewModel(factory = factory)
                    HabitsScreen(
                        vm = vm,
                        mascotKind = settingsState?.mascotKind ?: MascotKind.CAT,
                        aiEnabled = !settingsState?.apiBaseUrl.isNullOrBlank(),
                        onAiSummary = {
                            kotlinx.coroutines.runBlocking {
                                val base = container.settings.current().apiBaseUrl
                                if (base.isNotBlank()) {
                                    val text = vm.recentDiaryText()
                                    val result = runCatching {
                                        NetworkModule.api(base).aiDiarySummary(
                                            "Bearer ${container.settings.current().accessToken.orEmpty()}",
                                            AiRequest(text = text, mode = "CHAPTER")
                                        )
                                    }.getOrNull()
                                    val summary = result?.body()?.result?.values?.joinToString(" ") { "$it" }
                                    vm.setAiSummary(
                                        summary ?: "AI summary unavailable — diary stays local."
                                    )
                                }
                            }
                        }
                    )
                }

                composable(Routes.FOCUS) {
                    val timerVm = remember { FocusTimerViewModel() }
                    val appsVm: FocusAppsViewModel = viewModel(factory = factory)
                    FocusScreen(
                        timerVm = timerVm,
                        appsVm = appsVm,
                        mascotKind = settingsState?.mascotKind ?: MascotKind.CAT,
                        focusFeatureEnabled = settingsState?.focusFeatureEnabled ?: true
                    )
                }

                composable(Routes.RESOURCES) {
                    ResourcesScreen()
                }

                composable(Routes.SETTINGS) {
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(vm = vm, onAbout = { navController.navigate(Routes.ABOUT) })
                }

                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    // First run: send the user to onboarding when it has not been completed.
    LaunchedEffect(profile) {
        val p = profile
        if (p != null && !p.onboardingCompleted && navController.currentDestination?.route == Routes.HOME) {
            navController.navigate(Routes.ONBOARDING)
        }
    }
}
