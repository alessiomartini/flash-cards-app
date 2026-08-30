package com.engvocab.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: CardEntity): Long

    @Insert
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun update(card: CardEntity)

    @Delete
    suspend fun delete(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE language = :language ORDER BY createdAt DESC")
    fun observeAllByLanguage(language: TargetLanguage): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE language = :language AND due <= :now ORDER BY due ASC")
    suspend fun getDueByLanguage(language: TargetLanguage, now: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE language = :language AND due <= :now")
    suspend fun countDueByLanguage(language: TargetLanguage, now: Long): Int

    @Query("SELECT COUNT(*) FROM cards WHERE language = :language")
    fun observeTotalCountByLanguage(language: TargetLanguage): Flow<Int>

    @Query(
        "SELECT * FROM cards WHERE language = :language " +
            "AND (front LIKE '%' || :query || '%' OR back LIKE '%' || :query || '%') " +
            "ORDER BY createdAt DESC",
    )
    fun searchByLanguage(query: String, language: TargetLanguage): Flow<List<CardEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM cards WHERE language = :language AND LOWER(front) = LOWER(:front))")
    suspend fun existsWithFrontInLanguage(front: String, language: TargetLanguage): Boolean

    @Query("SELECT * FROM cards WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Long): CardEntity?

    /** Every card synced from D1 (i.e. not added manually on the phone) - used to detect cloud-side deletions. */
    @Query("SELECT * FROM cards WHERE remoteId IS NOT NULL")
    suspend fun getAllWithRemoteId(): List<CardEntity>

    /** Cards still missing a translation - e.g. freshly synced from a Duocards export with no back column. */
    @Query("SELECT * FROM cards WHERE language = :language AND back = ''")
    suspend fun getCardsWithBlankBack(language: TargetLanguage): List<CardEntity>
}
