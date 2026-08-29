package com.engvocab.core.dictionary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses a response body from the free Dictionary API (https://dictionaryapi.dev,
 * `GET /api/v2/entries/en/{word}`, no key required). Pure function over the raw JSON
 * text, so the actual HTTP call can live in the Android app layer while this stays
 * fully unit-testable.
 */
object FreeDictionaryResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Returns null if [rawJson] isn't a recognizable success response (e.g. "word not found"). */
    fun parse(rawJson: String): DictionaryLookupResult? {
        val entries = try {
            json.decodeFromString<List<FreeDictionaryEntry>>(rawJson)
        } catch (e: Exception) {
            return null
        }

        val entry = entries.firstOrNull() ?: return null
        val word = entry.word?.takeIf { it.isNotBlank() } ?: return null
        val meaning = entry.meanings?.firstOrNull { !it.definitions.isNullOrEmpty() }
        val definitionEntry = meaning?.definitions?.firstOrNull()

        return DictionaryLookupResult(
            word = word,
            phonetic = entry.phonetic?.takeIf { it.isNotBlank() },
            partOfSpeech = meaning?.partOfSpeech,
            definition = definitionEntry?.definition,
            example = definitionEntry?.example,
        )
    }
}

@Serializable
private data class FreeDictionaryEntry(
    val word: String? = null,
    val phonetic: String? = null,
    val meanings: List<FreeDictionaryMeaning>? = null,
)

@Serializable
private data class FreeDictionaryMeaning(
    val partOfSpeech: String? = null,
    val definitions: List<FreeDictionaryDefinition>? = null,
)

@Serializable
private data class FreeDictionaryDefinition(
    val definition: String? = null,
    val example: String? = null,
)
