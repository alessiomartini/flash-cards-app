package com.engvocab.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Thrown when the D1 REST API returns an HTTP error or a `{"success": false, ...}` envelope. */
class D1SyncException(message: String) : Exception(message)

@Serializable
internal data class D1Envelope(
    val success: Boolean,
    val result: List<D1QueryResult> = emptyList(),
    val errors: List<D1ApiError> = emptyList(),
)

@Serializable
internal data class D1QueryResult(
    val success: Boolean = true,
    val results: List<RemoteWord> = emptyList(),
)

@Serializable
internal data class D1ApiError(
    val code: Long = 0,
    val message: String = "",
)

/**
 * Parses a response body from Cloudflare's D1 REST API `/query` endpoint - the standard
 * Cloudflare API v4 envelope, `{"success": bool, "result": [{"results": [...rows]}], "errors": [...]}`.
 * Kept separate from [D1Client] so the parsing logic is unit-testable without a real network call.
 */
object D1ResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the rows from the first query result. Throws [D1SyncException] on any API-level failure. */
    fun parseWords(body: String): List<RemoteWord> = parseEnvelope(body).result.firstOrNull()?.results.orEmpty()

    /** For statements with no rows to read back (INSERT/CREATE). Throws [D1SyncException] on failure. */
    fun checkSuccess(body: String) {
        parseEnvelope(body)
    }

    private fun parseEnvelope(body: String): D1Envelope {
        val envelope = try {
            json.decodeFromString(D1Envelope.serializer(), body)
        } catch (e: Exception) {
            throw D1SyncException("Unexpected D1 response: ${e.message}")
        }
        if (!envelope.success) {
            val message = envelope.errors.joinToString { it.message }.ifBlank { "unknown error" }
            throw D1SyncException("D1 query failed: $message")
        }
        return envelope
    }
}
