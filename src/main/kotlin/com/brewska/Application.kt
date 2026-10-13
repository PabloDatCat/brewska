package com.brewska

import com.brewska.config.AppConfig
import com.brewska.database.Database
import com.brewska.catalog.Catalog
import com.brewska.collection.Collection
import com.brewska.http.installErrors
import com.brewska.http.journalRoutes
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun Application.coffeeJournal(config: AppConfig, database: Database, catalog: Catalog, collection: Collection) {
    monitor.subscribe(ApplicationStopped) { database.close() }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false; encodeDefaults = true })
    }
    installErrors()
    routing { journalRoutes(config, database, catalog, collection) }
}
