package com.engvocab.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class D1ResponseParserTest {

    @Test
    fun `parses a typical successful query response`() {
        val json = """
            {
              "success": true,
              "errors": [],
              "messages": [],
              "result": [
                {
                  "success": true,
                  "meta": { "served_by": "v3-prod", "duration": 1.2, "rows_read": 2, "rows_written": 0 },
                  "results": [
                    {
                      "id": 1, "front": "ubiquitous", "back": "onnipresente", "language": "en",
                      "definition": null, "example": "Smartphones are ubiquitous.",
                      "part_of_speech": "adjective", "card_type": "WORD", "tags": "",
                      "source": "DUOCARDS", "source_label": null, "known_already": 1,
                      "is_deleted": 0, "created_at": 1000, "updated_at": 1000
                    },
                    {
                      "id": 2, "front": "hello", "back": "ciao", "language": "en",
                      "known_already": 0, "is_deleted": 0, "created_at": 2000, "updated_at": 2000
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val words = D1ResponseParser.parseWords(json)

        assertEquals(2, words.size)
        assertEquals("ubiquitous", words[0].front)
        assertTrue(words[0].isKnownAlready)
        assertEquals("hello", words[1].front)
        assertEquals(false, words[1].isKnownAlready)
        assertEquals(false, words[1].isSoftDeleted)
    }

    @Test
    fun `empty result set decodes to an empty list`() {
        val json = """{"success": true, "errors": [], "result": [{"success": true, "results": []}]}"""

        assertEquals(emptyList(), D1ResponseParser.parseWords(json))
    }

    @Test
    fun `throws with the API error message when success is false`() {
        val json = """
            {
              "success": false,
              "errors": [{ "code": 7403, "message": "Authentication error" }],
              "result": []
            }
        """.trimIndent()

        val exception = assertFailsWith<D1SyncException> { D1ResponseParser.parseWords(json) }
        assertTrue(exception.message!!.contains("Authentication error"))
    }

    @Test
    fun `throws instead of crashing on garbage input`() {
        assertFailsWith<D1SyncException> { D1ResponseParser.parseWords("not json") }
        assertFailsWith<D1SyncException> { D1ResponseParser.parseWords("") }
    }

    @Test
    fun `checkSuccess does not throw for a successful write response with no rows`() {
        val json = """
            {
              "success": true,
              "errors": [],
              "result": [{ "success": true, "meta": { "last_row_id": 42, "rows_written": 1 } }]
            }
        """.trimIndent()

        D1ResponseParser.checkSuccess(json)
    }

    @Test
    fun `checkSuccess throws with the API error message when success is false`() {
        val json = """
            {
              "success": false,
              "errors": [{ "code": 7500, "message": "SQL error" }],
              "result": []
            }
        """.trimIndent()

        val exception = assertFailsWith<D1SyncException> { D1ResponseParser.checkSuccess(json) }
        assertTrue(exception.message!!.contains("SQL error"))
    }
}
