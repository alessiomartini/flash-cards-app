package com.engvocab.core.importer

/**
 * Parses generic front/back(/example) exports: CSV or TSV, with or without a header row.
 * This is what covers a Duocards export - Duocards doesn't document a stable export schema,
 * so rather than hard-coding one exact layout, this auto-detects the delimiter and tolerates
 * an optional header, taking column 1 as the term, column 2 as the translation/definition,
 * and an optional column 3 as an example sentence.
 */
object DelimitedTextParser {

    private val HEADER_HINTS = setOf(
        "front", "back", "term", "word", "translation", "definition", "meaning",
        "question", "answer", "expression", "fronte", "retro", "traduzione", "significato",
    )

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

    /** Parses a generic front/back export, auto-detecting delimiter and an optional header row. */
    fun parseCards(text: String, source: ImportSource): List<CardDraft> {
        if (text.isBlank()) return emptyList()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = sniffDelimiter(firstLine)
        var rows = tokenize(text, delimiter)
        if (rows.isEmpty()) return emptyList()

        val firstRowLower = rows.first().map { it.trim().lowercase() }
        if (firstRowLower.any { it in HEADER_HINTS }) rows = rows.drop(1)

        return rows.mapNotNull { cols ->
            val front = cols.getOrNull(0)?.trim().orEmpty()
            val back = cols.getOrNull(1)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) return@mapNotNull null
            val example = cols.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            CardDraft(front = front, back = back, example = example, source = source)
        }
    }
}
