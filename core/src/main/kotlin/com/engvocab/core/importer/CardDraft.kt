package com.engvocab.core.importer

import com.engvocab.core.model.TargetLanguage
import kotlinx.serialization.Serializable

/** Where an imported (or manually created) card originated from. */
@Serializable
enum class ImportSource {
    MANUAL,
    DUOCARDS,
    KINDLE,
    DICTIONARY,
}

/**
 * A candidate card produced by one of the importers, before the user reviews/edits it
 * in the import preview screen and it becomes a real, persisted card. Also the on-disk
 * shape of a "staged import" file (see the :cli module) - the phone app reads a JSON
 * array of these instead of parsing raw files itself.
 */
@Serializable
data class CardDraft(
    val front: String,
    val back: String,
    val example: String? = null,
    val source: ImportSource,
    /** e.g. the Kindle book title a highlight came from, shown to the user for context. */
    val sourceLabel: String? = null,
    /** The language [front] is written in - set by the importer UI, not detected from content. */
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    /** True if the source (e.g. Duocards' "Imparata completamente" status) says this is already well known. */
    val knownAlready: Boolean = false,
)
