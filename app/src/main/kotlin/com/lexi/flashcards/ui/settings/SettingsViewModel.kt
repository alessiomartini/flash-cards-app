package com.lexi.flashcards.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexi.flashcards.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val desiredRetention: StateFlow<Double> = settingsRepository.desiredRetention
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.9)

    val autoEnrichEnabled: StateFlow<Boolean> = settingsRepository.autoEnrichEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setDesiredRetention(value: Double) {
        viewModelScope.launch { settingsRepository.setDesiredRetention(value) }
    }

    fun setAutoEnrichEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoEnrichEnabled(value) }
    }
}
