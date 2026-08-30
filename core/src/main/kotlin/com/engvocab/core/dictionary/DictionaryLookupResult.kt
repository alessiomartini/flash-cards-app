package com.engvocab.core.dictionary

/** The auto-fetched enrichment for a card: an English definition/example plus an IT translation. */
data class DictionaryLookupResult(
    val word: String,
    val phonetic: String? = null,
    /** URL of a pronunciation audio clip (mp3), when the dictionary provides one. */
    val audioUrl: String? = null,
    val partOfSpeech: String? = null,
    val definition: String? = null,
    val example: String? = null,
)
