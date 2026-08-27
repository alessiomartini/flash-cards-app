package com.lexi.flashcards.di

import android.content.Context
import com.lexi.flashcards.data.db.AppDatabase
import com.lexi.flashcards.data.repository.CardRepository
import com.lexi.flashcards.data.repository.EnrichmentService
import com.lexi.flashcards.data.repository.SettingsRepository

/** Minimal hand-rolled service locator - no DI framework needed for an app this size. */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val settingsRepository = SettingsRepository(context)
    val cardRepository = CardRepository(database.cardDao(), database.reviewLogDao(), settingsRepository)
    val enrichmentService = EnrichmentService()
}
