package com.brewska

import com.brewska.config.AppConfig
import kotlin.test.*
import org.junit.jupiter.api.Test

class AppConfigTest {
    @Test fun `defaults bind loopback and one stable owner`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
        assertEquals(config.ownerId, AppConfig.fromEnvironment(emptyMap()).ownerId)
    }
    @Test fun `invalid configuration fails early`() {
        listOf(
            mapOf("PORT" to "bad"), mapOf("PORT" to "0"), mapOf("PORT" to "65536"),
            mapOf("DB_POOL_SIZE" to "0"), mapOf("DB_POOL_SIZE" to "5"),
            mapOf("DB_PARALLELISM" to "-1"), mapOf("DB_PARALLELISM" to "5"),
            mapOf("DATABASE_URL" to "mysql://remote"), mapOf("OWNER_ID" to "bad"),
            mapOf("HOST" to "0.0.0.0"), mapOf("DB_USER" to " ")
        ).forEach { assertFailsWith<IllegalArgumentException>(it.toString()) { AppConfig.fromEnvironment(it) } }
    }
    @Test fun `configuration diagnostic never includes password`() {
        val config = AppConfig.fromEnvironment(mapOf("DB_PASSWORD" to "never-print-this-password"))
        assertFalse(config.toString().contains("never-print-this-password"))
    }

}
