package com.lexi.flashcards.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lexi.flashcards.core.importer.ImportSource
import com.lexi.flashcards.core.model.FsrsCardState

/**
 * A single flashcard, persisted with its full FSRS scheduling state embedded directly
 * (see [FsrsCardState]) so the scheduler can be handed the row as-is on every review.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val front: String,
    val back: String,
    val definitionEn: String? = null,
    val exampleEn: String? = null,
    val partOfSpeech: String? = null,
    val cardType: CardType = CardType.WORD,
    /** Comma-separated tags, e.g. "kindle:Atomic Habits,business". */
    val tags: String = "",
    val source: ImportSource = ImportSource.MANUAL,
    val sourceLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @Embedded val fsrs: FsrsCardState = FsrsCardState(),
) {
    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

fun List<String>.toTagsColumn(): String = joinToString(",") { it.trim() }.trim(',')
