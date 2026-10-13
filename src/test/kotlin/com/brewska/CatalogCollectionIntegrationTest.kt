package com.brewska

import com.brewska.catalog.*
import com.brewska.collection.*
import com.brewska.domain.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import kotlin.test.*
import java.util.UUID
import java.time.LocalDate
import java.math.BigDecimal
import java.sql.SQLException

@Tag("integration")
class CatalogCollectionIntegrationTest {
    private val db get() = PostgresFixture.database
    private val owner get() = PostgresFixture.config.ownerId
    private val catalog get() = JdbcCatalog(db)
    private val collection get() = JdbcCollection(db, catalog)
    private val day = LocalDate.of(2026, 10, 1)
    @BeforeEach fun reset() { PostgresFixture.reset() }
    private suspend fun lot(): CoffeeLot = catalog.createLot(owner, LotInput(catalog.createRoaster(owner, " Roaster ").id))
    private suspend fun count(table: String): Int = db.transaction { c -> c.createStatement().use { s ->
        s.executeQuery("SELECT count(*) FROM $table").use { r -> r.next(); r.getInt(1) }
    } }
    @Test fun `one unchanged lot has two purchases including a preorder`(): Unit = runBlocking {
        val lot = lot()
        val first = collection.recordPurchase(owner, lot.id, UUID.randomUUID(), PurchaseInput(day, day.plusDays(2)))
        val second = collection.recordPurchase(owner, lot.id, UUID.randomUUID(), PurchaseInput(day.plusDays(1), weightGrams = 250, price = Price(BigDecimal.ZERO, "EUR")))
        assertFalse(first.replayed); assertFalse(second.replayed)
        assertEquals(listOf(second.purchase.id, first.purchase.id), collection.listPurchases(owner, lot.id).map { it.id })
        assertEquals("PURCHASED", first.purchase.state.name)
        assertEquals(lot, catalog.getLot(owner, lot.id))
        assertEquals(1, catalog.listLots(owner).size)
    }
    @Test fun `mixed origins and optional characteristics roundtrip without invented values`(): Unit = runBlocking {
        val roaster = catalog.createRoaster(owner, "R")
        val input = LotInput(roaster.id, name = " Blend ", roastProfile = RoastProfile.OMNI,
            originComponents = listOf(OriginComponent(country = " Colombia ", varieties = listOf("Caturra")), OriginComponent(region = " Sidama ", processingMethod = "Natural")),
            varieties = listOf("Bourbon"), declaredDescriptors = listOf("Peach"), description = " Notes ")
        val created = catalog.createLot(owner, input)
        assertEquals("Blend", created.name); assertEquals("Notes", created.description)
        assertEquals("Colombia", created.originComponents[0].country)
        assertNull(created.originComponents[1].country)
        assertEquals(listOf("Bourbon"), created.varieties)
        assertEquals(created, catalog.getLot(owner, created.id))
        assertEquals(emptyList(), collection.listPurchases(owner, created.id))
    }
    @Test fun `matching normalized replay returns original id and conflicting replay writes nothing`(): Unit = runBlocking {
        val lot = lot(); val key = UUID.randomUUID()
        val first = collection.recordPurchase(owner, lot.id, key, PurchaseInput(day, price = Price(BigDecimal("1"), "EUR")))
        val replay = collection.recordPurchase(owner, lot.id, key, PurchaseInput(day, price = Price(BigDecimal("1.00"), "EUR")))
        assertTrue(replay.replayed); assertEquals(first.purchase, replay.purchase)
        assertFailsWith<IdempotencyConflict> { collection.recordPurchase(owner, lot.id, key, PurchaseInput(day.plusDays(1))) }
        val other = lot()
        assertFailsWith<IdempotencyConflict> { collection.recordPurchase(owner, other.id, key, PurchaseInput(day)) }
        assertEquals(1, count("purchases"))
    }
    @Test fun `concurrent matching keys atomically produce one purchase and identifier`(): Unit = runBlocking {
        val lot = lot(); val key = UUID.randomUUID()
        val results = coroutineScope { (1..16).map { async(Dispatchers.Default) {
            collection.recordPurchase(owner, lot.id, key, PurchaseInput(day, weightGrams = 250))
        } }.awaitAll() }
        assertEquals(1, results.count { !it.replayed })
        assertEquals(1, results.map { it.purchase.id }.toSet().size)
        assertEquals(1, count("purchases"))
    }
    @Test fun `owner filtering denies foreign and missing identifiers uniformly`(): Unit = runBlocking {
        val lot = lot(); val stranger = UUID.randomUUID()
        db.transaction { c -> c.prepareStatement("INSERT INTO users(id) VALUES (?)").use { it.setObject(1, stranger); it.executeUpdate() } }
        for (id in listOf(lot.id, UUID.randomUUID())) {
            assertFailsWith<NotFound> { catalog.getLot(stranger, id) }
            assertFailsWith<NotFound> { collection.listPurchases(stranger, id) }
            assertFailsWith<NotFound> { collection.recordPurchase(stranger, id, UUID.randomUUID(), PurchaseInput(day)) }
        }
        assertEquals(emptyList(), catalog.listLots(stranger)); assertEquals(emptyList(), catalog.listRoasters(stranger))
        assertFailsWith<NotFound> { catalog.createLot(stranger, LotInput(lot.roasterId)) }
        assertFailsWith<NotFound> { catalog.createLot(owner, LotInput(UUID.randomUUID())) }
        assertEquals(1, count("coffee_lots")); assertEquals(0, count("purchases"))
    }
    @Test fun `roasters require bounded trimmed names without automatic merging`(): Unit = runBlocking {
        assertFailsWith<InvalidInput> { catalog.createRoaster(owner, " ") }
        assertFailsWith<InvalidInput> { catalog.createRoaster(owner, "x".repeat(201)) }
        catalog.createRoaster(owner, " R "); catalog.createRoaster(owner, "R")
        assertEquals(listOf("R", "R"), catalog.listRoasters(owner).map { it.name })
    }
    @Test fun `same date history uses ascending id as stable tie breaker`(): Unit = runBlocking {
        val lot = lot()
        repeat(5) { collection.recordPurchase(owner, lot.id, UUID.randomUUID(), PurchaseInput(day)) }
        val ids = collection.listPurchases(owner, lot.id).map { it.id.toString() }
        assertEquals(ids.sorted(), ids)
    }
    @Test fun `database independently protects owner references and purchase checks`(): Unit = runBlocking {
        val lot = lot(); val otherOwner = UUID.randomUUID()
        db.transaction { c -> c.prepareStatement("INSERT INTO users(id) VALUES (?)").use { it.setObject(1, otherOwner); it.executeUpdate() } }
        assertFailsWith<SQLException> { db.transaction { c -> c.prepareStatement("INSERT INTO coffee_lots(id, owner_id, roaster_id) VALUES (?, ?, ?)").use {
            it.setObject(1, UUID.randomUUID()); it.setObject(2, otherOwner); it.setObject(3, lot.roasterId); it.executeUpdate()
        } } }
        val invalidSql = listOf(
            "weight_grams" to "0", "price_amount, price_currency" to "-1, 'EUR'",
            "price_amount, price_currency" to "1, 'eur'", "price_amount" to "1",
            "owner_id" to "'$otherOwner'"
        )
        for ((column, value) in invalidSql) {
            val ownerColumn = if (column == "owner_id") "" else "owner_id,"
            val ownerValue = if (column == "owner_id") "" else "'$owner',"
            assertFailsWith<SQLException> {
                db.transaction { c -> c.createStatement().use { s ->
                    s.execute("INSERT INTO purchases(id, ${ownerColumn}coffee_lot_id, idempotency_key, purchased_on, $column) VALUES ('${UUID.randomUUID()}', ${ownerValue}'${lot.id}', '${UUID.randomUUID()}', '2026-10-01', $value)")
                } }
            }
        }
        assertEquals(0, count("purchases")); assertEquals(1, count("coffee_lots"))
    }
    @Test fun `database checks text and JSON array forms directly`(): Unit = runBlocking {
        val lot = lot()
        for (assignment in listOf("name = ''", "description = '${"x".repeat(4001)}'", "varieties = '{}'::jsonb", "origin_components = '{}'::jsonb", "roast_profile = 'DARK'")) {
            assertFailsWith<SQLException> { db.transaction { c -> c.createStatement().use { it.execute("UPDATE coffee_lots SET $assignment WHERE id = '${lot.id}'") } } }
        }
        assertEquals(lot, catalog.getLot(owner, lot.id))
    }
    @Test fun `database rejects nonfinite numeric price`() : Unit = runBlocking {
        val lot = lot()
        val purchase = collection.recordPurchase(owner, lot.id, UUID.randomUUID(), PurchaseInput(day, price = Price(BigDecimal.ONE, "EUR"))).purchase
        assertFailsWith<SQLException> { db.transaction { connection ->
            connection.prepareStatement("UPDATE purchases SET price_amount = 'NaN'::numeric WHERE id = ?").use {
                it.setObject(1, purchase.id); it.executeUpdate()
            }
        } }
        assertEquals(BigDecimal("1.00"), collection.listPurchases(owner, lot.id).single().price!!.amount)
    }

}
