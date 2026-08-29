package com.engvocab.core.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class DelimitedTextParserTest {

    @Test
    fun `parses comma-separated export with header row`() {
        val text = """
            front,back
            serendipity,fortunata coincidenza
            "to hit the sack",andare a dormire
        """.trimIndent()

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)

        assertEquals(2, cards.size)
        assertEquals(CardDraft("serendipity", "fortunata coincidenza", null, ImportSource.DUOCARDS), cards[0])
        assertEquals(CardDraft("to hit the sack", "andare a dormire", null, ImportSource.DUOCARDS), cards[1])
    }

    @Test
    fun `parses tab-separated export without header and with an example column`() {
        val text = "ubiquitous\tonnipresente\tSmartphones are ubiquitous nowadays.\n" +
            "loophole\tscappatoia\t\n"

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)

        assertEquals(2, cards.size)
        assertEquals("ubiquitous", cards[0].front)
        assertEquals("onnipresente", cards[0].back)
        assertEquals("Smartphones are ubiquitous nowadays.", cards[0].example)
        assertEquals("loophole", cards[1].front)
        assertEquals(null, cards[1].example)
    }

    @Test
    fun `handles quoted fields containing the delimiter itself`() {
        val text = "front,back\n\"hi, there\",\"ciao, amico\"\n"

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)

        assertEquals(1, cards.size)
        assertEquals("hi, there", cards[0].front)
        assertEquals("ciao, amico", cards[0].back)
    }

    @Test
    fun `skips rows missing a back column`() {
        val text = "front;back\nword only;\nfull;pair\n"

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)

        assertEquals(1, cards.size)
        assertEquals("full", cards[0].front)
    }

    @Test
    fun `blank input yields no cards`() {
        assertEquals(emptyList(), DelimitedTextParser.parseCards("", ImportSource.DUOCARDS))
        assertEquals(emptyList(), DelimitedTextParser.parseCards("   \n  \n", ImportSource.DUOCARDS))
    }
}
