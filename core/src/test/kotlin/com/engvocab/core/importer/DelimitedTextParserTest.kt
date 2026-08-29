package com.engvocab.core.importer

import com.engvocab.core.model.TargetLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `real Duocards word-list export has no translation column, only a level`() {
        val text = """
            Parola;Livello
            slow-moving;In apprendimento
            drive a hard bargain;In apprendimento
            to live the life;Imparata completamente
            traffic;Imparata completamente
        """.trimIndent()

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS, TargetLanguage.ENGLISH)

        assertEquals(4, cards.size)
        cards.forEach { assertEquals("", it.back) }

        assertEquals("slow-moving", cards[0].front)
        assertEquals(false, cards[0].knownAlready)
        assertEquals("drive a hard bargain", cards[1].front)
        assertEquals(false, cards[1].knownAlready)
        assertEquals("to live the life", cards[2].front)
        assertTrue(cards[2].knownAlready)
        assertEquals("traffic", cards[3].front)
        assertTrue(cards[3].knownAlready)
    }

    @Test
    fun `word-list export tags every card with the requested language`() {
        val text = "Parola;Livello\nder Tisch;Imparata completamente\n"

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS, TargetLanguage.GERMAN)

        assertEquals(1, cards.size)
        assertEquals(TargetLanguage.GERMAN, cards[0].language)
    }

    @Test
    fun `recognizes an English-locale level header too`() {
        val text = "Word;Status\nubiquitous;Learning\nresilient;Mastered\n"

        val cards = DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)

        assertEquals(2, cards.size)
        assertEquals(false, cards[0].knownAlready)
        assertTrue(cards[1].knownAlready)
    }
}
