package com.engvocab.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE due <= :now ORDER BY due ASC")
    suspend fun getDue(now: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE due <= :now")
    suspend fun countDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM cards")
    fun observeTotalCount(): Flow<Int>

    @Query(
        "SELECT * FROM cards WHERE front LIKE '%' || :query || '%' OR back LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC",
    )
    fun search(query: String): Flow<List<CardEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM cards WHERE LOWER(front) = LOWER(:front))")
    suspend fun existsWithFront(front: String): Boolean
}
