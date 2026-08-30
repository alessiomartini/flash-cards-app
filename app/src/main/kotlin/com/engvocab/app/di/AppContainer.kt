package com.engvocab.app.di

import android.content.Context
import com.engvocab.app.data.db.AppDatabase
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.EnrichmentService
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.data.sync.SyncRepository

/** Minimal hand-rolled service locator - no DI framework needed for an app this size. */
class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val settingsRepository = SettingsRepository(context)
    val cardRepository = CardRepository(database.cardDao(), database.reviewLogDao(), settingsRepository)
    val enrichmentService = EnrichmentService()
    val syncRepository = SyncRepository(cardRepository, settingsRepository)
}
