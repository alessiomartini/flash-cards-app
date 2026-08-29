package com.engvocab.core.fsrs

/**
 * Default model weights for FSRS-6, as published by the Open Spaced Repetition
 * project (https://github.com/open-spaced-repetition/py-fsrs, MIT licensed) and
 * fitted on hundreds of millions of real Anki reviews. Ported verbatim - do not
 * "simplify" these numbers, they are the trained model.
 *
 * Index 20 is the decay constant. Indices 0-3 are per-rating initial stability;
 * see [FsrsScheduler] for how each weight is used.
 */
val FSRS_DEFAULT_DECAY = 0.1542

val FSRS_DEFAULT_PARAMETERS: DoubleArray = doubleArrayOf(
    0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
    1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
    1.8729, 0.5425, 0.0912, 0.0658, FSRS_DEFAULT_DECAY,
)

const val STABILITY_MIN = 0.001
const val INITIAL_STABILITY_MAX = 100.0
const val MIN_DIFFICULTY = 1.0
const val MAX_DIFFICULTY = 10.0
