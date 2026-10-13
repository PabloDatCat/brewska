package com.brewska.catalog

import java.util.UUID
import com.brewska.domain.*

enum class RoastProfile { FILTER, ESPRESSO, OMNI }
data class Roaster(val id: UUID, val ownerId: UUID, val name: String)
data class OriginComponent(val country: String? = null, val region: String? = null,
    val producer: String? = null, val varieties: List<String> = emptyList(), val processingMethod: String? = null)
data class LotInput(val roasterId: UUID, val name: String? = null, val description: String? = null,
    val roastProfile: RoastProfile? = null, val originComponents: List<OriginComponent> = emptyList(),
    val varieties: List<String> = emptyList(), val processingMethod: String? = null,
    val declaredDescriptors: List<String> = emptyList()) {
    fun normalized(): LotInput = copy(
        name = optionalText(name), description = optionalText(description, 4000),
        originComponents = boundedList(originComponents) { origin -> origin.copy(
            country = optionalText(origin.country), region = optionalText(origin.region),
            producer = optionalText(origin.producer), varieties = labels(origin.varieties),
            processingMethod = optionalText(origin.processingMethod)) },
        varieties = labels(varieties), processingMethod = optionalText(processingMethod),
        declaredDescriptors = labels(declaredDescriptors))
}
data class CoffeeLot(val id: UUID, val ownerId: UUID, val roasterId: UUID, val name: String?,
    val description: String?, val roastProfile: RoastProfile?, val originComponents: List<OriginComponent>,
    val varieties: List<String>, val processingMethod: String?, val declaredDescriptors: List<String>)
