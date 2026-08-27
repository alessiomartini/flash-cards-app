package com.lexi.flashcards.data.repository

import com.lexi.flashcards.data.network.DictionaryApiClient
import com.lexi.flashcards.data.network.TranslationApiClient

/** Auto-completed fields for a card, fetched from free online dictionary/translation services. */
data class Enrichment(
    val translation: String?,
    val definition: String?,
    val example: String?,
    val partOfSpeech: String?,
) {
    val isEmpty: Boolean
        get() = translation == null && definition == null && example == null && partOfSpeech == null
}

class EnrichmentService(
    private val dictionaryApiClient: DictionaryApiClient = DictionaryApiClient(),
    private val translationApiClient: TranslationApiClient = TranslationApiClient(),
) {
    suspend fun enrich(englishTerm: String): Enrichment {
        val dictionary = dictionaryApiClient.lookup(englishTerm)
        val translation = translationApiClient.translateToItalian(englishTerm)
        return Enrichment(
            translation = translation,
            definition = dictionary?.definition,
            example = dictionary?.example,
            partOfSpeech = dictionary?.partOfSpeech,
        )
    }
}
