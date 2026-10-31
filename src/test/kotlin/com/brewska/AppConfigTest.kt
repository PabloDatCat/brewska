package com.brewska

import com.brewska.config.AppConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigValueFactory
import io.ktor.server.config.HoconApplicationConfig
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*

class AppConfigTest {
    private fun read(config: Config = TestConfiguration.resource()): AppConfig =
        AppConfig.fromConfig(HoconApplicationConfig(config))
    private fun changed(path: String, value: Any?): Config = TestConfiguration.resource()
        .withValue(path, ConfigValueFactory.fromAnyRef(value))
    private val requiredPaths = listOf("ktor.deployment.host", "ktor.deployment.port", "database.url",
        "database.user", "database.password", "database.poolSize", "database.parallelism", "journal.ownerId")

    @Test fun `resource defaults supply the complete local configuration`() {
        val config = read()
        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
        assertEquals("jdbc:postgresql://127.0.0.1:5432/coffee_journal", config.databaseUrl)
        assertEquals("coffee_journal", config.databaseUser)
        assertEquals("local-coffee-journal", config.databasePassword)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), config.ownerId)
        assertEquals(4, config.poolSize)
        assertEquals(4, config.databaseParallelism)
    }
    @Test fun `all eight existing environment substitutions override resource defaults`() {
        val config = read(TestConfiguration.resource(mapOf(
            "HOST" to "127.0.0.1", "PORT" to "9091",
            "DATABASE_URL" to "jdbc:postgresql://127.0.0.1:5544/override_test",
            "DB_USER" to "override_user", "DB_PASSWORD" to "override_password",
            "DB_POOL_SIZE" to "2", "DB_PARALLELISM" to "3",
            "OWNER_ID" to "f34ddc45-a120-4f94-baf7-ae35e991ba2c"
        )))
        assertEquals("127.0.0.1", config.host)
        assertEquals(9091, config.port)
        assertEquals("jdbc:postgresql://127.0.0.1:5544/override_test", config.databaseUrl)
        assertEquals("override_user", config.databaseUser)
        assertEquals("override_password", config.databasePassword)
        assertEquals(2, config.poolSize)
        assertEquals(3, config.databaseParallelism)
        assertEquals(UUID.fromString("f34ddc45-a120-4f94-baf7-ae35e991ba2c"), config.ownerId)
        // HOST has only one accepted value; a forbidden substituted value proves it was read.
        assertFailsWith<IllegalArgumentException> { read(TestConfiguration.resource(mapOf("HOST" to "0.0.0.0"))) }
    }
    @Test fun `an absent environment override leaves other defaults intact`() {
        val config = read(TestConfiguration.resource(mapOf("PORT" to "9092")))
        assertEquals(9092, config.port)
        assertEquals("coffee_journal", config.databaseUser)
        assertEquals(4, config.poolSize)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), config.ownerId)
    }
    @Test fun `a full HOCON file supplies nondefault settings without Kotlin fallbacks`() {
        val file = Files.createTempFile("coffee-journal-config-test-", ".conf")
        val propertyNames = requiredPaths + listOf("config.file", "config.url", "config.resource", "config.override_with_env_vars")
        val previousProperties = propertyNames.associateWith(System::getProperty)
        try {
            propertyNames.forEach(System::clearProperty)
            System.setProperty("config.file", file.toString())
            Files.writeString(file, """
                ktor.deployment { host = "127.0.0.1", port = 19091 }
                database {
                    url = "jdbc:postgresql://127.0.0.1:5545/file_test"
                    user = "file_user"
                    password = "file_password"
                    poolSize = 1
                    parallelism = 2
                }
                journal.ownerId = "4f452596-65c1-427d-b0fb-5b6ae086da29"
            """.trimIndent())
            ConfigFactory.invalidateCaches()
            val config = read(ConfigFactory.load(ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setUseSystemEnvironment(false)))
            assertEquals(19091, config.port)
            assertEquals("jdbc:postgresql://127.0.0.1:5545/file_test", config.databaseUrl)
            assertEquals("file_user", config.databaseUser)
            assertEquals("file_password", config.databasePassword)
            assertEquals(1, config.poolSize)
            assertEquals(2, config.databaseParallelism)
            assertEquals(UUID.fromString("4f452596-65c1-427d-b0fb-5b6ae086da29"), config.ownerId)
            Files.writeString(file, "ktor.deployment.port = 19092")
            ConfigFactory.invalidateCaches()
            val incomplete = ConfigFactory.load(ConfigParseOptions.defaults(), ConfigResolveOptions.defaults().setUseSystemEnvironment(false))
            assertFalse(incomplete.hasPath("database.url"), "External config must replace bundled application.conf")
            assertFailsWith<IllegalArgumentException> { read(incomplete) }
        } finally {
            previousProperties.forEach { (name, value) ->
                if (value == null) System.clearProperty(name) else System.setProperty(name, value)
            }
            ConfigFactory.invalidateCaches()
            Files.deleteIfExists(file)
        }
    }
    @Test fun `every required missing or null value fails instead of falling back to code defaults`() {
        for (path in requiredPaths) {
            for (config in listOf(TestConfiguration.resource().withoutPath(path), changed(path, null))) {
                val failure = assertFailsWith<IllegalArgumentException>(path) { read(config) }
                assertContains(failure.message.orEmpty(), path)
            }
        }
    }
    @Test fun `numeric values reject wrong types overflow and fractional notation`() {
        for (path in listOf("ktor.deployment.port", "database.poolSize", "database.parallelism")) {
            for (value in listOf("bad", "", "2147483648", 1.5, true, listOf(2), mapOf("value" to 2))) {
                val failure = assertFailsWith<IllegalArgumentException>("$path: $value") { read(changed(path, value)) }
                assertContains(failure.message.orEmpty(), path)
            }
        }
    }
    @Test fun `numeric bounds and existing host URL owner and user rules remain enforced`() {
        val invalid = listOf(
            "ktor.deployment.port" to 0, "ktor.deployment.port" to 65536,
            "database.poolSize" to 0, "database.poolSize" to 5,
            "database.parallelism" to -1, "database.parallelism" to 5,
            "database.url" to "mysql://remote", "journal.ownerId" to "bad",
            "ktor.deployment.host" to "0.0.0.0", "database.user" to " "
        )
        invalid.forEach { (path, value) -> assertFailsWith<IllegalArgumentException>(path) { read(changed(path, value)) } }
        assertEquals(1, read(changed("ktor.deployment.port", 1)).port)
        assertEquals(65535, read(changed("ktor.deployment.port", 65535)).port)
        assertEquals(1, read(changed("database.poolSize", 1)).poolSize)
        assertEquals(1, read(changed("database.parallelism", 1)).databaseParallelism)
    }
    @Test fun `configuration and invalid value diagnostics do not expose secret values`() {
        val secret = "must-never-appear-in-diagnostics"
        val config = read(changed("database.password", secret)
            .withValue("database.url", ConfigValueFactory.fromAnyRef("jdbc:postgresql://127.0.0.1:5432/db?password=$secret")))
        assertFalse(config.toString().contains(secret))
        val failures = listOf(
            changed("journal.ownerId", secret), changed("ktor.deployment.port", secret),
            changed("database.password", mapOf("secret" to secret)),
            changed("database.user", listOf(secret)), changed("database.url", listOf(secret)),
            ConfigFactory.parseString("database.password = \${$secret}").withFallback(TestConfiguration.resource())
        )
        for (invalid in failures) {
            val failure = assertFailsWith<IllegalArgumentException> { read(invalid) }
            assertFalse(failure.stackTraceToString().contains(secret))
        }
    }
    @Test fun `an explicitly empty password remains supported`() {
        assertEquals("", read(changed("database.password", "")).databasePassword)
    }
}
