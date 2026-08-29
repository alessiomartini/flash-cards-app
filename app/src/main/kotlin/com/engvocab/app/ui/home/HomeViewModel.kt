package com.engvocab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val reviewsToday: Int = 0,
    val streakDays: Int = 0,
    val isLoading: Boolean = true,
)

class HomeViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.selectedLanguage.collect { language -> refresh(language) }
        }
    }

    fun refresh() {
        viewModelScope.launch { refresh(settingsRepository.selectedLanguage.first()) }
    }

    fun setLanguage(language: TargetLanguage) {
        viewModelScope.launch { settingsRepository.setSelectedLanguage(language) }
    }

    private suspend fun refresh(language: TargetLanguage) {
        val due = cardRepository.countDue(language)
        val total = cardRepository.observeTotalCount(language).first()
        val today = cardRepository.reviewsToday()
        val streak = cardRepository.currentStreakDays()
        _uiState.value = HomeUiState(
            language = language,
            dueCount = due,
            totalCount = total,
            reviewsToday = today,
            streakDays = streak,
            isLoading = false,
        )
    }
}
