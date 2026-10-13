package com.brewska.http

import com.brewska.catalog.*
import com.brewska.collection.Purchase
import kotlinx.serialization.Serializable

@Serializable data class ErrorDto(val code: String, val message: String)
@Serializable data class HealthDto(val status: String)
@Serializable data class RoasterDto(val id: String, val ownerId: String, val name: String)
@Serializable data class LotDto(val id: String, val ownerId: String, val roasterId: String,
    val name: String?, val description: String?, val roastProfile: RoastProfile?,
    val originComponents: List<OriginDto>, val varieties: List<String>, val processingMethod: String?,
    val declaredDescriptors: List<String>)
@Serializable data class PriceDto(val amount: String, val currency: String)
@Serializable data class PurchaseDto(val id: String, val ownerId: String, val coffeeLotId: String,
    val purchasedOn: String, val roastedOn: String?, val weightGrams: Int?, val price: PriceDto?, val state: String)
internal fun Roaster.dto() = RoasterDto(id.toString(), ownerId.toString(), name)
internal fun CoffeeLot.dto() = LotDto(id.toString(), ownerId.toString(), roasterId.toString(), name, description,
    roastProfile, originComponents.map { OriginDto(it.country, it.region, it.producer, it.varieties, it.processingMethod) },
    varieties, processingMethod, declaredDescriptors)
internal fun Purchase.dto() = PurchaseDto(id.toString(), ownerId.toString(), coffeeLotId.toString(),
    purchasedOn.toString(), roastedOn?.toString(), weightGrams, price?.let { PriceDto(it.amount.toPlainString(), it.currency) }, state.name)
