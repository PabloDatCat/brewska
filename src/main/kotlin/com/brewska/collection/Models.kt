package com.brewska.collection

import java.util.UUID
import java.time.LocalDate
import java.math.BigDecimal
import com.brewska.domain.InvalidInput

data class Price(val amount: BigDecimal, val currency: String) {
    fun normalized(): Price {
        if (amount.signum() < 0 || amount.scale() > 2 || amount > BigDecimal("9999999999.99"))
            throw InvalidInput("Price must be nonnegative, at most 9999999999.99, with at most two decimals")
        if (!Regex("[A-Z]{3}").matches(currency)) throw InvalidInput("Currency must contain three uppercase letters")
        return copy(amount = amount.setScale(2))
    }
}
data class PurchaseInput(val purchasedOn: LocalDate, val roastedOn: LocalDate? = null,
    val weightGrams: Int? = null, val price: Price? = null) {
    fun normalized(): PurchaseInput {
        if (weightGrams != null && weightGrams <= 0) throw InvalidInput("Weight must be a positive integer")
        return copy(price = price?.normalized())
    }
}
enum class PurchaseState { PURCHASED }
data class Purchase(val id: UUID, val ownerId: UUID, val coffeeLotId: UUID, val purchasedOn: LocalDate,
    val roastedOn: LocalDate?, val weightGrams: Int?, val price: Price?, val state: PurchaseState = PurchaseState.PURCHASED)
data class PurchaseResult(val purchase: Purchase, val replayed: Boolean)
