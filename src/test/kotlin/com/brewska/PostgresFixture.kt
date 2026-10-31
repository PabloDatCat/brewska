package com.brewska

import com.brewska.config.AppConfig
import com.brewska.database.Database
import com.typesafe.config.ConfigValueFactory
import io.ktor.server.config.HoconApplicationConfig
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer

object PostgresFixture {
    private val env = System.getenv()
    private val container: PostgreSQLContainer<*>? = if (env["TEST_DATABASE_URL"] == null)
        PostgreSQLContainer("postgres:17.6-alpine").apply { start() } else null
    val config = AppConfig.fromConfig(HoconApplicationConfig(TestConfiguration.resource()
        .withValue("database.url", ConfigValueFactory.fromAnyRef(env["TEST_DATABASE_URL"] ?: container!!.jdbcUrl))
        .withValue("database.user", ConfigValueFactory.fromAnyRef(env["TEST_DB_USER"] ?: container?.username
            ?: error("TEST_DB_USER is required with TEST_DATABASE_URL")))
        .withValue("database.password", ConfigValueFactory.fromAnyRef(env["TEST_DB_PASSWORD"] ?: container?.password
            ?: error("TEST_DB_PASSWORD is required with TEST_DATABASE_URL")))
    ))
    val database = Database(config)
    fun reset() = runBlocking {
        database.transaction { connection ->
            connection.createStatement().use { it.execute("TRUNCATE roasters, coffee_lots, purchases CASCADE") }
        }
    }
}
