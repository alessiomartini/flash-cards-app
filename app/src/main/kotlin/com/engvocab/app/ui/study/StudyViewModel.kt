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
    val canUndo: Boolean = false,
) {
    val currentCard: CardEntity? get() = queue.getOrNull(currentIndex)
    val remaining: Int get() = (queue.size - currentIndex).coerceAtLeast(0)
}

/** Enough to restore the last rated card and its review log entry - see [StudyViewModel.undoLastRating]. */
private data class PendingUndo(val previousCard: CardEntity, val logId: Long, val queueIndex: Int)

class StudyViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    /** The most recently rated card, kept only until the next rating/delete/reload - one level of undo. */
    private var pendingUndo: PendingUndo? = null

    init {
        loadQueue()
    }

    fun loadQueue() {
        viewModelScope.launch {
            pendingUndo = null
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
                    canUndo = false,
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

    /** Speaks the current card's front text aloud via text-to-speech. */
    fun playPronunciation() {
        val card = uiState.value.currentCard ?: return
        audioPlayer.speak(card.front, card.language.apiCode)
    }

    fun rate(rating: Rating) {
        val card = uiState.value.currentCard ?: return
        val index = uiState.value.currentIndex
        viewModelScope.launch {
            audioPlayer.stop()
            val outcome = cardRepository.reviewCard(card, rating)
            pendingUndo = PendingUndo(previousCard = card, logId = outcome.logId, queueIndex = index)
            val nextIndex = index + 1
            val done = nextIndex >= uiState.value.queue.size
            _uiState.update { it.copy(currentIndex = nextIndex, isFlipped = false, isSessionComplete = done, canUndo = true) }
            if (!done) {
                loadPreview()
                playPronunciation()
            }
        }
    }

    /** Undoes the last [rate] call: restores the card's pre-review state and its review log entry. */
    fun undoLastRating() {
        val pending = pendingUndo ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            cardRepository.undoReview(pending.previousCard, pending.logId)
            pendingUndo = null
            _uiState.update { state ->
                val newQueue = state.queue.toMutableList()
                if (pending.queueIndex in newQueue.indices) newQueue[pending.queueIndex] = pending.previousCard
                state.copy(
                    queue = newQueue,
                    currentIndex = pending.queueIndex,
                    // Flipped, not flip=false: the point of undo is to re-pick a rating for a
                    // card the learner already saw the answer to, not to quiz them again.
                    isFlipped = true,
                    isSessionComplete = false,
                    canUndo = false,
                )
            }
            // Stays flipped (see above), so no auto-play here - that only happens for a
            // freshly-shown, unflipped front, per rate()/deleteCurrentCard().
            loadPreview()
        }
    }

    /** Deletes the card currently on screen and moves on to the next one in the queue, if any. */
    fun deleteCurrentCard() {
        val card = uiState.value.currentCard ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            // Removing an item shifts every later index, which would make pendingUndo's
            // stored queueIndex point at the wrong card - simplest to just drop it.
            pendingUndo = null
            cardRepository.deleteCard(card)
            val index = uiState.value.currentIndex
            val newQueue = uiState.value.queue.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            val done = index >= newQueue.size
            _uiState.update { it.copy(queue = newQueue, isFlipped = false, isSessionComplete = done, canUndo = false) }
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
