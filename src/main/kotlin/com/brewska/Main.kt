package com.brewska

import com.brewska.catalog.JdbcCatalog
import com.brewska.collection.JdbcCollection
import com.brewska.config.AppConfig
import com.brewska.database.Database
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

fun main() {
    val logger = LoggerFactory.getLogger("CoffeeJournal")
    try {
        val config = AppConfig.fromConfig(HoconApplicationConfig(ConfigFactory.load()))
        Database(config).use { database ->
            val catalog = JdbcCatalog(database)
            val collection = JdbcCollection(database, catalog)
            embeddedServer(Netty, host = config.host, port = config.port) {
                coffeeJournal(config, database, catalog, collection)
            }.start(wait = true)
        }
    } catch (failure: Exception) {
        logger.error("Application startup or server failed ({})", failure.javaClass.simpleName)
        exitProcess(1)
    }
}
