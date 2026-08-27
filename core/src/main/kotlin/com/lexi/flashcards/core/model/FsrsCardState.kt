package com.lexi.flashcards.core.model

/**
 * The complete FSRS scheduling state for a single flashcard.
 *
 * All timestamps are epoch milliseconds (UTC). A freshly created card should use
 * [CardState.LEARNING] with `step = 0` and `stability`/`difficulty` left `null` -
 * the scheduler fills them in on the first review.
 */
data class FsrsCardState(
    val state: CardState = CardState.LEARNING,
    val step: Int? = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val due: Long = System.currentTimeMillis(),
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** The result of grading one review: the card's new scheduling state and a log entry. */
data class ReviewResult(
    val card: FsrsCardState,
    val reviewedAt: Long,
    val rating: Rating,
    /** Milliseconds until the card is next due, from [reviewedAt]. Useful for UI previews. */
    val intervalMillis: Long,
)
