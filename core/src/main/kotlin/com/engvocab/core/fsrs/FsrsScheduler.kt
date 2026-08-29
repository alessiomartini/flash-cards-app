package com.engvocab.core.fsrs

import com.engvocab.core.model.CardState
import com.engvocab.core.model.FsrsCardState
import com.engvocab.core.model.Rating
import com.engvocab.core.model.ReviewResult
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

private data class FuzzRange(val start: Double, val end: Double, val factor: Double)

private val FUZZ_RANGES = listOf(
    FuzzRange(2.5, 7.0, 0.15),
    FuzzRange(7.0, 20.0, 0.1),
    FuzzRange(20.0, Double.POSITIVE_INFINITY, 0.05),
)

/**
 * A faithful Kotlin port of the FSRS-6 (Free Spaced Repetition Scheduler) algorithm,
 * from https://github.com/open-spaced-repetition/py-fsrs (MIT licensed).
 *
 * FSRS models each card's memory as a "stability" (days until recall probability
 * drops to ~90%) and "difficulty", both updated after every review based on how
 * well the learner remembered it. Research from the Open Spaced Repetition project,
 * benchmarked on 700M+ real reviews, shows it needs 20-30% fewer reviews than the
 * older SM-2 algorithm (used by classic Anki/SuperMemo) for the same retention -
 * see https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm.
 *
 * This class is pure and side-effect free: [review] takes a card's current state
 * and returns a brand new state, it never mutates anything.
 */
class FsrsScheduler(
    private val parameters: DoubleArray = FSRS_DEFAULT_PARAMETERS,
    /** Target recall probability. 0.9 = review a card again once you're ~90% likely to still recall it. */
    val desiredRetention: Double = 0.9,
    /** Short sub-day steps a brand-new card walks through before entering long-term review. */
    private val learningSteps: List<Long> = listOf(60_000L, 600_000L),
    /** Short sub-day steps a forgotten review card walks through before returning to long-term review. */
    private val relearningSteps: List<Long> = listOf(600_000L),
    private val maximumIntervalDays: Int = 36_500,
    private val enableFuzzing: Boolean = true,
    private val random: Random = Random.Default,
) {
    private val decay = -parameters[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    /** Predicted probability (0..1) that [card] can still be recalled right now. */
    fun getRetrievability(card: FsrsCardState, now: Long = System.currentTimeMillis()): Double {
        val lastReview = card.lastReview ?: return 0.0
        val stability = card.stability ?: return 0.0
        val elapsedDays = max(0L, (now - lastReview) / MILLIS_PER_DAY)
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    /** Grades [card] with [rating] and returns its new scheduling state. Does not mutate [card]. */
    fun review(card: FsrsCardState, rating: Rating, now: Long = System.currentTimeMillis()): ReviewResult {
        val daysSinceLastReview = card.lastReview?.let { max(0L, (now - it)) / MILLIS_PER_DAY }

        var state = card.state
        var step = card.step
        var stability = card.stability
        var difficulty = card.difficulty
        var intervalMillis: Long

        when (card.state) {
            CardState.LEARNING -> {
                val currentStep = requireNotNull(step) { "Learning-state card must have a step" }

                if (stability == null || difficulty == null) {
                    stability = initialStability(rating)
                    difficulty = initialDifficulty(rating, clamp = true)
                } else if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability, rating)
                    difficulty = nextDifficulty(difficulty, rating)
                } else {
                    stability = nextStability(difficulty, stability, getRetrievability(card, now), rating)
                    difficulty = nextDifficulty(difficulty, rating)
                }

                if (learningSteps.isEmpty() ||
                    (currentStep >= learningSteps.size && rating != Rating.AGAIN)
                ) {
                    state = CardState.REVIEW
                    step = null
                    intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            intervalMillis = learningSteps[0]
                        }
                        Rating.HARD -> {
                            intervalMillis = when {
                                currentStep == 0 && learningSteps.size == 1 -> (learningSteps[0] * 1.5).toLong()
                                currentStep == 0 && learningSteps.size >= 2 -> (learningSteps[0] + learningSteps[1]) / 2
                                else -> learningSteps[currentStep]
                            }
                        }
                        Rating.GOOD -> {
                            if (currentStep + 1 == learningSteps.size) {
                                state = CardState.REVIEW
                                step = null
                                intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                            } else {
                                step = currentStep + 1
                                intervalMillis = learningSteps[step]
                            }
                        }
                        Rating.EASY -> {
                            state = CardState.REVIEW
                            step = null
                            intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                        }
                    }
                }
            }

            CardState.REVIEW -> {
                val currentStability = requireNotNull(stability) { "Review-state card must have a stability" }
                val currentDifficulty = requireNotNull(difficulty) { "Review-state card must have a difficulty" }

                stability = if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    shortTermStability(currentStability, rating)
                } else {
                    nextStability(currentDifficulty, currentStability, getRetrievability(card, now), rating)
                }
                difficulty = nextDifficulty(currentDifficulty, rating)

                when (rating) {
                    Rating.AGAIN -> {
                        if (relearningSteps.isEmpty()) {
                            intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                        } else {
                            state = CardState.RELEARNING
                            step = 0
                            intervalMillis = relearningSteps[0]
                        }
                    }
                    Rating.HARD, Rating.GOOD, Rating.EASY -> {
                        intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                    }
                }
            }

            CardState.RELEARNING -> {
                val currentStep = requireNotNull(step) { "Relearning-state card must have a step" }
                val currentStability = requireNotNull(stability) { "Relearning-state card must have a stability" }
                val currentDifficulty = requireNotNull(difficulty) { "Relearning-state card must have a difficulty" }

                if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(currentStability, rating)
                    difficulty = nextDifficulty(currentDifficulty, rating)
                } else {
                    stability = nextStability(currentDifficulty, currentStability, getRetrievability(card, now), rating)
                    difficulty = nextDifficulty(currentDifficulty, rating)
                }

                if (relearningSteps.isEmpty() ||
                    (currentStep >= relearningSteps.size && rating != Rating.AGAIN)
                ) {
                    state = CardState.REVIEW
                    step = null
                    intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            intervalMillis = relearningSteps[0]
                        }
                        Rating.HARD -> {
                            intervalMillis = when {
                                currentStep == 0 && relearningSteps.size == 1 -> (relearningSteps[0] * 1.5).toLong()
                                currentStep == 0 && relearningSteps.size >= 2 -> (relearningSteps[0] + relearningSteps[1]) / 2
                                else -> relearningSteps[currentStep]
                            }
                        }
                        Rating.GOOD -> {
                            if (currentStep + 1 == relearningSteps.size) {
                                state = CardState.REVIEW
                                step = null
                                intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                            } else {
                                step = currentStep + 1
                                intervalMillis = relearningSteps[step]
                            }
                        }
                        Rating.EASY -> {
                            state = CardState.REVIEW
                            step = null
                            intervalMillis = nextIntervalDays(stability) * MILLIS_PER_DAY
                        }
                    }
                }
            }
        }

        if (enableFuzzing && state == CardState.REVIEW) {
            intervalMillis = fuzzedIntervalMillis(intervalMillis)
        }

        val lapses = if (card.state == CardState.REVIEW && rating == Rating.AGAIN) card.lapses + 1 else card.lapses

        val newCard = FsrsCardState(
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            due = now + intervalMillis,
            lastReview = now,
            reps = card.reps + 1,
            lapses = lapses,
        )

        return ReviewResult(card = newCard, reviewedAt = now, rating = rating, intervalMillis = intervalMillis)
    }

    /** Convenience for the UI: shows the resulting interval for each of the 4 ratings without persisting anything. */
    fun previewIntervals(card: FsrsCardState, now: Long = System.currentTimeMillis()): Map<Rating, Long> =
        Rating.entries.associateWith { review(card, it, now).intervalMillis }

    private fun clampStability(stability: Double) = max(stability, STABILITY_MIN)

    private fun clampDifficulty(difficulty: Double) = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun initialStability(rating: Rating): Double =
        clampStability(parameters[rating.value - 1])

    private fun initialDifficulty(rating: Rating, clamp: Boolean): Double {
        val value = parameters[4] - exp(parameters[5] * (rating.value - 1)) + 1
        return if (clamp) clampDifficulty(value) else value
    }

    private fun nextIntervalDays(stability: Double): Long {
        val raw = (stability / factor) * (desiredRetention.pow(1.0 / decay) - 1.0)
        var days = Math.round(raw)
        days = max(days, 1L)
        days = min(days, maximumIntervalDays.toLong())
        return days
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        val increaseRaw = exp(parameters[17] * (rating.value - 3 + parameters[18])) * stability.pow(-parameters[19])
        val increase = if (rating != Rating.AGAIN) max(increaseRaw, 1.0) else increaseRaw
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val deltaDifficulty = -(parameters[6] * (rating.value - 3))
        val linearDamping = (10.0 - difficulty) * deltaDifficulty / 9.0
        val arg1 = initialDifficulty(Rating.EASY, clamp = false)
        val arg2 = difficulty + linearDamping
        val meanReversion = parameters[7] * arg1 + (1 - parameters[7]) * arg2
        return clampDifficulty(meanReversion)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val value = if (rating == Rating.AGAIN) {
            nextForgetStability(difficulty, stability, retrievability)
        } else {
            nextRecallStability(difficulty, stability, retrievability, rating)
        }
        return clampStability(value)
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val longTerm = parameters[11] *
            difficulty.pow(-parameters[12]) *
            (((stability + 1).pow(parameters[13])) - 1) *
            exp((1 - retrievability) * parameters[14])
        val shortTerm = stability / exp(parameters[17] * parameters[18])
        return min(longTerm, shortTerm)
    }

    private fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0
        return stability * (
            1 +
                exp(parameters[8]) *
                (11 - difficulty) *
                stability.pow(-parameters[9]) *
                (exp((1 - retrievability) * parameters[10]) - 1) *
                hardPenalty *
                easyBonus
            )
    }

    private fun fuzzedIntervalMillis(intervalMillis: Long): Long {
        val days = intervalMillis / MILLIS_PER_DAY
        if (days < 2.5) return intervalMillis

        var delta = 1.0
        for (range in FUZZ_RANGES) {
            delta += range.factor * max(min(days.toDouble(), range.end) - range.start, 0.0)
        }

        var minIvl = Math.round(days - delta)
        var maxIvl = Math.round(days + delta)
        minIvl = max(2L, minIvl)
        maxIvl = min(maxIvl, maximumIntervalDays.toLong())
        minIvl = min(minIvl, maxIvl)

        val fuzzedDays = (random.nextDouble() * (maxIvl - minIvl + 1)) + minIvl
        val resultDays = min(Math.round(fuzzedDays), maximumIntervalDays.toLong())
        return resultDays * MILLIS_PER_DAY
    }
}
