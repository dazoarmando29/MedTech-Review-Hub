package io.medtech.backend

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.medtech.backend.config.AppConfig
import io.medtech.backend.plugins.configureAuth
import io.medtech.backend.plugins.configureCors
import io.medtech.backend.plugins.configureDatabase
import io.medtech.backend.plugins.configureObservability
import io.medtech.backend.plugins.configureRateLimit
import io.medtech.backend.plugins.configureSerialization
import io.medtech.backend.plugins.configureStatusPages
import io.medtech.backend.routes.AppServices
import io.medtech.backend.routes.configureRoutes
import io.medtech.backend.security.JwtService
import kotlinx.coroutines.runBlocking

fun main() {
    val config = AppConfig.load()
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module() {
    module(AppConfig.load())
}

fun Application.module(config: AppConfig) {
    configureSerialization()
    configureStatusPages()
    configureCors(config)
    configureObservability()
    configureDatabase(config)
    val services = AppServices(config)
    runBlocking { services.contentService.seed() }
    configureAuth(JwtService(config))
    configureRateLimit(config)
    configureRoutes(services)
}
