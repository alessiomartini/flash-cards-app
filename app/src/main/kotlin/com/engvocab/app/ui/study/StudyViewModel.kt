package com.engvocab.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.audio.AudioPlayer
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.core.model.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        loadQueue()
    }

    fun loadQueue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val language = settingsRepository.selectedLanguage.first()
            val due = cardRepository.getDueCards(language)
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
            playPronunciation()
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

    /** Plays the recorded pronunciation clip if the dictionary had one, otherwise speaks the front text. */
    fun playPronunciation() {
        val card = uiState.value.currentCard ?: return
        val audioUrl = card.audioUrl
        if (audioUrl != null) audioPlayer.play(audioUrl) else audioPlayer.speak(card.front, card.language.apiCode)
    }

    fun rate(rating: Rating) {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            cardRepository.reviewCard(card, rating)
            val nextIndex = uiState.value.currentIndex + 1
            val done = nextIndex >= uiState.value.queue.size
            _uiState.update { it.copy(currentIndex = nextIndex, isFlipped = false, isSessionComplete = done) }
            if (!done) {
                loadPreview()
                playPronunciation()
            }
        }
    }

    /** Deletes the card currently on screen and moves on to the next one in the queue, if any. */
    fun deleteCurrentCard() {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            cardRepository.deleteCard(card)
            val index = uiState.value.currentIndex
            val newQueue = uiState.value.queue.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            val done = index >= newQueue.size
            _uiState.update { it.copy(queue = newQueue, isFlipped = false, isSessionComplete = done) }
            if (!done) {
                loadPreview()
                playPronunciation()
            }
        }
    }

    /** Re-reads the current card from the DB - call after returning from editing it. */
    fun refreshCurrentCard() {
        val current = uiState.value.currentCard ?: return
        viewModelScope.launch {
            val refreshed = cardRepository.getCard(current.id) ?: return@launch
            val index = uiState.value.currentIndex
            _uiState.update { state ->
                val newQueue = state.queue.toMutableList()
                if (index in newQueue.indices) newQueue[index] = refreshed
                state.copy(queue = newQueue)
            }
            loadPreview()
            playPronunciation()
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
    }
}
