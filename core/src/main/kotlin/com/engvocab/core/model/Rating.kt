package com.engvocab.core.model

/**
 * The four recall-quality ratings a learner can give when reviewing a card,
 * matching the FSRS (Free Spaced Repetition Scheduler) algorithm's rating scale.
 */
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
}
