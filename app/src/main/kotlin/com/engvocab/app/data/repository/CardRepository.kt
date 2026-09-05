package com.engvocab.app.data.repository

import com.engvocab.core.fsrs.FsrsScheduler
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.model.CardState
import com.engvocab.core.model.FsrsCardState
import com.engvocab.core.model.Rating
import com.engvocab.core.model.TargetLanguage
import com.engvocab.core.sync.D1Client
import com.engvocab.core.sync.D1Credentials
import com.engvocab.core.sync.RemoteWord
import com.engvocab.app.data.db.CardDao
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.db.CardType
import com.engvocab.app.data.db.ReviewLogDao
import com.engvocab.app.data.db.ReviewLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Outcome of one [CardRepository.applySync] pass, shown to the user on the Sync screen. */
data class SyncResult(val added: Int, val updated: Int, val removed: Int)

/** Outcome of one [CardRepository.reviewCard] call - enough to undo it via [CardRepository.undoReview]. */
data class ReviewOutcome(val updatedCard: CardEntity, val logId: Long)

/**
 * A just-unlocked secondary direction starts at this fraction of the primary direction's
 * demonstrated stability - a deliberate, tunable product choice (FSRS itself has no notion of
 * cross-direction transfer), not starting from zero but not full credit either.
 */
private const val SECONDARY_DIRECTION_STABILITY_FACTOR = 0.5

private fun CardEntity.fsrsFor(mode: StudyMode): FsrsCardState? = when (mode) {
    StudyMode.TERM_FIRST -> fsrs
    StudyMode.MEANING_FIRST -> fsrsMeaningFirst
    StudyMode.LISTENING -> fsrsListening
    StudyMode.MIXED -> null // not a real direction - callers always resolve MIXED before calling
}

private fun CardEntity.withFsrsFor(mode: StudyMode, newState: FsrsCardState): CardEntity = when (mode) {
    StudyMode.TERM_FIRST -> copy(fsrs = newState)
    StudyMode.MEANING_FIRST -> copy(fsrsMeaningFirst = newState)
    StudyMode.LISTENING -> copy(fsrsListening = newState)
    StudyMode.MIXED -> this
}

/** Wires the Room DAOs together with the FSRS scheduler; every card update goes through here. */
class CardRepository(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val settingsRepository: SettingsRepository,
) {
    // Outlives any single ViewModel, so a push started right before the user navigates away
    // (clearing that ViewModel's own scope) still gets a chance to finish.
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeAllCards(language: TargetLanguage): Flow<List<CardEntity>> = cardDao.observeAllByLanguage(language)

    fun observeTotalCount(language: TargetLanguage): Flow<Int> = cardDao.observeTotalCountByLanguage(language)

    fun searchCards(query: String, language: TargetLanguage): Flow<List<CardEntity>> =
        cardDao.searchByLanguage(query, language)

    suspend fun getDueCards(language: TargetLanguage, now: Long = System.currentTimeMillis()): List<CardEntity> =
        cardDao.getDueByLanguage(language, now)

    suspend fun countDue(language: TargetLanguage, now: Long = System.currentTimeMillis()): Int =
        cardDao.countDueByLanguage(language, now)

    suspend fun addCard(card: CardEntity): Long {
        val id = cardDao.insert(card)
        pushEventBestEffort { it.insertCardAddEvent(card.language.apiCode, System.currentTimeMillis()) }
        return id
    }

    suspend fun addCards(cards: List<CardEntity>): List<Long> = cardDao.insertAll(cards)

    suspend fun updateCard(card: CardEntity) = cardDao.update(card)

    suspend fun deleteCard(card: CardEntity) = cardDao.delete(card)

    suspend fun cardExists(front: String, language: TargetLanguage): Boolean =
        cardDao.existsWithFrontInLanguage(front, language)

    suspend fun getCard(id: Long): CardEntity? = cardDao.getById(id)

    /** Cards in [language] still missing a translation - the bulk auto-fill target on the Sync screen. */
    suspend fun cardsMissingTranslation(language: TargetLanguage): List<CardEntity> =
        cardDao.getCardsWithBlankBack(language)

    /** Cards in [language] with a translation but no phonetic/audio yet - the other bulk auto-fill target. */
    suspend fun cardsMissingPronunciation(language: TargetLanguage): List<CardEntity> =
        cardDao.getCardsMissingPronunciation(language)

    private suspend fun scheduler(): FsrsScheduler =
        FsrsScheduler(desiredRetention = settingsRepository.desiredRetention.first())

    /** Predicted interval for each of the 4 ratings, for the "Again <10m · Good 3d · Easy 7d" preview row. */
    suspend fun previewIntervals(card: CardEntity, mode: StudyMode, now: Long = System.currentTimeMillis()): Map<Rating, Long> =
        scheduler().previewIntervals(card.fsrsFor(mode) ?: card.fsrs, now)

    /** Which of [card]'s unlocked directions are due right now - never empty for a card the due-cards query returned. */
    fun dueDirections(card: CardEntity, now: Long = System.currentTimeMillis()): List<StudyMode> = buildList {
        if (card.fsrs.due <= now) add(StudyMode.TERM_FIRST)
        card.fsrsMeaningFirst?.takeIf { it.due <= now }?.let { add(StudyMode.MEANING_FIRST) }
        card.fsrsListening?.takeIf { it.due <= now }?.let { add(StudyMode.LISTENING) }
    }

    private data class InitialFsrsStates(val termFirst: FsrsCardState, val meaningFirst: FsrsCardState?, val listening: FsrsCardState?)

    /**
     * Starting FSRS state(s) for an imported card. Cards flagged [knownAlready] (e.g. Duocards'
     * "fully learned" status) get two synthetic "Good" reviews applied to the primary direction -
     * enough to walk a brand-new card through both default learning steps into long-term review -
     * so it starts scheduled like an already-mastered card instead of making the learner redo the
     * whole brand-new-card ramp-up. That graduates it into REVIEW exactly like an organic pass
     * would, so the other two directions unlock immediately too, seeded the normal way.
     */
    private suspend fun initialFsrsStates(knownAlready: Boolean, now: Long = System.currentTimeMillis()): InitialFsrsStates {
        if (!knownAlready) return InitialFsrsStates(FsrsCardState(), null, null)
        val fsrsScheduler = scheduler()
        val afterFirstGood = fsrsScheduler.review(FsrsCardState(), Rating.GOOD, now).card
        val termFirst = fsrsScheduler.review(afterFirstGood, Rating.GOOD, now).card
        return InitialFsrsStates(termFirst, unlockSecondaryDirection(termFirst, now), unlockSecondaryDirection(termFirst, now))
    }

    /**
     * Starting point for a direction the moment it unlocks: not a brand-new card (no
     * relearning-steps ramp-up) but not full credit either - [SECONDARY_DIRECTION_STABILITY_FACTOR]
     * of [primary]'s demonstrated stability, same difficulty (which reflects the word itself more
     * than the direction being tested), due immediately so it joins the Mixed rotation right away.
     */
    private fun unlockSecondaryDirection(primary: FsrsCardState, now: Long): FsrsCardState {
        val primaryStability = checkNotNull(primary.stability) { "A graduated (REVIEW) card must have a stability" }
        return FsrsCardState(
            state = CardState.REVIEW,
            step = null,
            stability = primaryStability * SECONDARY_DIRECTION_STABILITY_FACTOR,
            difficulty = primary.difficulty,
            due = now,
            lastReview = now,
            reps = 0,
            lapses = 0,
        )
    }

    suspend fun reviewCard(card: CardEntity, rating: Rating, mode: StudyMode, now: Long = System.currentTimeMillis()): ReviewOutcome {
        val result = scheduler().review(card.fsrsFor(mode) ?: card.fsrs, rating, now)
        var updated = card.withFsrsFor(mode, result.card)

        // The first time the primary direction graduates out of initial learning, unlock the
        // other two so Mixed can start drilling this word both ways - see unlockSecondaryDirection.
        if (mode == StudyMode.TERM_FIRST && result.card.state == CardState.REVIEW && card.fsrsMeaningFirst == null) {
            updated = updated.copy(
                fsrsMeaningFirst = unlockSecondaryDirection(result.card, now),
                fsrsListening = unlockSecondaryDirection(result.card, now),
            )
        }

        cardDao.update(updated)
        val logId = reviewLogDao.insert(
            ReviewLogEntity(
                cardId = card.id,
                rating = rating.value,
                reviewedAt = now,
                intervalMillis = result.intervalMillis,
            ),
        )
        pushEventBestEffort { it.insertReviewEvent(card.language.apiCode, rating.value, now) }
        return ReviewOutcome(updated, logId)
    }

    /**
     * Fire-and-forget push to the stats site's D1 tables (see [D1Client.insertReviewEvent]/
     * [D1Client.insertCardAddEvent]). Silently does nothing without Cloudflare credentials in
     * Settings, and silently swallows any network/API failure - this is analytics, not sync,
     * and must never slow down or fail a review or a card save.
     */
    private fun pushEventBestEffort(action: (D1Client) -> Unit) {
        eventScope.launch {
            val accountId = settingsRepository.cloudflareAccountId.first()
            val databaseId = settingsRepository.cloudflareDatabaseId.first()
            val apiToken = settingsRepository.cloudflareApiToken.first()
            if (accountId.isBlank() || databaseId.isBlank() || apiToken.isBlank()) return@launch
            runCatching { action(D1Client(D1Credentials(accountId, databaseId, apiToken))) }
        }
    }

    /**
     * Reverts one [reviewCard] call: restores the card to its pre-review state and removes the
     * log entry it created, so streaks/reviews-today don't keep counting an undone review.
     */
    suspend fun undoReview(previousCard: CardEntity, logId: Long) {
        cardDao.update(previousCard)
        reviewLogDao.deleteById(logId)
    }

    /**
     * Pull-sync from the online D1 vocabulary: upserts every remote row by [RemoteWord.id]
     * (matched against [CardEntity.remoteId]), preserving each card's local FSRS progress on
     * update, and deletes local cards whose remote row is gone (soft-deleted or hard-deleted -
     * [remoteWords] only ever contains non-deleted rows, see the D1 query). Cards added manually
     * on the phone (remoteId == null) are never touched - the cloud is one-directional, cloud -> phone.
     */
    suspend fun applySync(remoteWords: List<RemoteWord>): SyncResult {
        var added = 0
        var updated = 0
        var removed = 0

        val remoteIds = remoteWords.mapTo(HashSet()) { it.id }

        for (remote in remoteWords) {
            val language = TargetLanguage.entries.find { it.apiCode == remote.language } ?: continue
            val existing = cardDao.getByRemoteId(remote.id)
            if (existing == null) {
                val initial = initialFsrsStates(remote.isKnownAlready)
                cardDao.insert(
                    CardEntity(
                        front = remote.front,
                        back = remote.back,
                        language = language,
                        definition = remote.definition,
                        example = remote.example,
                        partOfSpeech = remote.partOfSpeech,
                        cardType = runCatching { CardType.valueOf(remote.cardType) }.getOrDefault(CardType.WORD),
                        tags = remote.tags,
                        source = runCatching { ImportSource.valueOf(remote.source) }.getOrDefault(ImportSource.MANUAL),
                        sourceLabel = remote.sourceLabel,
                        remoteId = remote.id,
                        fsrs = initial.termFirst,
                        fsrsMeaningFirst = initial.meaningFirst,
                        fsrsListening = initial.listening,
                    ),
                )
                added++
            } else {
                val refreshed = existing.copy(
                    front = remote.front,
                    back = remote.back,
                    language = language,
                    definition = remote.definition,
                    example = remote.example,
                    partOfSpeech = remote.partOfSpeech,
                    tags = remote.tags,
                    sourceLabel = remote.sourceLabel,
                )
                if (refreshed != existing) {
                    cardDao.update(refreshed)
                    updated++
                }
            }
        }

        val locallyRemoved = cardDao.getAllWithRemoteId().filter { it.remoteId !in remoteIds }
        for (card in locallyRemoved) {
            cardDao.delete(card)
            removed++
        }

        return SyncResult(added, updated, removed)
    }

    suspend fun reviewsToday(): Int = reviewLogDao.countSince(startOfToday())

    /** Consecutive days (including today, if not yet reviewed) with at least one review - Duolingo-style. */
    suspend fun currentStreakDays(): Int {
        val reviewedDates = reviewLogDao.distinctReviewDates().toHashSet()
        if (reviewedDates.isEmpty()) return 0

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cursor = Calendar.getInstance()
        if (dayFormat.format(cursor.time) !in reviewedDates) {
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }

        var streak = 0
        while (dayFormat.format(cursor.time) in reviewedDates) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
