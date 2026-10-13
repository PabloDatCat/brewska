package com.brewska.catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

internal object CatalogJson {
    fun labels(values: List<String>): String = Json.encodeToString(values)
    fun labels(value: String): List<String> = Json.decodeFromString(value)
    fun origins(values: List<OriginComponent>): String = JsonArray(values.map { origin -> buildJsonObject {
        put("country", origin.country?.let(::JsonPrimitive) ?: JsonNull)
        put("region", origin.region?.let(::JsonPrimitive) ?: JsonNull)
        put("producer", origin.producer?.let(::JsonPrimitive) ?: JsonNull)
        put("varieties", JsonArray(origin.varieties.map(::JsonPrimitive)))
        put("processingMethod", origin.processingMethod?.let(::JsonPrimitive) ?: JsonNull)
    } }).toString()
    fun origins(value: String): List<OriginComponent> = Json.parseToJsonElement(value).jsonArray.map { element ->
        val objectValue = element.jsonObject
        fun text(name: String): String? = objectValue[name]?.jsonPrimitive?.contentOrNull
        OriginComponent(text("country"), text("region"), text("producer"),
            objectValue.getValue("varieties").jsonArray.map { it.jsonPrimitive.content }, text("processingMethod"))
    }
}
