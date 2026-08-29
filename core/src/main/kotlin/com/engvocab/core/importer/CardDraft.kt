package com.engvocab.core.importer

/** Where an imported (or manually created) card originated from. */
enum class ImportSource {
    MANUAL,
    DUOCARDS,
    KINDLE,
    DICTIONARY,
}

/**
 * A candidate card produced by one of the importers, before the user reviews/edits it
 * in the import preview screen and it becomes a real, persisted card.
 */
data class CardDraft(
    val front: String,
    val back: String,
    val example: String? = null,
    val source: ImportSource,
    /** e.g. the Kindle book title a highlight came from, shown to the user for context. */
    val sourceLabel: String? = null,
)
