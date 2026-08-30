package com.engvocab.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.EnrichmentService
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.data.repository.SyncResult
import com.engvocab.app.data.sync.SyncOutcome
import com.engvocab.app.data.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AutoFillResult(val filled: Int, val stillMissing: Int)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastResult: SyncResult? = null,
    val errorMessage: String? = null,
    val isAutoFilling: Boolean = false,
    val autoFillDone: Int = 0,
    val autoFillTotal: Int = 0,
    val autoFillResult: AutoFillResult? = null,
) {
    val isBusy: Boolean get() = isSyncing || isAutoFilling
}

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val cardRepository: CardRepository,
    private val enrichmentService: EnrichmentService,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    val lastSyncedAt: StateFlow<Long?> = settingsRepository.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun syncNow() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null, lastResult = null) }
            when (val outcome = syncRepository.sync()) {
                is SyncOutcome.Success ->
                    _uiState.update { it.copy(isSyncing = false, lastResult = outcome.result) }
                is SyncOutcome.MissingCredentials ->
                    _uiState.update { it.copy(isSyncing = false, errorMessage = outcome.message) }
                is SyncOutcome.Failure ->
                    _uiState.update { it.copy(isSyncing = false, errorMessage = outcome.message) }
            }
        }
    }

    /**
     * Fills in the translation (and, when the free dictionary has one, a short example sentence
     * and definition) for every card in the selected language still missing a back - typically a
     * batch just pulled from a Duocards export, which never carries translations. Runs one card
     * at a time to respect the free translation API's rate limit; safe to stop and resume later.
     */
    fun fillMissingTranslations() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isAutoFilling = true, errorMessage = null, autoFillResult = null, autoFillDone = 0, autoFillTotal = 0)
            }
            val language = settingsRepository.selectedLanguage.first()
            val cards = cardRepository.cardsMissingTranslation(language)
            _uiState.update { it.copy(autoFillTotal = cards.size) }

            var filled = 0
            var stillMissing = 0
            for ((index, card) in cards.withIndex()) {
                val enrichment = enrichmentService.enrich(card.front, language)
                if (enrichment.translation.isNullOrBlank()) {
                    stillMissing++
                } else {
                    cardRepository.updateCard(
                        card.copy(
                            back = enrichment.translation,
                            definition = card.definition ?: enrichment.definition,
                            example = card.example ?: enrichment.example,
                            partOfSpeech = card.partOfSpeech ?: enrichment.partOfSpeech,
                        ),
                    )
                    filled++
                }
                _uiState.update { it.copy(autoFillDone = index + 1) }
            }

            _uiState.update {
                it.copy(isAutoFilling = false, autoFillResult = AutoFillResult(filled, stillMissing))
            }
        }
    }
}
