package com.brewska.http

import com.brewska.catalog.*
import com.brewska.collection.*
import com.brewska.domain.InvalidInput
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

@Serializable data class RoasterInputDto(val name: String)
@Serializable data class OriginDto(val country: String? = null, val region: String? = null,
    val producer: String? = null, val varieties: List<String> = emptyList(), val processingMethod: String? = null) {
    fun domain(): OriginComponent = OriginComponent(country, region, producer, varieties, processingMethod)
}
@Serializable data class LotInputDto(val roasterId: String, val name: String? = null,
    val description: String? = null, val roastProfile: RoastProfile? = null,
    val originComponents: List<OriginDto> = emptyList(), val varieties: List<String> = emptyList(),
    val processingMethod: String? = null, val declaredDescriptors: List<String> = emptyList()) {
    fun domain(): LotInput = LotInput(uuid(roasterId), name, description, roastProfile,
        originComponents.map { it.domain() }, varieties, processingMethod, declaredDescriptors)
}
@Serializable data class PriceInputDto(val amount: String, val currency: String) {
    fun domain(): Price {
        if (!Regex("[0-9]+(\\.[0-9]{1,2})?").matches(amount)) throw InvalidInput("Amount must be a decimal string with at most two decimal places")
        return Price(BigDecimal(amount), currency)
    }
}
@Serializable data class PurchaseInputDto(val purchasedOn: String, val roastedOn: String? = null,
    val weightGrams: Int? = null, val price: PriceInputDto? = null) {
    fun domain(): PurchaseInput = PurchaseInput(date(purchasedOn), roastedOn?.let(::date), weightGrams, price?.domain())
}
internal fun uuid(value: String): UUID {
    if (!Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(value))
        throw InvalidInput("Identifier must be a UUID")
    return UUID.fromString(value)
}
private fun date(value: String): LocalDate {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) throw InvalidInput("Date must use YYYY-MM-DD")
    return try { LocalDate.parse(value) }
    catch (_: DateTimeParseException) { throw InvalidInput("Date must be a valid calendar date") }
}
