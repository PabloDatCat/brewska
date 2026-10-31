package com.brewska.domain

sealed class DomainFailure(message: String) : RuntimeException(message)
class InvalidInput(message: String = "Invalid input") : DomainFailure(message)
class NotFound : DomainFailure("Resource not found")
class IdempotencyConflict : DomainFailure("Idempotency key was already used with different data")
class ResourceHasDependents : DomainFailure("Resource has dependent records")
