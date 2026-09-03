package com.engvocab.core.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Credentials needed to call a Cloudflare D1 database's REST API directly (no Worker involved). */
data class D1Credentials(val accountId: String, val databaseId: String, val apiToken: String)

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Talks directly to Cloudflare's D1 REST API (`/client/v4/accounts/{account}/d1/database/{db}/query`)
 * over HTTPS with a bearer API token - no Worker deployed. Cloudflare's own docs note this base REST
 * API is best suited for administrative/low-volume use (a shared account-wide rate limit applies),
 * which fits this app: an occasional bulk import from a computer, an occasional phone pull-sync, and
 * one small write per review/card-add for the stats site (see [insertReviewEvent]/[insertCardAddEvent]).
 * Vocabulary content itself still only ever flows one way, cloud -> phone.
 */
class D1Client(
    private val credentials: D1Credentials,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val endpoint =
        "https://api.cloudflare.com/client/v4/accounts/${credentials.accountId}/d1/database/${credentials.databaseId}/query"

    /** Every non-deleted word, all languages - the phone app's full pull-sync. */
    fun fetchAllWords(): List<RemoteWord> = execute("SELECT * FROM words WHERE is_deleted = 0 ORDER BY id")

    /** Existing fronts (lowercased) for one language - used by the `:cli` tool to skip duplicates on re-import. */
    fun fetchFrontsByLanguage(language: String): Set<String> =
        execute("SELECT front FROM words WHERE language = ? AND is_deleted = 0", listOf(language))
            .map { it.front.trim().lowercase() }
            .toSet()

    /** Inserts one row. Only the computer-side `:cli` bulk-import tool ever writes to D1. */
    fun insertWord(
        front: String,
        back: String,
        language: String,
        example: String?,
        source: String,
        sourceLabel: String?,
        knownAlready: Boolean,
    ) {
        val now = System.currentTimeMillis()
        execute(
            sql = """
                INSERT INTO words (front, back, language, example, source, source_label, known_already, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            params = listOf(front, back, language, example, source, sourceLabel, knownAlready, now, now),
        )
    }

    /**
     * Records one review, for the stats site's timeline - best-effort from the app's side (see
     * [com.engvocab.app.data.repository.CardRepository]), never blocks or fails a review locally.
     */
    fun insertReviewEvent(language: String, rating: Int, reviewedAt: Long) {
        executeWrite(
            sql = "INSERT INTO review_events (language, rating, reviewed_at) VALUES (?, ?, ?)",
            params = listOf(language, rating, reviewedAt),
        )
    }

    /** Records one manually-added card, for the stats site's timeline - imported cards use `words.created_at` instead. */
    fun insertCardAddEvent(language: String, addedAt: Long) {
        executeWrite(
            sql = "INSERT INTO card_add_events (language, added_at) VALUES (?, ?)",
            params = listOf(language, addedAt),
        )
    }

    private fun executeWrite(sql: String, params: List<Any?> = emptyList()) {
        val body = requestBody(sql, params)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${credentials.apiToken}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw D1SyncException("D1 request failed: HTTP ${response.code} $responseBody")
            }
            D1ResponseParser.checkSuccess(responseBody)
        }
    }

    private fun requestBody(sql: String, params: List<Any?>) =
        JsonObject(mapOf("sql" to JsonPrimitive(sql), "params" to JsonArray(params.map(::toJsonElement))))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

    private fun execute(sql: String, params: List<Any?> = emptyList()): List<RemoteWord> {
        val body = requestBody(sql, params)

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${credentials.apiToken}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw D1SyncException("D1 request failed: HTTP ${response.code} $responseBody")
            }
            return D1ResponseParser.parseWords(responseBody)
        }
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Long -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(if (value) 1L else 0L)
        else -> JsonPrimitive(value.toString())
    }
}
