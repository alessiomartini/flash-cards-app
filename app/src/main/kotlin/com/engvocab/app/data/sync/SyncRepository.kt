package com.engvocab.app.data.sync

import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.data.repository.SyncResult
import com.engvocab.core.sync.D1Client
import com.engvocab.core.sync.D1Credentials
import com.engvocab.core.sync.D1SyncException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface SyncOutcome {
    data class Success(val result: SyncResult) : SyncOutcome
    data class MissingCredentials(val message: String) : SyncOutcome
    data class Failure(val message: String) : SyncOutcome
}

/** Pulls the online vocabulary (Cloudflare D1) down into the local Room database. Never writes to D1. */
class SyncRepository(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun sync(): SyncOutcome {
        val accountId = settingsRepository.cloudflareAccountId.first()
        val databaseId = settingsRepository.cloudflareDatabaseId.first()
        val apiToken = settingsRepository.cloudflareApiToken.first()
        if (accountId.isBlank() || databaseId.isBlank() || apiToken.isBlank()) {
            return SyncOutcome.MissingCredentials(
                "Add your Cloudflare account ID, database ID, and API token in Settings first.",
            )
        }

        return try {
            val remoteWords = withContext(Dispatchers.IO) {
                D1Client(D1Credentials(accountId, databaseId, apiToken)).fetchAllWords()
            }
            val result = cardRepository.applySync(remoteWords)
            settingsRepository.setLastSyncedAt(System.currentTimeMillis())
            SyncOutcome.Success(result)
        } catch (e: D1SyncException) {
            SyncOutcome.Failure(e.message ?: "Sync failed")
        } catch (e: IOException) {
            SyncOutcome.Failure("Network error: ${e.message}")
        }
    }
}
