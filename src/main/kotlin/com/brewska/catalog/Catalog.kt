package com.brewska.catalog

import java.util.UUID
import java.sql.Connection

interface Catalog {
    suspend fun createRoaster(owner: UUID, name: String): Roaster
    suspend fun listRoasters(owner: UUID): List<Roaster>
    suspend fun deleteRoaster(owner: UUID, id: UUID)
    suspend fun createLot(owner: UUID, input: LotInput): CoffeeLot
    suspend fun listLots(owner: UUID): List<CoffeeLot>
    suspend fun getLot(owner: UUID, id: UUID): CoffeeLot
    suspend fun deleteLot(owner: UUID, id: UUID)
}
fun interface LotAvailability {
    fun requireAvailable(connection: Connection, owner: UUID, id: UUID)
}
