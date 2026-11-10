package io.medtech.backend.db

import io.medtech.backend.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object Db {
    lateinit var database: Database
        private set

    fun init(config: AppConfig) {
        val driver = if (config.databaseUrl.startsWith("jdbc:h2")) "org.h2.Driver" else "org.postgresql.Driver"
        database = Database.connect(
            url = config.databaseUrl,
            driver = driver,
            user = config.databaseUser,
            password = config.databasePassword,
        )
        Flyway.configure()
            .dataSource(config.databaseUrl, config.databaseUser, config.databasePassword)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

suspend fun <T> tx(block: (Transaction) -> T): T = withContext(Dispatchers.IO) {
    transaction(Db.database) { block(this) }
}

fun newId(): String = UUID.randomUUID().toString()

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun now(): Instant = Clock.System.now()

fun Instant.toEpoch(): Long = this.toEpochMilliseconds()

fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

fun LocalDate.toIsoString(): String = this.toString()

fun todayLocalDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

fun Long.toUtcDateString(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toString()
