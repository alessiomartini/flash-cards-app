package com.engvocab.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v3 -> v4: added phonetic transcription + pronunciation audio URL, both purely additive. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cards ADD COLUMN phonetic TEXT")
        db.execSQL("ALTER TABLE cards ADD COLUMN audioUrl TEXT")
    }
}

/**
 * v4 -> v5: added a second and third FSRS schedule per card (meaning->term, listening), both
 * fully nullable columns - null means "not unlocked yet" for every existing card, exactly the
 * pre-migration behavior (term->meaning only). See CardEntity's own doc for why.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (prefix in listOf("meaning_", "listening_")) {
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}state TEXT")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}step INTEGER")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}stability REAL")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}difficulty REAL")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}due INTEGER")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}lastReview INTEGER")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}reps INTEGER")
            db.execSQL("ALTER TABLE cards ADD COLUMN ${prefix}lapses INTEGER")
        }
    }
}

@Database(entities = [CardEntity::class, ReviewLogEntity::class], version = 5, exportSchema = false)
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // Fallback only covers schema changes older than v3 -> v4, from before
                    // there was a real installed base with cloud-synced vocabulary and FSRS
                    // progress worth preserving; new schema changes should get a real migration.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
