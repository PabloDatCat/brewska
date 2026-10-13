package com.brewska.database

import com.brewska.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.concurrent.Executors

class Database(config: AppConfig) : AutoCloseable {
    private val dispatcher = Executors.newFixedThreadPool(config.databaseParallelism) { task ->
        Thread(task, "coffee-journal-db").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val source: HikariDataSource
    init {
        try {
            source = HikariDataSource(HikariConfig().apply {
                jdbcUrl = config.databaseUrl
                username = config.databaseUser
                password = config.databasePassword
                maximumPoolSize = config.poolSize
                connectionTimeout = 5_000
                validationTimeout = 2_000
                addDataSourceProperty("options", "-c statement_timeout=5000")
                poolName = "coffee-journal"
            })
        } catch (failure: Throwable) { dispatcher.close(); throw failure }
        try {
            runBlocking(dispatcher) {
                Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate()
                transaction { connection ->
                    connection.prepareStatement("INSERT INTO users(id) VALUES (?) ON CONFLICT DO NOTHING").use {
                        it.setObject(1, config.ownerId); it.executeUpdate()
                    }
                }
            }
        } catch (failure: Throwable) { close(); throw failure }
    }
    suspend fun <T> transaction(block: (Connection) -> T): T {
        check(!source.isClosed) { "Database is closed" }
        return withContext(dispatcher) {
        source.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                ensureActive()
                connection.commit()
                result
            } catch (failure: Throwable) {
                try { connection.rollback() } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
                throw failure
            }
        }
    }
    }
    suspend fun health(): Boolean {
        if (source.isClosed) return false
        return try {
            transaction { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1").use { rows -> rows.next() && rows.getInt(1) == 1 }
            } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    override fun close() { source.close(); dispatcher.close() }
}
