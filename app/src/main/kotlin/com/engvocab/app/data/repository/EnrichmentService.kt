package com.engvocab.app.data.repository

import com.engvocab.app.data.network.DictionaryApiClient
import com.engvocab.app.data.network.TranslationApiClient
import com.engvocab.core.model.TargetLanguage

/** Auto-completed fields for a card, fetched from free online dictionary/translation services. */
data class Enrichment(
    val translation: String?,
    val definition: String?,
    val example: String?,
    val partOfSpeech: String?,
    val phonetic: String?,
    val audioUrl: String?,
) {
    val isEmpty: Boolean
        get() = translation == null && definition == null && example == null &&
            partOfSpeech == null && phonetic == null && audioUrl == null
}

class EnrichmentService(
    private val dictionaryApiClient: DictionaryApiClient = DictionaryApiClient(),
    private val translationApiClient: TranslationApiClient = TranslationApiClient(),
) {
    /**
     * [term] is in [language] - the dictionary lookup only works for languages dictionaryapi.dev
     * covers. Pass [needsTranslation] = false to skip the translation API call (and spare its
     * quota) when the card already has a back and only pronunciation is being filled in.
     */
    suspend fun enrich(term: String, language: TargetLanguage, needsTranslation: Boolean = true): Enrichment {
        val dictionary = dictionaryApiClient.lookup(term, language)
        val translation = if (needsTranslation) translationApiClient.translateToItalian(term, language) else null
        return Enrichment(
            translation = translation,
            definition = dictionary?.definition,
            example = dictionary?.example,
            partOfSpeech = dictionary?.partOfSpeech,
            phonetic = dictionary?.phonetic,
            audioUrl = dictionary?.audioUrl,
        )
    }
}
