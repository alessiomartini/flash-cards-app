package com.lexi.flashcards.core.dictionary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses a response body from the free MyMemory translation API
 * (https://mymemory.translated.net, `GET /get?q={text}&langpair=en|it`, no key required).
 */
object MyMemoryResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Returns null if [rawJson] can't be parsed, or the API returned a quota-exhausted warning. */
    fun parse(rawJson: String): String? {
        val response = try {
            json.decodeFromString<MyMemoryResponse>(rawJson)
        } catch (e: Exception) {
            return null
        }

        val text = response.responseData?.translatedText?.trim()
        if (text.isNullOrBlank()) return null
        if (text.contains("MYMEMORY WARNING", ignoreCase = true)) return null
        return text
    }
}

@Serializable
private data class MyMemoryResponse(val responseData: MyMemoryResponseData? = null)

@Serializable
private data class MyMemoryResponseData(val translatedText: String? = null)
