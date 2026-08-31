package com.engvocab.core.icon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordIconMatcherTest {

    @Test
    fun `matches a known idiom by its full phrase, ignoring literal words`() {
        val icon = WordIconMatcher.match("Spill the beans", definition = null, example = null, partOfSpeech = null)
        assertEquals("🫘", icon)
    }

    @Test
    fun `idiom match is case and punctuation insensitive`() {
        val icon = WordIconMatcher.match("  SPILL THE BEANS.  ", definition = null, example = null, partOfSpeech = null)
        assertEquals("🫘", icon)
    }

    @Test
    fun `falls back to a keyword match in the term when no idiom matches`() {
        val icon = WordIconMatcher.match("a coconut", definition = null, example = null, partOfSpeech = null)
        assertEquals("🥥", icon)
    }

    @Test
    fun `falls back to a keyword match in the definition when the term has none`() {
        val icon = WordIconMatcher.match(
            "mutt",
            definition = "informal term for a dog of mixed breed",
            example = null,
            partOfSpeech = null,
        )
        assertEquals("🐶", icon)
    }

    @Test
    fun `falls back to a keyword match in the example as a last resort`() {
        val icon = WordIconMatcher.match(
            "peculiar",
            definition = "strange or odd",
            example = "The dog behaved in a peculiar way.",
            partOfSpeech = null,
        )
        assertEquals("🐶", icon)
    }

    @Test
    fun `falls back to a part-of-speech icon when no keyword matches anything`() {
        assertEquals("🏃", WordIconMatcher.match("ponder", null, null, "verb"))
        assertEquals("📦", WordIconMatcher.match("ponder", null, null, "noun"))
        assertEquals("🎨", WordIconMatcher.match("ponder", null, null, "adjective"))
        assertEquals("💫", WordIconMatcher.match("ponder", null, null, "adverb"))
    }

    @Test
    fun `returns null when nothing matches at all`() {
        assertNull(WordIconMatcher.match("ponder", null, null, null))
        assertNull(WordIconMatcher.match("ponder", null, null, "preposition"))
    }
}
