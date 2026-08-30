package com.engvocab.app.data.repository

import com.engvocab.core.fsrs.FsrsScheduler
import com.engvocab.core.importer.ImportSource
import com.engvocab.core.model.FsrsCardState
import com.engvocab.core.model.Rating
import com.engvocab.core.model.TargetLanguage
import com.engvocab.core.sync.RemoteWord
import com.engvocab.app.data.db.CardDao
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.db.CardType
import com.engvocab.app.data.db.ReviewLogDao
import com.engvocab.app.data.db.ReviewLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Outcome of one [CardRepository.applySync] pass, shown to the user on the Sync screen. */
data class SyncResult(val added: Int, val updated: Int, val removed: Int)

/** Wires the Room DAOs together with the FSRS scheduler; every card update goes through here. */
class CardRepository(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeAllCards(language: TargetLanguage): Flow<List<CardEntity>> = cardDao.observeAllByLanguage(language)

    fun observeTotalCount(language: TargetLanguage): Flow<Int> = cardDao.observeTotalCountByLanguage(language)

    fun searchCards(query: String, language: TargetLanguage): Flow<List<CardEntity>> =
        cardDao.searchByLanguage(query, language)

    suspend fun getDueCards(language: TargetLanguage, now: Long = System.currentTimeMillis()): List<CardEntity> =
        cardDao.getDueByLanguage(language, now)

    suspend fun countDue(language: TargetLanguage, now: Long = System.currentTimeMillis()): Int =
        cardDao.countDueByLanguage(language, now)

    suspend fun addCard(card: CardEntity): Long = cardDao.insert(card)

    suspend fun addCards(cards: List<CardEntity>): List<Long> = cardDao.insertAll(cards)

    suspend fun updateCard(card: CardEntity) = cardDao.update(card)

    suspend fun deleteCard(card: CardEntity) = cardDao.delete(card)

    suspend fun cardExists(front: String, language: TargetLanguage): Boolean =
        cardDao.existsWithFrontInLanguage(front, language)

    suspend fun getCard(id: Long): CardEntity? = cardDao.getById(id)

    private suspend fun scheduler(): FsrsScheduler =
        FsrsScheduler(desiredRetention = settingsRepository.desiredRetention.first())

    /** Predicted interval for each of the 4 ratings, for the "Again <10m · Good 3d · Easy 7d" preview row. */
    suspend fun previewIntervals(card: CardEntity, now: Long = System.currentTimeMillis()): Map<Rating, Long> =
        scheduler().previewIntervals(card.fsrs, now)

    /**
     * Starting FSRS state for an imported card. Cards flagged [knownAlready] (e.g. Duocards'
     * "fully learned" status) get two synthetic "Good" reviews applied - enough to walk a
     * brand-new card through both default learning steps into long-term review - so they
     * start scheduled like an already-mastered card instead of making the learner redo the
     * whole brand-new-card ramp-up.
     */
    suspend fun initialFsrsState(knownAlready: Boolean, now: Long = System.currentTimeMillis()): FsrsCardState {
        if (!knownAlready) return FsrsCardState()
        val fsrsScheduler = scheduler()
        val afterFirstGood = fsrsScheduler.review(FsrsCardState(), Rating.GOOD, now).card
        return fsrsScheduler.review(afterFirstGood, Rating.GOOD, now).card
    }

    suspend fun reviewCard(card: CardEntity, rating: Rating, now: Long = System.currentTimeMillis()): CardEntity {
        val result = scheduler().review(card.fsrs, rating, now)
        val updated = card.copy(fsrs = result.card)
        cardDao.update(updated)
        reviewLogDao.insert(
            ReviewLogEntity(
                cardId = card.id,
                rating = rating.value,
                reviewedAt = now,
                intervalMillis = result.intervalMillis,
            ),
        )
        return updated
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
                        fsrs = initialFsrsState(remote.isKnownAlready),
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
