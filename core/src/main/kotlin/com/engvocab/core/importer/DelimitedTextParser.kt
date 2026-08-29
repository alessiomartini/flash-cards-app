package com.engvocab.core.importer

import com.engvocab.core.model.TargetLanguage

/**
 * Parses generic front/back(/example) exports: CSV or TSV, with or without a header row.
 * This is what covers a Duocards export. Duocards' actual "word list" export has no
 * translation column at all - just the term and a learning-status column (e.g. Italian
 * locale: "Parola;Livello" with values like "In apprendimento" / "Imparata completamente").
 * When a status/level column is detected, its value becomes [CardDraft.knownAlready]
 * instead of a translation, and the back is left empty for auto-fill. Anything else is
 * treated as a plain front/back(/example) export, auto-detecting the delimiter.
 */
object DelimitedTextParser {

    private val HEADER_HINTS = setOf(
        "front", "back", "term", "word", "translation", "definition", "meaning",
        "question", "answer", "expression", "fronte", "retro", "traduzione", "significato", "parola",
    )

    private val LEVEL_HEADER_HINTS = setOf("livello", "level", "stato", "status", "progress", "box")

    private val KNOWN_STATUS_HINTS = listOf("completa", "master", "learned", "known", "done")

    fun sniffDelimiter(sampleLine: String): Char {
        val candidates = listOf('\t', ';', ',')
        return candidates.maxByOrNull { d -> sampleLine.count { it == d } }?.takeIf { sampleLine.contains(it) } ?: ','
    }

    /** Tokenizes [text] into rows of fields, respecting double-quoted fields (RFC 4180-ish). */
    fun tokenize(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> endField()
                    '\r' -> {}
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Parses a generic front/back export, auto-detecting delimiter, header row, and word-list-vs-back-column shape. */
    fun parseCards(text: String, source: ImportSource, language: TargetLanguage = TargetLanguage.ENGLISH): List<CardDraft> {
        if (text.isBlank()) return emptyList()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = sniffDelimiter(firstLine)
        var rows = tokenize(text, delimiter)
        if (rows.isEmpty()) return emptyList()

        val headerRowLower = rows.first().map { it.trim().lowercase() }
        val hasHeader = headerRowLower.any { it in HEADER_HINTS || it in LEVEL_HEADER_HINTS }
        val isLevelColumn = hasHeader && headerRowLower.getOrNull(1) in LEVEL_HEADER_HINTS
        if (hasHeader) rows = rows.drop(1)

        return rows.mapNotNull { cols ->
            val front = cols.getOrNull(0)?.trim().orEmpty()
            if (front.isEmpty()) return@mapNotNull null

            if (isLevelColumn) {
                val status = cols.getOrNull(1)?.trim().orEmpty()
                val known = KNOWN_STATUS_HINTS.any { status.contains(it, ignoreCase = true) }
                CardDraft(front = front, back = "", source = source, language = language, knownAlready = known)
            } else {
                val back = cols.getOrNull(1)?.trim().orEmpty()
                if (back.isEmpty()) return@mapNotNull null
                val example = cols.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                CardDraft(front = front, back = back, example = example, source = source, language = language)
            }
        }
    }
}
