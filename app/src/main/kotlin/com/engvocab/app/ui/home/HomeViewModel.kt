package com.engvocab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val reviewsToday: Int = 0,
    val streakDays: Int = 0,
    val isLoading: Boolean = true,
)

class HomeViewModel(private val cardRepository: CardRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val due = cardRepository.countDue()
            val total = cardRepository.observeTotalCount().first()
            val today = cardRepository.reviewsToday()
            val streak = cardRepository.currentStreakDays()
            _uiState.value = HomeUiState(
                dueCount = due,
                totalCount = total,
                reviewsToday = today,
                streakDays = streak,
                isLoading = false,
            )
        }
    }
}
