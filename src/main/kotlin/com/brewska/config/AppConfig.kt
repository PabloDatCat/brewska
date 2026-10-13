package com.brewska.config

import java.util.UUID

@ConsistentCopyVisibility
data class AppConfig private constructor(
    val host: String, val port: Int, val databaseUrl: String,
    val databaseUser: String, val databasePassword: String,
    val ownerId: UUID, val poolSize: Int, val databaseParallelism: Int
) {
    override fun toString(): String = "AppConfig(host=$host, port=$port, ownerId=$ownerId, poolSize=$poolSize, databaseParallelism=$databaseParallelism)"
    companion object {
        fun fromEnvironment(environment: Map<String, String>): AppConfig {
            fun number(name: String, fallback: Int, range: IntRange): Int {
                val value = environment[name]?.toIntOrNull() ?: if (name in environment)
                    throw IllegalArgumentException("$name must be an integer") else fallback
                require(value in range) { "$name is outside its supported range" }
                return value
            }
            val host = environment["HOST"] ?: "127.0.0.1"
            require(host == "127.0.0.1") { "Only local loopback hosting is supported" }
            val url = environment["DATABASE_URL"] ?: "jdbc:postgresql://127.0.0.1:5432/coffee_journal"
            require(url.startsWith("jdbc:postgresql://")) { "DATABASE_URL must be a PostgreSQL JDBC URL" }
            val user = environment["DB_USER"] ?: "coffee_journal"
            require(user.isNotBlank()) { "DB_USER must not be blank" }
            val owner = UUID.fromString(environment["OWNER_ID"] ?: "00000000-0000-0000-0000-000000000001")
            return AppConfig(host, number("PORT", 8080, 1..65535), url, user,
                environment["DB_PASSWORD"] ?: "local-coffee-journal", owner,
                number("DB_POOL_SIZE", 4, 1..4), number("DB_PARALLELISM", 4, 1..4))
        }
    }
}
