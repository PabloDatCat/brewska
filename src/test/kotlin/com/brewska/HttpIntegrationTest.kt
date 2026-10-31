package com.brewska

import com.brewska.catalog.*
import com.brewska.collection.JdbcCollection
import com.brewska.database.Database
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import kotlin.test.*
import java.sql.SQLException
import java.util.UUID

@Tag("integration")
class HttpIntegrationTest {
    @BeforeEach fun reset() { PostgresFixture.reset() }
    private fun TestApplicationBuilder.install(database: Database = Database(PostgresFixture.config), catalog: Catalog = JdbcCatalog(database)) {
        application { coffeeJournal(PostgresFixture.config, database, catalog, JdbcCollection(database, JdbcCatalog(database))) }
    }
    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, key: String? = null): HttpResponse = client.post(path) {
        contentType(ContentType.Application.Json); setBody(body)
        if (key != null) header("Idempotency-Key", key)
    }
    private suspend fun HttpResponse.objectBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun ApplicationTestBuilder.newLot(extra: String = ""): String {
        val roaster = postJson("/api/roasters", "{\"name\":\"Roaster\"}")
        assertEquals(HttpStatusCode.Created, roaster.status)
        val roasterId = roaster.objectBody().getValue("id").jsonPrimitive.content
        val lot = postJson("/api/coffee-lots", "{\"roasterId\":\"$roasterId\"$extra}")
        assertEquals(HttpStatusCode.Created, lot.status)
        return lot.objectBody().getValue("id").jsonPrimitive.content
    }
    private suspend fun HttpResponse.expectError(status: HttpStatusCode, code: String) {
        assertEquals(status, this.status, bodyAsText())
        assertTrue(headers[HttpHeaders.ContentType]?.startsWith("application/json") == true, "Missing JSON Content-Type: ${bodyAsText()}")
        val body = objectBody(); assertEquals(setOf("code", "message"), body.keys)
        assertEquals(code, body.getValue("code").jsonPrimitive.content)
    }
    @Test fun `HTTP flow creates one catalog lot and two purchases with exact replay`() = testApplication {
        install()
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        val lotId = newLot(",\"name\":\" Blend \",\"roastProfile\":\"OMNI\",\"originComponents\":[{\"country\":\"Colombia\",\"varieties\":[\"Caturra\"]},{\"region\":\"Sidama\"}],\"declaredDescriptors\":[\"Peach\"]")
        val path = "/api/coffee-lots/$lotId/purchases"; val key = UUID.randomUUID().toString()
        val first = postJson(path, "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":\"1\",\"currency\":\"EUR\"}}", key)
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals("1.00", first.objectBody()["price"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        val second = postJson(path, "{\"purchasedOn\":\"2026-10-02\",\"weightGrams\":250}", UUID.randomUUID().toString())
        assertEquals(HttpStatusCode.Created, second.status)
        val replay = postJson(path, "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":\"1.00\",\"currency\":\"EUR\"}}", key)
        assertEquals(HttpStatusCode.OK, replay.status); assertEquals(first.objectBody(), replay.objectBody())
        val history = Json.parseToJsonElement(client.get(path).bodyAsText()).jsonArray
        assertEquals(2, history.size)
        assertEquals(second.objectBody()["id"], history[0].jsonObject["id"])
        assertEquals("PURCHASED", history[1].jsonObject["state"]!!.jsonPrimitive.content)
        val lot = client.get("/api/coffee-lots/$lotId").objectBody()
        assertEquals("Blend", lot["name"]!!.jsonPrimitive.content)
        assertEquals(2, lot["originComponents"]!!.jsonArray.size)
        assertEquals(1, Json.parseToJsonElement(client.get("/api/coffee-lots").bodyAsText()).jsonArray.size)
        assertEquals(1, Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()).jsonArray.size)
    }
    @Test fun `missing and null optional data and preorder dates remain valid`() = testApplication {
        install()
        val lot = newLot(",\"name\":null,\"description\":null,\"roastProfile\":null,\"processingMethod\":null")
        val response = postJson("/api/coffee-lots/$lot/purchases", "{\"purchasedOn\":\"2026-10-01\",\"roastedOn\":\"2026-10-03\",\"price\":null,\"weightGrams\":null}", UUID.randomUUID().toString())
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("2026-10-03", response.objectBody()["roastedOn"]!!.jsonPrimitive.content)
        val stored = client.get("/api/coffee-lots/$lot").objectBody()
        assertEquals(JsonNull, stored["name"])
        assertEquals(JsonArray(emptyList()), stored["varieties"])
    }
    @Test fun `malformed JSON unknown fields and empty required values return 400`() = testApplication {
        install()
        for (body in listOf("{", "[]", "null", "{}", "{\"name\":null}", "{\"name\":\" \"}", "{\"name\":123}", "{\"name\":\"R\",\"ownerId\":\"${UUID.randomUUID()}\"}")) {
            postJson("/api/roasters", body).expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        }
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()))
    }
    @Test fun `lot UUID enum optional text list and nested fields are validated`() = testApplication {
        install()
        val roaster = postJson("/api/roasters", "{\"name\":\"R\"}").objectBody()["id"]!!.jsonPrimitive.content
        val invalidExtras = listOf(",\"roastProfile\":\"DARK\"", ",\"name\":\" \"", ",\"varieties\":null", ",\"varieties\":[\"\"]", ",\"originComponents\":[{\"country\":\" \"}]", ",\"originComponents\":[{\"unexpected\":true}]", ",\"ownerId\":\"${UUID.randomUUID()}\"")
        for (extra in invalidExtras) postJson("/api/coffee-lots", "{\"roasterId\":\"$roaster\"$extra}").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        for (id in listOf("bad", "1-1-1-1-1")) postJson("/api/coffee-lots", "{\"roasterId\":\"$id\"}").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        postJson("/api/coffee-lots", "{\"roasterId\":\"$roaster\",\"varieties\":${JsonArray(List(51) { JsonPrimitive("x") })}}").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/coffee-lots").bodyAsText()))
    }
    @Test fun `invalid purchase dates amounts weights keys and unknown fields create nothing`() = testApplication {
        install(); val lot = newLot(); val path = "/api/coffee-lots/$lot/purchases"
        val invalid = listOf(
            "{}", "{\"purchasedOn\":null}", "{\"purchasedOn\":\"2026-02-30\"}", "{\"purchasedOn\":\"+10000-01-01\"}", "{\"purchasedOn\":\"2026-1-01\"}",
            "{\"purchasedOn\":\"2026-10-01\",\"weightGrams\":0}", "{\"purchasedOn\":\"2026-10-01\",\"weightGrams\":-1}", "{\"purchasedOn\":\"2026-10-01\",\"weightGrams\":2147483648}", "{\"purchasedOn\":\"2026-10-01\",\"weightGrams\":1.5}",
            "{\"purchasedOn\":\"2026-10-01\",\"roastedOn\":\"no\"}", "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":\"1\"}}", "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"currency\":\"EUR\"}}", "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":1,\"currency\":\"EUR\"}}", "{\"purchasedOn\":\"2026-10-01\",\"unknown\":true}") +
            listOf("-1", "NaN", "1e3", "1.000", "10000000000", "9999999999999999999999999999999999999").map { "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":\"$it\",\"currency\":\"EUR\"}}" } +
            listOf("eur", "EURO", "123").map { "{\"purchasedOn\":\"2026-10-01\",\"price\":{\"amount\":\"1\",\"currency\":\"$it\"}}" }
        for (body in invalid) postJson(path, body, UUID.randomUUID().toString()).expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        for (key in listOf(null, "not-a-uuid", "1-1-1-1-1")) postJson(path, "{\"purchasedOn\":\"2026-10-01\"}", key).expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        val multiple = client.post(path) { contentType(ContentType.Application.Json); setBody("{\"purchasedOn\":\"2026-10-01\"}"); header("Idempotency-Key", UUID.randomUUID()); header("Idempotency-Key", UUID.randomUUID()) }
        multiple.expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get(path).bodyAsText()))
    }
    @Test fun `foreign and nonexistent references have uniform 404 responses`() = testApplication {
        install(); val stranger = UUID.randomUUID(); val db = PostgresFixture.database
        db.transaction { connection -> connection.prepareStatement("INSERT INTO users(id) VALUES (?)").use { it.setObject(1, stranger); it.executeUpdate() } }
        val catalog = JdbcCatalog(db); val foreignRoaster = catalog.createRoaster(stranger, "Foreign")
        val foreignLot = catalog.createLot(stranger, LotInput(foreignRoaster.id))
        for (id in listOf(foreignLot.id, UUID.randomUUID())) {
            client.get("/api/coffee-lots/$id").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
            client.get("/api/coffee-lots/$id/purchases").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
            postJson("/api/coffee-lots/$id/purchases", "{\"purchasedOn\":\"2026-10-01\"}", UUID.randomUUID().toString()).expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        }
        for (id in listOf(foreignRoaster.id, UUID.randomUUID())) postJson("/api/coffee-lots", "{\"roasterId\":\"$id\"}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.get("/api/coffee-lots/1-1-1-1-1").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
    }
    @Test fun `mismatching idempotency keys return 409 without additional purchase`() = testApplication {
        install(); val lot = newLot(); val other = newLot(); val key = UUID.randomUUID().toString()
        val path = "/api/coffee-lots/$lot/purchases"
        assertEquals(HttpStatusCode.Created, postJson(path, "{\"purchasedOn\":\"2026-10-01\"}", key).status)
        postJson(path, "{\"purchasedOn\":\"2026-10-02\"}", key).expectError(HttpStatusCode.Conflict, "IDEMPOTENCY_CONFLICT")
        postJson("/api/coffee-lots/$other/purchases", "{\"purchasedOn\":\"2026-10-01\"}", key).expectError(HttpStatusCode.Conflict, "IDEMPOTENCY_CONFLICT")
        assertEquals(1, Json.parseToJsonElement(client.get(path).bodyAsText()).jsonArray.size)
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/coffee-lots/$other/purchases").bodyAsText()))
    }
    @Test fun `unknown routes and unsupported methods have JSON errors`() = testApplication {
        install()
        client.get("/missing").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        val response = client.delete("/api/roasters")
        assertTrue(response.status in listOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed))
        assertEquals("NOT_FOUND", response.objectBody()["code"]!!.jsonPrimitive.content)
    }
    @Test fun `unavailable database health is 503 and mutation error is safe 500`() = testApplication {
        val closed = Database(PostgresFixture.config).apply { close() }; install(closed)
        client.get("/health").expectError(HttpStatusCode.ServiceUnavailable, "DATABASE_UNAVAILABLE")
        val response = postJson("/api/roasters", "{\"name\":\"R\"}")
        response.expectError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        assertFalse(response.bodyAsText().contains("Hikari"))
    }
    @Test fun `unexpected technical failures never expose SQL credentials or stack`() = testApplication {
        val broken = object : Catalog by JdbcCatalog(PostgresFixture.database) {
            override suspend fun listRoasters(owner: UUID): List<Roaster> = throw SQLException("SELECT password=secret from private")
        }
        install(catalog = broken)
        val response = client.get("/api/roasters")
        response.expectError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR")
        for (secret in listOf("SELECT", "secret", "SQLException", "private")) assertFalse(response.bodyAsText().contains(secret))
    }
    @Test fun `concurrent HTTP requests with one key return one ID and one creation`() = testApplication {
        install(); val lot = newLot(); val key = UUID.randomUUID().toString()
        val responses = coroutineScope { (1..12).map { async {
            postJson("/api/coffee-lots/$lot/purchases", "{\"purchasedOn\":\"2026-10-01\"}", key)
        } }.awaitAll() }
        assertEquals(1, responses.count { it.status == HttpStatusCode.Created })
        assertEquals(11, responses.count { it.status == HttpStatusCode.OK })
        assertEquals(1, responses.map { it.objectBody()["id"] }.toSet().size)
        assertEquals(1, Json.parseToJsonElement(client.get("/api/coffee-lots/$lot/purchases").bodyAsText()).jsonArray.size)
    }
    @Test fun `NUL text in roaster and nested lot input returns 400 without writes`() = testApplication {
        install()
        postJson("/api/roasters", "{\"name\":\"R\\u0000oaster\"}").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()))
        val id = postJson("/api/roasters", "{\"name\":\"R\"}").objectBody()["id"]!!.jsonPrimitive.content
        postJson("/api/coffee-lots", "{\"roasterId\":\"$id\",\"originComponents\":[{\"country\":\"Co\\u0000lombia\"}]}").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/coffee-lots").bodyAsText()))
    }

    @Test fun `delete purchase lot and roaster removes each resource`() = testApplication {
        install()
        val lotId = newLot()
        val roasterId = Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()).jsonArray
            .single().jsonObject.getValue("id").jsonPrimitive.content
        val purchase = postJson("/api/coffee-lots/$lotId/purchases", "{\"purchasedOn\":\"2026-10-01\"}", UUID.randomUUID().toString())
        assertEquals(HttpStatusCode.Created, purchase.status)
        val purchaseId = purchase.objectBody().getValue("id").jsonPrimitive.content

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/coffee-lots/$lotId/purchases/$purchaseId").status)
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/coffee-lots/$lotId/purchases").bodyAsText()))
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/coffee-lots/$lotId").status)
        client.get("/api/coffee-lots/$lotId").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/roasters/$roasterId").status)
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()))
    }

    @Test fun `deleting parent resources with dependents returns conflict and preserves children`() = testApplication {
        install()
        val lotId = newLot()
        val roasterId = Json.parseToJsonElement(client.get("/api/roasters").bodyAsText()).jsonArray
            .single().jsonObject.getValue("id").jsonPrimitive.content
        val purchase = postJson("/api/coffee-lots/$lotId/purchases", "{\"purchasedOn\":\"2026-10-01\"}", UUID.randomUUID().toString())
        assertEquals(HttpStatusCode.Created, purchase.status)

        client.delete("/api/roasters/$roasterId").expectError(HttpStatusCode.Conflict, "RESOURCE_HAS_DEPENDENTS")
        client.delete("/api/coffee-lots/$lotId").expectError(HttpStatusCode.Conflict, "RESOURCE_HAS_DEPENDENTS")
        assertEquals(lotId, client.get("/api/coffee-lots/$lotId").objectBody().getValue("id").jsonPrimitive.content)
        assertEquals(1, Json.parseToJsonElement(client.get("/api/coffee-lots/$lotId/purchases").bodyAsText()).jsonArray.size)
    }

    @Test fun `delete endpoints return not found for unavailable resources and bad request for malformed UUIDs`() = testApplication {
        install()
        val stranger = UUID.randomUUID()
        PostgresFixture.database.transaction { connection ->
            connection.prepareStatement("INSERT INTO users(id) VALUES (?)").use { it.setObject(1, stranger); it.executeUpdate() }
        }
        val foreignCatalog = JdbcCatalog(PostgresFixture.database)
        val foreignRoaster = foreignCatalog.createRoaster(stranger, "Foreign")
        val foreignLot = foreignCatalog.createLot(stranger, LotInput(foreignRoaster.id))
        val ownLot = newLot()

        client.delete("/api/roasters/${foreignRoaster.id}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/roasters/${UUID.randomUUID()}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/coffee-lots/${foreignLot.id}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/coffee-lots/${UUID.randomUUID()}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/coffee-lots/$ownLot/purchases/${UUID.randomUUID()}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/coffee-lots/${foreignLot.id}/purchases/${UUID.randomUUID()}").expectError(HttpStatusCode.NotFound, "NOT_FOUND")
        client.delete("/api/roasters/not-a-uuid").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        client.delete("/api/coffee-lots/not-a-uuid").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
        client.delete("/api/coffee-lots/$ownLot/purchases/not-a-uuid").expectError(HttpStatusCode.BadRequest, "INVALID_INPUT")
    }

}
