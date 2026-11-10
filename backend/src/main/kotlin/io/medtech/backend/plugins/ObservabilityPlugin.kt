package io.medtech.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.medtech.backend.config.AppConfig
import io.medtech.backend.db.Db

fun Application.configureObservability() {
    install(CallLogging)
    install(Compression) {
        gzip()
        deflate()
    }
}

fun Application.configureDatabase(config: AppConfig) {
    Db.init(config)
}
