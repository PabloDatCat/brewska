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
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
internal fun Route.journalRoutes(config: AppConfig, database: Database, catalog: Catalog, collection: Collection) {
    val owner = config.ownerId
    /**
     * Description: Checks whether the service can reach its database.
     * Tag: Health
     * Responses:
     * - 200 application/json [HealthDto] The database is available.
     * - 503 application/json [ErrorDto] The database is unavailable.
     */
    get("/health") {
        if (database.health()) call.respond(HealthDto("ok"))
        else call.respond(HttpStatusCode.ServiceUnavailable, ErrorDto("DATABASE_UNAVAILABLE", "Database is unavailable"))
    }
    route("/api") {
        route("/roasters") {
            /**
             * Description: Creates a roaster in the current owner's catalog.
             * Tag: Roasters
             * Body: application/json [RoasterInputDto] The roaster name.
             * Responses:
             * - 201 application/json [RoasterDto] The created roaster.
             * - 400 application/json [ErrorDto] The input is invalid.
             */
            post { call.respond(HttpStatusCode.Created, catalog.createRoaster(owner, call.receive<RoasterInputDto>().name).dto()) }
            /**
             * Description: Lists roasters in the current owner's catalog.
             * Tag: Roasters
             * Responses:
             * - 200 application/json The catalog's roasters as a JSON array.
             */
            get { call.respond(catalog.listRoasters(owner).map { it.dto() }) }
            route("/{id}") {
                /**
                 * Description: Deletes a roaster that has no coffee lots.
                 * Tag: Roasters
                 * Path: [String] id The roaster UUID.
                 * Responses:
                 * - 204 The roaster was deleted.
                 * - 400 application/json [ErrorDto] The identifier is not a UUID.
                 * - 404 application/json [ErrorDto] The roaster does not exist.
                 * - 409 application/json [ErrorDto] The roaster still has coffee lots.
                 */
                delete {
                    catalog.deleteRoaster(owner, call.roasterId())
                    call.respond(HttpStatusCode.NoContent)
                }.describe { tag("Roasters") }
            }
        }
        route("/coffee-lots") {
            /**
             * Description: Creates a coffee lot in the current owner's catalog.
             * Body: application/json [LotInputDto] The coffee lot details.
             * Responses:
             * - 201 application/json [LotDto] The created coffee lot.
             * - 400 application/json [ErrorDto] The input is invalid or the roaster does not exist.
             */
            post { call.respond(HttpStatusCode.Created, catalog.createLot(owner, call.receive<LotInputDto>().domain()).dto()) }
                .describe { tag("Coffee lots") }
            /**
             * Description: Lists coffee lots in the current owner's catalog.
             * Responses:
             * - 200 application/json The catalog's coffee lots as a JSON array.
             */
            get { call.respond(catalog.listLots(owner).map { it.dto() }) }
                .describe { tag("Coffee lots") }
            route("/{id}") {
                /**
                 * Description: Gets one coffee lot by its UUID.
                 * Path: [String] id The coffee lot UUID.
                 * Responses:
                 * - 200 application/json [LotDto] The coffee lot.
                 * - 400 application/json [ErrorDto] The identifier is not a UUID.
                 * - 404 application/json [ErrorDto] The coffee lot does not exist.
                 */
                get { call.respond(catalog.getLot(owner, call.lotId()).dto()) }
                    .describe { tag("Coffee lots") }
                /**
                 * Description: Deletes a coffee lot that has no purchases.
                 * Tag: Coffee lots
                 * Path: [String] id The coffee lot UUID.
                 * Responses:
                 * - 204 The coffee lot was deleted.
                 * - 400 application/json [ErrorDto] The identifier is not a UUID.
                 * - 404 application/json [ErrorDto] The coffee lot does not exist.
                 * - 409 application/json [ErrorDto] The coffee lot still has purchases.
                 */
                delete {
                    catalog.deleteLot(owner, call.lotId())
                    call.respond(HttpStatusCode.NoContent)
                }.describe { tag("Coffee lots") }
                route("/purchases") {
                    /**
                     * Description: Records a purchase for a coffee lot. Repeating the same key and input returns the original purchase.
                     * Tag: Purchases
                     * Path: [String] id The coffee lot UUID.
                     * Header: [String] Idempotency-Key A UUID unique to this purchase request.
                     * Body: application/json [PurchaseInputDto] The purchase details.
                     * Responses:
                     * - 201 application/json [PurchaseDto] The newly created purchase.
                     * - 200 application/json [PurchaseDto] The original purchase returned for a matching retry.
                     * - 400 application/json [ErrorDto] The identifier, key, or input is invalid.
                     * - 404 application/json [ErrorDto] The coffee lot does not exist.
                     * - 409 application/json [ErrorDto] The key was already used with different purchase details.
                     */
                    post {
                        val keys = call.request.headers.getAll("Idempotency-Key")
                        if (keys == null || keys.size != 1) throw InvalidInput("Exactly one Idempotency-Key UUID is required")
                        val key = uuid(keys.single())
                        val result = collection.recordPurchase(owner, call.lotId(), key, call.receive<PurchaseInputDto>().domain())
                        call.respond(if (result.replayed) HttpStatusCode.OK else HttpStatusCode.Created, result.purchase.dto())
                    }
                    /**
                     * Description: Lists purchase history for a coffee lot, newest purchase first.
                     * Tag: Purchases
                     * Path: [String] id The coffee lot UUID.
                     * Responses:
                     * - 200 application/json The coffee lot's purchase history as a JSON array.
                     * - 400 application/json [ErrorDto] The identifier is not a UUID.
                     * - 404 application/json [ErrorDto] The coffee lot does not exist.
                     */
                    get { call.respond(collection.listPurchases(owner, call.lotId()).map { it.dto() }) }
                    route("/{purchaseId}") {
                        /**
                         * Description: Deletes one purchase from a coffee lot.
                         * Tag: Purchases
                         * Path: [String] id The coffee lot UUID.
                         * Path: [String] purchaseId The purchase UUID.
                         * Responses:
                         * - 204 The purchase was deleted.
                         * - 400 application/json [ErrorDto] An identifier is not a UUID.
                         * - 404 application/json [ErrorDto] The coffee lot or purchase does not exist.
                         */
                        delete {
                            collection.deletePurchase(owner, call.lotId(), call.purchaseId())
                            call.respond(HttpStatusCode.NoContent)
                        }.describe { tag("Purchases") }
                    }
                }
            }
        }
    }
}
private fun ApplicationCall.lotId() = uuid(parameters["id"] ?: throw InvalidInput("Lot identifier is required"))
private fun ApplicationCall.roasterId() = uuid(parameters["id"] ?: throw InvalidInput("Roaster identifier is required"))
private fun ApplicationCall.purchaseId() = uuid(parameters["purchaseId"] ?: throw InvalidInput("Purchase identifier is required"))
