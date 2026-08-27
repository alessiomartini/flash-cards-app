package com.lexi.flashcards.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexi.flashcards.core.model.Rating
import com.lexi.flashcards.data.db.CardEntity
import com.lexi.flashcards.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val queue: List<CardEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val isSessionComplete: Boolean = false,
    val previewIntervals: Map<Rating, Long> = emptyMap(),
) {
    val currentCard: CardEntity? get() = queue.getOrNull(currentIndex)
    val remaining: Int get() = (queue.size - currentIndex).coerceAtLeast(0)
}

class StudyViewModel(private val cardRepository: CardRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        loadQueue()
    }

    fun loadQueue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val due = cardRepository.getDueCards()
            _uiState.update {
                it.copy(
                    queue = due,
                    currentIndex = 0,
                    isFlipped = false,
                    isLoading = false,
                    isSessionComplete = due.isEmpty(),
                )
            }
            loadPreview()
        }
    }

    private fun loadPreview() {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val preview = cardRepository.previewIntervals(card)
            _uiState.update { it.copy(previewIntervals = preview) }
        }
    }

    fun flip() {
        _uiState.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun rate(rating: Rating) {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch {
            cardRepository.reviewCard(card, rating)
            val nextIndex = uiState.value.currentIndex + 1
            val done = nextIndex >= uiState.value.queue.size
            _uiState.update { it.copy(currentIndex = nextIndex, isFlipped = false, isSessionComplete = done) }
            if (!done) loadPreview()
        }
    }
}
