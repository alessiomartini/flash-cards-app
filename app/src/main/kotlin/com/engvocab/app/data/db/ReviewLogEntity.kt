package com.engvocab.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One graded review, kept for stats (streaks, reviews-per-day, retention charts). */
@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cardId"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    /** Rating.value: 1=Again, 2=Hard, 3=Good, 4=Easy. */
    val rating: Int,
    val reviewedAt: Long,
    val intervalMillis: Long,
)
