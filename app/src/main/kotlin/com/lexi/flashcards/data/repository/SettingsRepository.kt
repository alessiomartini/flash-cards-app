package com.lexi.flashcards.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lexi_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DESIRED_RETENTION = doublePreferencesKey("desired_retention")
        val AUTO_ENRICH_ENABLED = booleanPreferencesKey("auto_enrich_enabled")
    }

    /** Target recall probability the FSRS scheduler aims for (0.7-0.99). Higher = more, closer-together reviews. */
    val desiredRetention: Flow<Double> = context.dataStore.data.map { it[Keys.DESIRED_RETENTION] ?: 0.9 }

    /** Whether new/imported cards should be auto-completed via the dictionary + translation APIs. */
    val autoEnrichEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ENRICH_ENABLED] ?: true }

    suspend fun setDesiredRetention(value: Double) {
        context.dataStore.edit { it[Keys.DESIRED_RETENTION] = value.coerceIn(0.7, 0.99) }
    }

    suspend fun setAutoEnrichEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ENRICH_ENABLED] = value }
    }
}
