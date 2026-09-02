package com.engvocab.core.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreeDictionaryResponseParserTest {

    @Test
    fun `parses a typical success response`() {
        val json = """
            [
              {
                "word": "serendipity",
                "phonetic": "ˌsɛr.ənˈdɪp.ɪ.ti",
                "phonetics": [ { "text": "ˌsɛr.ənˈdɪp.ɪ.ti", "audio": "" } ],
                "meanings": [
                  {
                    "partOfSpeech": "noun",
                    "definitions": [
                      {
                        "definition": "A fortunate accident; luck that takes the form of finding valuable things not sought for.",
                        "example": "Finding this cafe was pure serendipity.",
                        "synonyms": [],
                        "antonyms": []
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = FreeDictionaryResponseParser.parse(json)

        assertEquals("serendipity", result?.word)
        assertEquals("ˌsɛr.ənˈdɪp.ɪ.ti", result?.phonetic)
        assertEquals("noun", result?.partOfSpeech)
        assertEquals(
            "A fortunate accident; luck that takes the form of finding valuable things not sought for.",
            result?.definition,
        )
        assertEquals("Finding this cafe was pure serendipity.", result?.example)
    }

    @Test
    fun `picks the first phonetics entry with a non-blank audio url`() {
        val json = """
            [
              {
                "word": "dismal",
                "phonetics": [
                  { "text": "/ˈdɪzməl/", "audio": "" },
                  { "text": "/ˈdɪzməl/", "audio": "https://api.dictionaryapi.dev/media/pronunciations/en/dismal-us.mp3" }
                ],
                "meanings": [ { "partOfSpeech": "adjective", "definitions": [ { "definition": "Causing gloom." } ] } ]
              }
            ]
        """.trimIndent()

        val result = FreeDictionaryResponseParser.parse(json)

        assertEquals("/ˈdɪzməl/", result?.phonetic)
        assertEquals("https://api.dictionaryapi.dev/media/pronunciations/en/dismal-us.mp3", result?.audioUrl)
    }

    @Test
    fun `adds https to a protocol-relative audio url`() {
        val json = """
            [
              {
                "word": "elephant",
                "phonetics": [
                  { "text": "/ˈɛlɪfənt/", "audio": "//ssl.gstatic.com/dictionary/static/sounds/oxford/elephant--_gb_1.mp3" }
                ],
                "meanings": [ { "partOfSpeech": "noun", "definitions": [ { "definition": "A large mammal." } ] } ]
              }
            ]
        """.trimIndent()

        val result = FreeDictionaryResponseParser.parse(json)

        assertEquals(
            "https://ssl.gstatic.com/dictionary/static/sounds/oxford/elephant--_gb_1.mp3",
            result?.audioUrl,
        )
    }

    @Test
    fun `returns null for the not-found error object`() {
        val json = """
            {
              "title": "No Definitions Found",
              "message": "Sorry pal, we couldn't find definitions for the word you were looking for.",
              "resolution": "You can try the search again at a later time or head to the web instead."
            }
        """.trimIndent()

        assertNull(FreeDictionaryResponseParser.parse(json))
    }

    @Test
    fun `skips a meaning that has no definitions and falls back to the next one`() {
        val json = """
            [
              {
                "word": "loophole",
                "meanings": [
                  { "partOfSpeech": "empty", "definitions": [] },
                  {
                    "partOfSpeech": "noun",
                    "definitions": [ { "definition": "An ambiguity that allows something to be avoided." } ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = FreeDictionaryResponseParser.parse(json)

        assertEquals("noun", result?.partOfSpeech)
        assertEquals("An ambiguity that allows something to be avoided.", result?.definition)
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        assertNull(FreeDictionaryResponseParser.parse("not json at all"))
        assertNull(FreeDictionaryResponseParser.parse(""))
        assertNull(FreeDictionaryResponseParser.parse("<html>502 Bad Gateway</html>"))
    }
}
