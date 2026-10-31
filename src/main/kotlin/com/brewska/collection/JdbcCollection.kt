package com.brewska.collection

import com.brewska.database.Database
import com.brewska.database.query
import com.brewska.catalog.LotAvailability
import com.brewska.domain.IdempotencyConflict
import com.brewska.domain.NotFound
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

class JdbcCollection(private val database: Database, private val lots: LotAvailability,
    private val newId: () -> UUID = UUID::randomUUID) : Collection {
    override suspend fun recordPurchase(owner: UUID, lotId: UUID, key: UUID, input: PurchaseInput): PurchaseResult {
        val normalized = input.normalized()
        return database.transaction { connection ->
            lots.requireAvailable(connection, owner, lotId)
            val created = connection.query("""INSERT INTO purchases(id, owner_id, coffee_lot_id, idempotency_key,
                purchased_on, roasted_on, weight_grams, price_amount, price_currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, idempotency_key) DO NOTHING RETURNING *""",
                arrayOf<Any?>(newId(), owner, lotId, key, normalized.purchasedOn, normalized.roastedOn,
                    normalized.weightGrams, normalized.price?.amount, normalized.price?.currency), ::purchase).singleOrNull()
            if (created != null) PurchaseResult(created, false)
            else {
                val existing = connection.query("SELECT * FROM purchases WHERE owner_id = ? AND idempotency_key = ?",
                    arrayOf<Any?>(owner, key), ::purchase).single()
                if (existing.coffeeLotId != lotId || PurchaseInput(existing.purchasedOn, existing.roastedOn,
                        existing.weightGrams, existing.price) != normalized) throw IdempotencyConflict()
                PurchaseResult(existing, true)
            }
        }
    }
    override suspend fun listPurchases(owner: UUID, lotId: UUID): List<Purchase> = database.transaction { connection ->
        lots.requireAvailable(connection, owner, lotId)
        connection.query("SELECT * FROM purchases WHERE owner_id = ? AND coffee_lot_id = ? ORDER BY purchased_on DESC, id ASC",
            arrayOf<Any?>(owner, lotId), ::purchase)
    }
    override suspend fun deletePurchase(owner: UUID, lotId: UUID, purchaseId: UUID) = database.transaction { connection ->
        lots.requireAvailable(connection, owner, lotId)
        connection.prepareStatement("DELETE FROM purchases WHERE id = ? AND owner_id = ? AND coffee_lot_id = ?").use {
            it.setObject(1, purchaseId); it.setObject(2, owner); it.setObject(3, lotId)
            if (it.executeUpdate() == 0) throw NotFound()
        }
    }
    private fun purchase(rows: ResultSet): Purchase = Purchase(rows.getObject("id", UUID::class.java),
        rows.getObject("owner_id", UUID::class.java), rows.getObject("coffee_lot_id", UUID::class.java),
        rows.getObject("purchased_on", LocalDate::class.java), rows.getObject("roasted_on", LocalDate::class.java),
        rows.getObject("weight_grams") as Int?, rows.getBigDecimal("price_amount")?.let {
            Price(it, rows.getString("price_currency"))
        }, PurchaseState.valueOf(rows.getString("state")))
}
