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
        val phoneticEntry = entry.phonetics?.firstOrNull { !it.text.isNullOrBlank() || !it.audio.isNullOrBlank() }

        return DictionaryLookupResult(
            word = word,
            phonetic = entry.phonetic?.takeIf { it.isNotBlank() } ?: phoneticEntry?.text?.takeIf { it.isNotBlank() },
            audioUrl = entry.phonetics?.firstOrNull { !it.audio.isNullOrBlank() }?.audio?.let(::withScheme),
            partOfSpeech = meaning?.partOfSpeech,
            definition = definitionEntry?.definition,
            example = definitionEntry?.example,
        )
    }

    /**
     * dictionaryapi.dev sometimes serves audio as a protocol-relative URL (e.g.
     * "//ssl.gstatic.com/…mp3") instead of a full one - fine in a browser, but MediaPlayer has no
     * base scheme to resolve it against and fails silently. Assume https for those.
     */
    private fun withScheme(url: String): String = if (url.startsWith("//")) "https:$url" else url
}

@Serializable
private data class FreeDictionaryEntry(
    val word: String? = null,
    val phonetic: String? = null,
    val phonetics: List<FreeDictionaryPhonetic>? = null,
    val meanings: List<FreeDictionaryMeaning>? = null,
)

@Serializable
private data class FreeDictionaryPhonetic(
    val text: String? = null,
    val audio: String? = null,
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
