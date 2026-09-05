package com.engvocab.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.model.FsrsCardState
import com.engvocab.core.model.TargetLanguage

/**
 * A single flashcard, persisted with its full FSRS scheduling state embedded directly
 * (see [FsrsCardState]) so the scheduler can be handed the row as-is on every review.
 *
 * [fsrs] (term -> meaning) is the only direction a new card starts with. [fsrsMeaningFirst] and
 * [fsrsListening] stay null - "not unlocked yet" - until [fsrs] first graduates into
 * [com.engvocab.core.model.CardState.REVIEW], at which point
 * [com.engvocab.app.data.repository.CardRepository.reviewCard] seeds both from it (see there for
 * why). Each then keeps its own independent schedule: knowing a word one way doesn't mean
 * knowing it another way equally well, but they aren't unrelated either.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val front: String,
    val back: String,
    /** The language [front] is written in - what deck/study-session this card belongs to. */
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    val definition: String? = null,
    val example: String? = null,
    val partOfSpeech: String? = null,
    /** IPA transcription, e.g. "/ˈdɪzməl/" - from the free dictionary lookup. */
    val phonetic: String? = null,
    /** URL of a pronunciation audio clip (mp3), when the dictionary provides one. */
    val audioUrl: String? = null,
    val cardType: CardType = CardType.WORD,
    /** Comma-separated tags, e.g. "kindle:Atomic Habits,business". */
    val tags: String = "",
    val source: ImportSource = ImportSource.MANUAL,
    val sourceLabel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** The D1 `words.id` this card was synced from, or null for a card added manually on the phone. */
    val remoteId: Long? = null,
    @Embedded val fsrs: FsrsCardState = FsrsCardState(),
    @Embedded(prefix = "meaning_") val fsrsMeaningFirst: FsrsCardState? = null,
    @Embedded(prefix = "listening_") val fsrsListening: FsrsCardState? = null,
) {
    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

fun List<String>.toTagsColumn(): String = joinToString(",") { it.trim() }.trim(',')
