package io.medtech.backend.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import io.medtech.backend.config.AppConfig
import io.medtech.backend.domain.ApiException
import io.medtech.backend.service.AdminService
import io.medtech.backend.service.AiService
import io.medtech.backend.service.AnalyticsService
import io.medtech.backend.service.AuthService
import io.medtech.backend.service.ContentService
import io.medtech.backend.service.FlashcardService
import io.medtech.backend.service.HabitService
import io.medtech.backend.service.PdfService
import io.medtech.backend.service.PlanService
import io.medtech.backend.service.QuizService
import io.medtech.backend.service.UserService
import io.medtech.backend.content.ContentStore

class AppServices(val config: AppConfig) {
    val contentStore: ContentStore = ContentStore.load(config.contentDir)
    val contentService: ContentService = ContentService(contentStore)
    val authService: AuthService = AuthService(config)
    val userService: UserService = UserService()
    val planService: PlanService = PlanService(contentService = contentService)
    val flashcardService: FlashcardService = FlashcardService()
    val quizService: QuizService = QuizService(contentService = contentService)
    val pdfService: PdfService = PdfService(config)
    val aiService: AiService = AiService(config)
    val habitService: HabitService = HabitService()
    val adminService: AdminService = AdminService()
    val analyticsService: AnalyticsService = AnalyticsService()
}

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()?.payload?.subject ?: throw ApiException.unauthorized()

fun parseInstantParam(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { kotlinx.datetime.Instant.parse(value).toEpochMilliseconds() }.getOrNull()
        ?: runCatching {
            kotlinx.datetime.LocalDate.parse(value)
                .atTime(0, 0)
                .toInstant(kotlinx.datetime.TimeZone.UTC)
                .toEpochMilliseconds()
        }.getOrNull()
        ?: throw ApiException.validation("Invalid datetime: $value")
}

suspend inline fun <reified T : Any> ApplicationCall.receiveOrThrow(): T =
    try {
        receive()
    } catch (e: ApiException) {
        throw e
    } catch (e: Throwable) {
        throw ApiException.validation("Invalid request body", mapOf("body" to (e.message ?: "unable to parse")))
    }

fun Application.configureRoutes(services: AppServices) {
    healthRoutes()
    authRoutes(services)
    userRoutes(services)
    contentRoutes(services)
    planRoutes(services)
    flashcardRoutes(services)
    quizRoutes(services)
    pdfRoutes(services)
    aiRoutes(services)
    habitRoutes(services)
    adminRoutes(services)
}
