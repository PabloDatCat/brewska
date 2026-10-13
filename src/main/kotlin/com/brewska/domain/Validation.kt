package com.brewska.domain

internal fun text(value: String, maxLength: Int = 200): String = value.trim().also {
    if (it.isEmpty() || it.length > maxLength || '\u0000' in it) throw InvalidInput("Text must be nonblank, contain no NUL, and be at most $maxLength characters")
}
internal fun optionalText(value: String?, maxLength: Int = 200): String? = value?.let { text(it, maxLength) }
internal fun <T> boundedList(values: List<T>, normalize: (T) -> T): List<T> {
    if (values.size > 50) throw InvalidInput("Lists may contain at most 50 items")
    return values.map(normalize)
}
internal fun labels(values: List<String>): List<String> = boundedList(values) { text(it) }
