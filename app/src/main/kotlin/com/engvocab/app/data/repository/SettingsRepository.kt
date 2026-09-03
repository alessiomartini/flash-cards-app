package com.engvocab.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "engvocab_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DESIRED_RETENTION = doublePreferencesKey("desired_retention")
        val AUTO_ENRICH_ENABLED = booleanPreferencesKey("auto_enrich_enabled")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val STUDY_MODE = stringPreferencesKey("study_mode")
        val CF_ACCOUNT_ID = stringPreferencesKey("cf_account_id")
        val CF_DATABASE_ID = stringPreferencesKey("cf_database_id")
        val CF_API_TOKEN = stringPreferencesKey("cf_api_token")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
    }

    /** Target recall probability the FSRS scheduler aims for (0.7-0.99). Higher = more, closer-together reviews. */
    val desiredRetention: Flow<Double> = context.dataStore.data.map { it[Keys.DESIRED_RETENTION] ?: 0.9 }

    /** Whether new/imported cards should be auto-completed via the dictionary + translation APIs. */
    val autoEnrichEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ENRICH_ENABLED] ?: true }

    /** The language currently being studied - scopes Home/Study/Cards and is the default for new cards. */
    val selectedLanguage: Flow<TargetLanguage> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_LANGUAGE]?.let { runCatching { TargetLanguage.valueOf(it) }.getOrNull() }
            ?: TargetLanguage.ENGLISH
    }

    suspend fun setDesiredRetention(value: Double) {
        context.dataStore.edit { it[Keys.DESIRED_RETENTION] = value.coerceIn(0.7, 0.99) }
    }

    suspend fun setAutoEnrichEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ENRICH_ENABLED] = value }
    }

    suspend fun setSelectedLanguage(language: TargetLanguage) {
        context.dataStore.edit { it[Keys.SELECTED_LANGUAGE] = language.name }
    }

    /** Which side of the card Study shows first - remembered so it doesn't reset every session. */
    val studyMode: Flow<StudyMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.STUDY_MODE]?.let { runCatching { StudyMode.valueOf(it) }.getOrNull() } ?: StudyMode.TERM_FIRST
    }

    suspend fun setStudyMode(mode: StudyMode) {
        context.dataStore.edit { it[Keys.STUDY_MODE] = mode.name }
    }

    /** Cloudflare account ID, D1 database UUID, and an API token with D1:Edit - set once in Settings. */
    val cloudflareAccountId: Flow<String> = context.dataStore.data.map { it[Keys.CF_ACCOUNT_ID] ?: "" }
    val cloudflareDatabaseId: Flow<String> = context.dataStore.data.map { it[Keys.CF_DATABASE_ID] ?: "" }
    val cloudflareApiToken: Flow<String> = context.dataStore.data.map { it[Keys.CF_API_TOKEN] ?: "" }

    /** Epoch millis of the last successful sync, or null if never synced. */
    val lastSyncedAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_SYNCED_AT] }

    suspend fun setCloudflareAccountId(value: String) {
        context.dataStore.edit { it[Keys.CF_ACCOUNT_ID] = value.trim() }
    }

    suspend fun setCloudflareDatabaseId(value: String) {
        context.dataStore.edit { it[Keys.CF_DATABASE_ID] = value.trim() }
    }

    suspend fun setCloudflareApiToken(value: String) {
        context.dataStore.edit { it[Keys.CF_API_TOKEN] = value.trim() }
    }

    suspend fun setLastSyncedAt(value: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNCED_AT] = value }
    }

    /** Whether the app should silently check for (and install) newer builds on its own. */
    val autoCheckForUpdates: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_CHECK_UPDATES] ?: true }

    /** Epoch millis of the last update check, or null if never checked - used to throttle auto-checks. */
    val lastUpdateCheckAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_UPDATE_CHECK_AT] }

    suspend fun setAutoCheckForUpdates(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = value }
    }

    suspend fun setLastUpdateCheckAt(value: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT] = value }
    }
}
