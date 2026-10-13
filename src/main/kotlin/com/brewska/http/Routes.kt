package com.brewska.http

import com.brewska.catalog.Catalog
import com.brewska.collection.Collection
import com.brewska.config.AppConfig
import com.brewska.database.Database
import com.brewska.domain.InvalidInput
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.journalRoutes(config: AppConfig, database: Database, catalog: Catalog, collection: Collection) {
    val owner = config.ownerId
    get("/health") {
        if (database.health()) call.respond(HealthDto("ok"))
        else call.respond(HttpStatusCode.ServiceUnavailable, ErrorDto("DATABASE_UNAVAILABLE", "Database is unavailable"))
    }
    route("/api") {
        route("/roasters") {
            post { call.respond(HttpStatusCode.Created, catalog.createRoaster(owner, call.receive<RoasterInputDto>().name).dto()) }
            get { call.respond(catalog.listRoasters(owner).map { it.dto() }) }
        }
        route("/coffee-lots") {
            post { call.respond(HttpStatusCode.Created, catalog.createLot(owner, call.receive<LotInputDto>().domain()).dto()) }
            get { call.respond(catalog.listLots(owner).map { it.dto() }) }
            route("/{id}") {
                get { call.respond(catalog.getLot(owner, call.lotId()).dto()) }
                route("/purchases") {
                    post {
                        val keys = call.request.headers.getAll("Idempotency-Key")
                        if (keys == null || keys.size != 1) throw InvalidInput("Exactly one Idempotency-Key UUID is required")
                        val key = uuid(keys.single())
                        val result = collection.recordPurchase(owner, call.lotId(), key, call.receive<PurchaseInputDto>().domain())
                        call.respond(if (result.replayed) HttpStatusCode.OK else HttpStatusCode.Created, result.purchase.dto())
                    }
                    get { call.respond(collection.listPurchases(owner, call.lotId()).map { it.dto() }) }
                }
            }
        }
    }
}
private fun ApplicationCall.lotId() = uuid(parameters["id"] ?: throw InvalidInput("Lot identifier is required"))
