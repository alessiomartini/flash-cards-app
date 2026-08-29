package com.engvocab.core.model

import kotlinx.serialization.Serializable

/**
 * A language the learner is studying vocabulary in. [apiCode] is the ISO 639-1 code used to
 * query external dictionary/translation APIs; [hasDictionarySupport] marks whether
 * dictionaryapi.dev publishes a dictionary for it (it doesn't cover Dutch, so those cards
 * only get an auto-translation, not a definition/example).
 */
@Serializable
enum class TargetLanguage(val apiCode: String, val displayName: String, val flagEmoji: String, val hasDictionarySupport: Boolean) {
    ENGLISH("en", "English", "🇬🇧", true),
    GERMAN("de", "German", "🇩🇪", true),
    FRENCH("fr", "French", "🇫🇷", true),
    DUTCH("nl", "Dutch", "🇳🇱", false),
}
