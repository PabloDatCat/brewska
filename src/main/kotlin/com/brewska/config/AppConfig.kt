package com.brewska.config

import com.typesafe.config.ConfigException
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.ApplicationConfigurationException
import java.util.UUID

@ConsistentCopyVisibility
data class AppConfig private constructor(
    val host: String, val port: Int, val databaseUrl: String,
    val databaseUser: String, val databasePassword: String,
    val ownerId: UUID, val poolSize: Int, val databaseParallelism: Int
) {
    override fun toString(): String = "AppConfig(host=$host, port=$port, ownerId=$ownerId, poolSize=$poolSize, databaseParallelism=$databaseParallelism)"
    companion object {
        fun fromConfig(config: ApplicationConfig): AppConfig {
            fun value(path: String): String = try {
                config.property(path).getString()
            } catch (_: ConfigException) {
                throw IllegalArgumentException("$path must be configured as a scalar value")
            } catch (_: ApplicationConfigurationException) {
                throw IllegalArgumentException("$path must be configured as a scalar value")
            }
            fun number(path: String, range: IntRange): Int {
                val value = value(path).toIntOrNull()
                    ?: throw IllegalArgumentException("$path must be an integer")
                require(value in range) { "$path is outside its supported range" }
                return value
            }
            val host = value("ktor.deployment.host")
            require(host == "127.0.0.1") { "ktor.deployment.host supports only local loopback hosting" }
            val url = value("database.url")
            require(url.startsWith("jdbc:postgresql://")) { "database.url must be a PostgreSQL JDBC URL" }
            val user = value("database.user")
            require(user.isNotBlank()) { "database.user must not be blank" }
            val owner = try { UUID.fromString(value("journal.ownerId")) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("journal.ownerId must be a UUID") }
            return AppConfig(host, number("ktor.deployment.port", 1..65535), url, user,
                value("database.password"), owner, number("database.poolSize", 1..4),
                number("database.parallelism", 1..4))
        }
    }
}
