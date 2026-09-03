package com.engvocab.app.data.repository

/**
 * Which side of a card is shown before flipping, and whether its pronunciation auto-plays with
 * it. Purely a Study-screen display choice - the same due queue is studied in every mode, and it
 * has no effect on FSRS scheduling.
 */
enum class StudyMode {
    /** See the target-language term (+ pronunciation), recall the Italian meaning. */
    TERM_FIRST,

    /** See the Italian meaning, recall/produce the target-language term - no audio until flipped, so it can't give the answer away. */
    MEANING_FIRST,

    /** Hear the target-language term's pronunciation with no text shown at all, recall the Italian meaning. */
    LISTENING,

    /**
     * Rolls a fresh, random pick from the three modes above each time a new card is shown, so
     * the same vocabulary gets drilled in every direction instead of always the same one. The
     * default: knowing a word one-directionally (e.g. only recognizing it in writing) is a much
     * weaker memory than being able to go both ways and recognize it by ear.
     */
    MIXED,
}
