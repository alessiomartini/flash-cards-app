package com.lexi.flashcards.core.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KindleClippingsParserTest {

    @Test
    fun `pairs an English highlight with its following note`() {
        val text = """
            Sample Book Title (Author Name)
            - Your Highlight on page 12 | location 245-245 | Added on Monday, January 5, 2026 8:32:10 AM

            serendipity

            ==========
            Sample Book Title (Author Name)
            - Your Note on page 12 | location 245 | Added on Monday, January 5, 2026 8:32:45 AM

            fortunata coincidenza

            ==========
        """.trimIndent()

        val cards = KindleClippingsParser.parse(text)

        assertEquals(1, cards.size)
        assertEquals("serendipity", cards[0].front)
        assertEquals("fortunata coincidenza", cards[0].back)
        assertEquals(ImportSource.KINDLE, cards[0].source)
        assertEquals("Sample Book Title", cards[0].sourceLabel)
    }

    @Test
    fun `pairs an Italian-locale highlight and nota`() {
        val text = """
            Un Altro Libro (Autore)
            - La tua evidenziazione a pagina 5 | posizione 88-88 | Aggiunto il 5 gennaio 2026 10:00:00

            bittersweet

            ==========
            Un Altro Libro (Autore)
            - La tua nota a pagina 5 | posizione 88 | Aggiunto il 5 gennaio 2026 10:00:30

            agrodolce

            ==========
        """.trimIndent()

        val cards = KindleClippingsParser.parse(text)

        assertEquals(1, cards.size)
        assertEquals("bittersweet", cards[0].front)
        assertEquals("agrodolce", cards[0].back)
    }

    @Test
    fun `a highlight with no note becomes a card with an empty back`() {
        val text = """
            Sample Book Title (Author Name)
            - Your Highlight on page 20 | location 300-300 | Added on Monday, January 5, 2026 9:00:00 AM

            ubiquitous

            ==========
        """.trimIndent()

        val cards = KindleClippingsParser.parse(text)

        assertEquals(1, cards.size)
        assertEquals("ubiquitous", cards[0].front)
        assertEquals("", cards[0].back)
    }

    @Test
    fun `bookmarks are ignored`() {
        val text = """
            Sample Book Title (Author Name)
            - Your Bookmark on page 25 | location 350 | Added on Monday, January 5, 2026 9:10:00 AM

            ==========
        """.trimIndent()

        assertTrue(KindleClippingsParser.parse(text).isEmpty())
    }

    @Test
    fun `a note with no preceding highlight is dropped`() {
        val text = """
            Sample Book Title (Author Name)
            - Your Note on page 1 | location 1 | Added on Monday, January 5, 2026 9:10:00 AM

            orphan note, no highlight before it

            ==========
        """.trimIndent()

        assertTrue(KindleClippingsParser.parse(text).isEmpty())
    }

    @Test
    fun `re-highlighting the same word later keeps the copy that has a note`() {
        val text = """
            Sample Book Title (Author Name)
            - Your Highlight on page 12 | location 245-245 | Added on Monday, January 5, 2026 8:32:10 AM

            serendipity

            ==========
            Sample Book Title (Author Name)
            - Your Highlight on page 400 | location 5000-5000 | Added on Tuesday, January 6, 2026 8:00:00 AM

            Serendipity

            ==========
            Sample Book Title (Author Name)
            - Your Note on page 400 | location 5000 | Added on Tuesday, January 6, 2026 8:00:30 AM

            fortuitous discovery

            ==========
        """.trimIndent()

        val cards = KindleClippingsParser.parse(text)

        assertEquals(1, cards.size)
        assertEquals("Serendipity", cards[0].front)
        assertEquals("fortuitous discovery", cards[0].back)
    }

    @Test
    fun `blank input yields no cards`() {
        assertTrue(KindleClippingsParser.parse("").isEmpty())
    }
}
