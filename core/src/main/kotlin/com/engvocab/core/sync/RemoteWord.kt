package com.engvocab.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A row of the `words` table in the Cloudflare D1 database - the online vocabulary store the
 * user uploads to / edits from a computer (via the Cloudflare dashboard or the `:cli` tool).
 * Field names match the D1 column names (snake_case) since this is decoded directly from the
 * D1 REST API's JSON response. SQLite/D1 has no boolean type, so booleans round-trip as 0/1.
 */
@Serializable
data class RemoteWord(
    val id: Long = 0,
    val front: String,
    val back: String,
    val language: String,
    val definition: String? = null,
    val example: String? = null,
    @SerialName("part_of_speech") val partOfSpeech: String? = null,
    @SerialName("card_type") val cardType: String = "WORD",
    val tags: String = "",
    val source: String = "MANUAL",
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("known_already") val knownAlready: Long = 0,
    @SerialName("is_deleted") val isDeleted: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    val isKnownAlready: Boolean get() = knownAlready != 0L
    val isSoftDeleted: Boolean get() = isDeleted != 0L
}
