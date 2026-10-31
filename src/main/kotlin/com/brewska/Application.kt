package com.brewska

import com.brewska.config.AppConfig
import com.brewska.database.Database
import com.brewska.catalog.Catalog
import com.brewska.collection.Collection
import com.brewska.http.installErrors
import com.brewska.http.journalRoutes
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun Application.coffeeJournal(config: AppConfig, database: Database, catalog: Catalog, collection: Collection) {
    monitor.subscribe(ApplicationStopped) { database.close() }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false; encodeDefaults = true })
    }
    installErrors()
    routing {
        journalRoutes(config, database, catalog, collection)
        swaggerUI("/swagger") {
            info = OpenApiInfo("Coffee Journal API", "1.0.0")
            tag(name = "Health", description = "Service health checks")
            tag(name = "Roasters", description = "Roasters in the current owner's catalog")
            tag(name = "Coffee lots", description = "Coffee lots in the current owner's catalog")
            tag(name = "Purchases", description = "Purchase history for coffee lots")
            source = OpenApiDocSource.Routing(contentType = ContentType.Application.Json)
        }
    }
}
