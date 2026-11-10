package io.medtech.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.medtech.backend.config.AppConfig

fun Application.configureCors(config: AppConfig) {
    install(CORS) {
        if (config.corsAllowAll) {
            anyHost()
        } else {
            config.corsAllowedOrigins.forEach { origin ->
                val host = origin.removePrefix("https://").removePrefix("http://").removeSuffix("/")
                if (host.isNotBlank()) allowHost(host)
            }
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
}
