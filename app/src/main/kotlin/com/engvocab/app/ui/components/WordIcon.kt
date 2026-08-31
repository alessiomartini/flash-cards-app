package com.engvocab.app.ui.components

import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.db.CardType
import com.engvocab.core.icon.WordIconMatcher

private val CARD_TYPE_ICON = mapOf(
    CardType.WORD to "🔤",
    CardType.PHRASAL_VERB to "🔗",
    CardType.IDIOM to "💭",
    CardType.EXPRESSION to "💬",
)

/** A mnemonic icon for this card - matched to its meaning when possible, or a generic fallback. */
fun CardEntity.icon(): String =
    WordIconMatcher.match(front, definition, example, partOfSpeech) ?: CARD_TYPE_ICON.getValue(cardType)
