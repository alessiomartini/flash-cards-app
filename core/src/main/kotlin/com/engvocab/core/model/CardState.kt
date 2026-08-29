package com.engvocab.core.model

/** The learning phase a card is currently in, as tracked by the FSRS scheduler. */
enum class CardState {
    LEARNING,
    REVIEW,
    RELEARNING,
}
