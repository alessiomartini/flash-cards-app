package com.lexi.flashcards.core.importer

/**
 * Parses a Kindle "My Clippings.txt" file (found at the root of the Kindle's `documents`
 * folder when connected over USB) into card drafts.
 *
 * Kindle writes one entry per highlight/note/bookmark, separated by a line of dashes/equals
 * signs. When you highlight a word and then attach a Note to it (e.g. typing its translation),
 * Kindle stores them as two separate, adjacent entries at the same location - this parser
 * pairs them back together into a single front/back card. A highlight with no note becomes a
 * card with an empty back, ready to be filled in manually or via the dictionary auto-lookup.
 *
 * The metadata line's language depends on the Kindle's device language (English "Location",
 * Italian "posizione", etc.), so type/location detection is done with tolerant,
 * multi-language regexes rather than a fixed template.
 */
object KindleClippingsParser {

    private val ENTRY_SEPARATOR = Regex("(?m)^\\s*[=]{3,}\\s*$")
    private val TYPE_HIGHLIGHT = Regex("highlight|evidenzia", RegexOption.IGNORE_CASE)
    private val TYPE_NOTE = Regex("\\bnote\\b|\\bnota\\b|\\bappunt", RegexOption.IGNORE_CASE)
    private val TYPE_BOOKMARK = Regex("bookmark|segnalibro", RegexOption.IGNORE_CASE)
    private val LOCATION = Regex(
        "(?:location|loc\\.|posizione|position)\\D{0,15}?(\\d+)(?:\\s*-\\s*(\\d+))?",
        RegexOption.IGNORE_CASE,
    )

    private enum class ClippingType { HIGHLIGHT, NOTE, BOOKMARK, UNKNOWN }

    private data class RawClipping(
        val title: String,
        val type: ClippingType,
        val locationStart: Int?,
        val locationEnd: Int?,
        val content: String,
    )

    fun parse(text: String): List<CardDraft> {
        val entries = splitEntries(text).mapNotNull(::parseEntry)
        return dedupe(pairHighlightsWithNotes(entries))
    }

    private fun splitEntries(text: String): List<String> =
        text.split(ENTRY_SEPARATOR)
            .map { it.trim('﻿', '\r', '\n', ' ') }
            .filter { it.isNotBlank() }

    private fun parseEntry(entry: String): RawClipping? {
        val lines = entry.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null

        val title = lines[0].substringBefore(" (").trim().ifBlank { lines[0] }
        val metaLine = lines[1]

        val type = when {
            TYPE_HIGHLIGHT.containsMatchIn(metaLine) -> ClippingType.HIGHLIGHT
            TYPE_NOTE.containsMatchIn(metaLine) -> ClippingType.NOTE
            TYPE_BOOKMARK.containsMatchIn(metaLine) -> ClippingType.BOOKMARK
            else -> ClippingType.UNKNOWN
        }

        val locationMatch = LOCATION.find(metaLine)
        val locationStart = locationMatch?.groupValues?.get(1)?.toIntOrNull()
        val locationEnd = locationMatch?.groupValues?.get(2)?.toIntOrNull() ?: locationStart

        val content = lines.drop(2).joinToString(" ").trim()
        return RawClipping(title, type, locationStart, locationEnd, content)
    }

    private fun pairHighlightsWithNotes(entries: List<RawClipping>): List<CardDraft> {
        val drafts = mutableListOf<CardDraft>()
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            if (entry.type == ClippingType.HIGHLIGHT && entry.content.isNotBlank()) {
                val next = entries.getOrNull(i + 1)
                val pairedNote = next != null &&
                    next.type == ClippingType.NOTE &&
                    next.title == entry.title &&
                    next.content.isNotBlank() &&
                    locationsOverlap(entry, next)

                if (pairedNote) {
                    drafts.add(
                        CardDraft(
                            front = entry.content,
                            back = next!!.content,
                            source = ImportSource.KINDLE,
                            sourceLabel = entry.title,
                        ),
                    )
                    i += 2
                    continue
                }

                drafts.add(
                    CardDraft(
                        front = entry.content,
                        back = "",
                        source = ImportSource.KINDLE,
                        sourceLabel = entry.title,
                    ),
                )
            }
            i += 1
        }
        return drafts
    }

    private fun locationsOverlap(a: RawClipping, b: RawClipping): Boolean {
        val aStart = a.locationStart ?: return true
        val bStart = b.locationStart ?: return true
        val aEnd = a.locationEnd ?: aStart
        return bStart in (aStart - 2)..(aEnd + 2)
    }

    /** Kindle re-writes a clipping every time you re-highlight the same spot; keep the best copy. */
    private fun dedupe(drafts: List<CardDraft>): List<CardDraft> {
        val byFront = LinkedHashMap<String, CardDraft>()
        for (draft in drafts) {
            val key = draft.front.trim().lowercase()
            val existing = byFront[key]
            if (existing == null || (existing.back.isBlank() && draft.back.isNotBlank())) {
                byFront[key] = draft
            }
        }
        return byFront.values.toList()
    }
}
