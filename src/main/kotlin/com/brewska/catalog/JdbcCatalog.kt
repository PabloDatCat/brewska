package com.brewska.catalog

import com.brewska.database.Database
import com.brewska.database.query
import com.brewska.domain.NotFound
import com.brewska.domain.ResourceHasDependents
import com.brewska.domain.text
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class JdbcCatalog(private val database: Database, private val newId: () -> UUID = UUID::randomUUID) : Catalog, LotAvailability {
    override suspend fun createRoaster(owner: UUID, name: String): Roaster {
        val normalizedName = text(name)
        return database.transaction { connection ->
            connection.query("INSERT INTO roasters(id, owner_id, name) VALUES (?, ?, ?) RETURNING *",
                arrayOf<Any?>(newId(), owner, normalizedName), ::roaster).single()
        }
    }
    override suspend fun listRoasters(owner: UUID): List<Roaster> = database.transaction {
        it.query("SELECT * FROM roasters WHERE owner_id = ? ORDER BY id", arrayOf<Any?>(owner), ::roaster)
    }
    override suspend fun deleteRoaster(owner: UUID, id: UUID) = database.transaction { connection ->
        val exists = connection.query("SELECT id FROM roasters WHERE id = ? AND owner_id = ? FOR UPDATE",
            arrayOf<Any?>(id, owner)) { it.getObject(1, UUID::class.java) }.isNotEmpty()
        if (!exists) throw NotFound()
        val hasLots = connection.query("SELECT id FROM coffee_lots WHERE roaster_id = ? AND owner_id = ? LIMIT 1",
            arrayOf<Any?>(id, owner)) { it.getObject(1, UUID::class.java) }.isNotEmpty()
        if (hasLots) throw ResourceHasDependents()
        connection.prepareStatement("DELETE FROM roasters WHERE id = ? AND owner_id = ?").use {
            it.setObject(1, id); it.setObject(2, owner)
            if (it.executeUpdate() == 0) throw NotFound()
        }
    }
    override suspend fun createLot(owner: UUID, input: LotInput): CoffeeLot {
        val lot = input.normalized()
        return database.transaction { connection ->
            val available = connection.query("SELECT id FROM roasters WHERE id = ? AND owner_id = ?",
                arrayOf<Any?>(lot.roasterId, owner)) { it.getObject(1, UUID::class.java) }.isNotEmpty()
            if (!available) throw NotFound()
            connection.query("""INSERT INTO coffee_lots(id, owner_id, roaster_id, name, description, roast_profile,
                origin_components, varieties, processing_method, declared_descriptors)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb) RETURNING *""",
                arrayOf<Any?>(newId(), owner, lot.roasterId, lot.name, lot.description, lot.roastProfile?.name,
                    CatalogJson.origins(lot.originComponents), CatalogJson.labels(lot.varieties), lot.processingMethod,
                    CatalogJson.labels(lot.declaredDescriptors)), ::coffeeLot).single()
        }
    }
    override suspend fun listLots(owner: UUID): List<CoffeeLot> = database.transaction {
        it.query("SELECT * FROM coffee_lots WHERE owner_id = ? ORDER BY id", arrayOf<Any?>(owner), ::coffeeLot)
    }
    override suspend fun getLot(owner: UUID, id: UUID): CoffeeLot = database.transaction {
        it.query("SELECT * FROM coffee_lots WHERE id = ? AND owner_id = ?", arrayOf<Any?>(id, owner), ::coffeeLot)
            .singleOrNull() ?: throw NotFound()
    }
    override suspend fun deleteLot(owner: UUID, id: UUID) = database.transaction { connection ->
        val exists = connection.query("SELECT id FROM coffee_lots WHERE id = ? AND owner_id = ? FOR UPDATE",
            arrayOf<Any?>(id, owner)) { it.getObject(1, UUID::class.java) }.isNotEmpty()
        if (!exists) throw NotFound()
        val hasPurchases = connection.query("SELECT id FROM purchases WHERE coffee_lot_id = ? AND owner_id = ? LIMIT 1",
            arrayOf<Any?>(id, owner)) { it.getObject(1, UUID::class.java) }.isNotEmpty()
        if (hasPurchases) throw ResourceHasDependents()
        connection.prepareStatement("DELETE FROM coffee_lots WHERE id = ? AND owner_id = ?").use {
            it.setObject(1, id); it.setObject(2, owner)
            if (it.executeUpdate() == 0) throw NotFound()
        }
    }
    override fun requireAvailable(connection: Connection, owner: UUID, id: UUID) {
        if (connection.query("SELECT id FROM coffee_lots WHERE id = ? AND owner_id = ?", arrayOf<Any?>(id, owner)) {
            it.getObject(1, UUID::class.java)
        }.isEmpty()) throw NotFound()
    }
    private fun roaster(rows: ResultSet): Roaster = Roaster(rows.getObject("id", UUID::class.java),
        rows.getObject("owner_id", UUID::class.java), rows.getString("name"))
    private fun coffeeLot(rows: ResultSet): CoffeeLot = CoffeeLot(rows.getObject("id", UUID::class.java),
        rows.getObject("owner_id", UUID::class.java), rows.getObject("roaster_id", UUID::class.java),
        rows.getString("name"), rows.getString("description"), rows.getString("roast_profile")?.let(RoastProfile::valueOf),
        CatalogJson.origins(rows.getString("origin_components")), CatalogJson.labels(rows.getString("varieties")),
        rows.getString("processing_method"), CatalogJson.labels(rows.getString("declared_descriptors")))
}
