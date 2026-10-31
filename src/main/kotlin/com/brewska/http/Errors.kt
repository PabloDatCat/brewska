package com.brewska.http

import com.brewska.domain.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

internal fun Application.installErrors() {
    install(StatusPages) {
        exception<Throwable> { call, failure ->
            if (failure is CancellationException) throw failure
            val (status, error) = when (failure) {
                is InvalidInput -> HttpStatusCode.BadRequest to ErrorDto("INVALID_INPUT", failure.message ?: "Invalid input")
                is NotFound -> HttpStatusCode.NotFound to ErrorDto("NOT_FOUND", "Resource not found")
                is IdempotencyConflict -> HttpStatusCode.Conflict to ErrorDto("IDEMPOTENCY_CONFLICT", "Idempotency key was already used with different data")
                is ResourceHasDependents -> HttpStatusCode.Conflict to ErrorDto("RESOURCE_HAS_DEPENDENTS", "Resource has dependent records")
                is BadRequestException, is ContentTransformationException, is SerializationException ->
                    HttpStatusCode.BadRequest to ErrorDto("INVALID_INPUT", "Request body must match the JSON contract")
                else -> {
                    // Exception messages and stack traces can contain SQL or credentials.
                    this@installErrors.log.error("Request failed ({})", failure.javaClass.simpleName)
                    HttpStatusCode.InternalServerError to ErrorDto("INTERNAL_ERROR", "An internal error occurred")
                }
            }
            call.respond(status, error)
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(status, ErrorDto("NOT_FOUND", "Route or method not found"))
        }
    }
}
