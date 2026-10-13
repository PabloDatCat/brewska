package com.brewska.collection

import java.util.UUID

interface Collection {
    suspend fun recordPurchase(owner: UUID, lotId: UUID, key: UUID, input: PurchaseInput): PurchaseResult
    suspend fun listPurchases(owner: UUID, lotId: UUID): List<Purchase>
}
