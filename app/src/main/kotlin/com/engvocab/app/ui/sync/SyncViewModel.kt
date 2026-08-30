package com.engvocab.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.data.repository.SyncResult
import com.engvocab.app.data.sync.SyncOutcome
import com.engvocab.app.data.sync.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastResult: SyncResult? = null,
    val errorMessage: String? = null,
)

class SyncViewModel(
    private val syncRepository: SyncRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    val lastSyncedAt: StateFlow<Long?> = settingsRepository.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun syncNow() {
        if (_uiState.value.isSyncing) return
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
}
