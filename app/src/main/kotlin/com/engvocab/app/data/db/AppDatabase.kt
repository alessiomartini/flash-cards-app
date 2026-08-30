package com.engvocab.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [CardEntity::class, ReviewLogEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "engvocab.db",
                )
                    // Pre-release app, no installed base with real data yet - simplest to reset
                    // on schema changes (v2 added multi-language support, v3 added remoteId for
                    // cloud sync) rather than write migrations for data nobody has yet.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
