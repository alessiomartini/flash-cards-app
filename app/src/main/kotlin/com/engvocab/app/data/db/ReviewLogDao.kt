package com.engvocab.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity): Long

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :sinceEpochMs")
    suspend fun countSince(sinceEpochMs: Long): Int

    /** One entry per calendar day (local time zone) that had at least one review - used for streaks. */
    @Query("SELECT DISTINCT date(reviewedAt / 1000, 'unixepoch', 'localtime') FROM review_logs ORDER BY 1 DESC")
    suspend fun distinctReviewDates(): List<String>
}
