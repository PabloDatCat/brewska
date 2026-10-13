package com.brewska

import com.brewska.config.AppConfig
import com.brewska.database.Database
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer

object PostgresFixture {
    private val env = System.getenv()
    private val container: PostgreSQLContainer<*>? = if (env["TEST_DATABASE_URL"] == null)
        PostgreSQLContainer("postgres:17.6-alpine").apply { start() } else null
    val config = AppConfig.fromEnvironment(mapOf(
        "DATABASE_URL" to (env["TEST_DATABASE_URL"] ?: container!!.jdbcUrl),
        "DB_USER" to (env["TEST_DB_USER"] ?: container?.username ?: error("TEST_DB_USER is required with TEST_DATABASE_URL")),
        "DB_PASSWORD" to (env["TEST_DB_PASSWORD"] ?: container?.password ?: error("TEST_DB_PASSWORD is required with TEST_DATABASE_URL"))
    ))
    val database = Database(config)
    fun reset() = runBlocking {
        database.transaction { connection ->
            connection.createStatement().use { it.execute("TRUNCATE roasters, coffee_lots, purchases CASCADE") }
        }
    }
}
