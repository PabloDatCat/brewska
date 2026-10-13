package com.brewska

import com.brewska.database.Database
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import kotlin.test.*
import java.util.UUID

@Tag("integration")
class DatabaseTest {
    @BeforeEach fun reset() { PostgresFixture.reset() }
    @Test fun `migrations are repeatable and real PostgreSQL is healthy`(): Unit = runBlocking {
        Database(PostgresFixture.config).use { second ->
            assertTrue(second.health())
            assertEquals("PostgreSQL", second.transaction { it.metaData.databaseProductName })
            assertEquals(1, second.transaction { c -> c.createStatement().use { s ->
                s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { r -> r.next(); r.getInt(1) }
            } })
        }
    }
    @Test fun `failed transaction rolls back all writes`(): Unit = runBlocking {
        val db = PostgresFixture.database
        assertFailsWith<IllegalStateException> {
            db.transaction { c ->
                c.prepareStatement("INSERT INTO roasters(id, owner_id, name) VALUES (?, ?, ?)").use {
                    it.setObject(1, UUID.randomUUID()); it.setObject(2, PostgresFixture.config.ownerId)
                    it.setString(3, "Rolled back"); it.executeUpdate()
                }
                error("force rollback")
            }
        }
        assertEquals(0, db.transaction { c -> c.createStatement().use { s ->
            s.executeQuery("SELECT count(*) FROM roasters").use { r -> r.next(); r.getInt(1) }
        } })
    }
    @Test fun `closed database reports unhealthy and rejects transactions`(): Unit = runBlocking {
        val db = Database(PostgresFixture.config)
        db.close()
        assertFalse(db.health())
        val failure = assertFailsWith<IllegalStateException> { db.transaction { it.isValid(1) } }
        assertEquals(IllegalStateException::class, failure::class)
    }
}
