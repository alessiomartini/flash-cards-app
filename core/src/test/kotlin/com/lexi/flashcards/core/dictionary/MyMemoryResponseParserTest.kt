package com.lexi.flashcards.core.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyMemoryResponseParserTest {

    @Test
    fun `parses a typical translation response`() {
        val json = """
            {
              "responseData": { "translatedText": "Ciao", "match": 1 },
              "quotaFinished": false,
              "responseStatus": 200,
              "matches": [
                {
                  "id": "0", "segment": "hello", "translation": "Ciao",
                  "source": "en-GB", "target": "it-IT", "quality": "74",
                  "reference": null, "usage-count": 15, "subject": "All",
                  "created-by": "MateCat", "match": 1
                }
              ]
            }
        """.trimIndent()

        assertEquals("Ciao", MyMemoryResponseParser.parse(json))
    }

    @Test
    fun `returns null when the daily quota warning is returned as the translation`() {
        val json = """
            {
              "responseData": {
                "translatedText": "MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY",
                "match": 0
              },
              "responseStatus": 200
            }
        """.trimIndent()

        assertNull(MyMemoryResponseParser.parse(json))
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        assertNull(MyMemoryResponseParser.parse("not json"))
        assertNull(MyMemoryResponseParser.parse(""))
        assertNull(MyMemoryResponseParser.parse("{}"))
    }
}
