package com.engvocab.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.audio.AudioPlayer
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.app.data.repository.StudyMode
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
    /** The chosen setting - what the mode chips show as selected. Persisted. */
    val mode: StudyMode = StudyMode.MIXED,
    /**
     * How the current card is actually being presented - always one of TERM_FIRST/MEANING_FIRST/
     * LISTENING, never MIXED, and always one of that card's *due* directions (a not-yet-unlocked
     * or not-yet-due direction can't be tested even if [mode] asks for it - see
     * [StudyViewModel.pickActiveMode]). Equal to [mode] when [mode] is a due concrete direction;
     * otherwise a pick among the due ones, held fixed until the card changes so it doesn't
     * shuffle under the learner mid-answer.
     */
    val activeMode: StudyMode = StudyMode.TERM_FIRST,
) {
    val currentCard: CardEntity? get() = queue.getOrNull(currentIndex)
    val remaining: Int get() = (queue.size - currentIndex).coerceAtLeast(0)
}

/** Enough to restore the last rated card, its review log entry, and how it was presented - see [StudyViewModel.undoLastRating]. */
private data class PendingUndo(
    val previousCard: CardEntity,
    val logId: Long,
    val queueIndex: Int,
    val previousActiveMode: StudyMode,
)

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
            val mode = settingsRepository.studyMode.first()
            val due = cardRepository.getDueCards(language)
            _uiState.update {
                it.copy(
                    queue = due,
                    currentIndex = 0,
                    isFlipped = false,
                    isLoading = false,
                    isSessionComplete = due.isEmpty(),
                    canUndo = false,
                    mode = mode,
                    activeMode = due.firstOrNull()?.let { card -> pickActiveMode(card, mode) } ?: StudyMode.TERM_FIRST,
                )
            }
            loadPreview()
            autoPlayPronunciation()
        }
    }

    /** Switches which side Study shows first, persisting the choice for next time. Re-picks the current card's presentation right away. */
    fun setMode(mode: StudyMode) {
        _uiState.update { state ->
            state.copy(mode = mode, activeMode = state.currentCard?.let { pickActiveMode(it, mode) } ?: state.activeMode)
        }
        viewModelScope.launch { settingsRepository.setStudyMode(mode) }
    }

    /**
     * A concrete presentation for [card] right now: [mode] itself if it's one of that card's
     * currently-due directions, otherwise (including whenever [mode] is MIXED) a random pick
     * among whichever directions actually are due - a not-yet-unlocked or not-yet-due direction
     * can't be tested no matter what's selected, see [CardEntity]'s own doc for why.
     */
    private fun pickActiveMode(card: CardEntity, mode: StudyMode): StudyMode {
        val due = cardRepository.dueDirections(card)
        if (mode in due) return mode
        return due.randomOrNull() ?: StudyMode.TERM_FIRST
    }

    private fun loadPreview() {
        val card = uiState.value.currentCard ?: return
        val mode = uiState.value.activeMode
        viewModelScope.launch {
            val preview = cardRepository.previewIntervals(card, mode)
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

    /**
     * Auto-play on a freshly-shown, unflipped card - skipped in [StudyMode.MEANING_FIRST], where
     * hearing the term pronounced would just hand over the answer before the user's tried to
     * produce it from the Italian meaning.
     */
    private fun autoPlayPronunciation() {
        if (uiState.value.activeMode != StudyMode.MEANING_FIRST) playPronunciation()
    }

    fun rate(rating: Rating) {
        val card = uiState.value.currentCard ?: return
        val index = uiState.value.currentIndex
        val activeModeForCard = uiState.value.activeMode
        viewModelScope.launch {
            audioPlayer.stop()
            val outcome = cardRepository.reviewCard(card, rating, activeModeForCard)
            pendingUndo = PendingUndo(previousCard = card, logId = outcome.logId, queueIndex = index, previousActiveMode = activeModeForCard)
            val nextIndex = index + 1
            val done = nextIndex >= uiState.value.queue.size
            _uiState.update { state ->
                val nextCard = state.queue.getOrNull(nextIndex)
                state.copy(
                    currentIndex = nextIndex,
                    isFlipped = false,
                    isSessionComplete = done,
                    canUndo = true,
                    activeMode = nextCard?.let { pickActiveMode(it, state.mode) } ?: state.activeMode,
                )
            }
            if (!done) {
                loadPreview()
                autoPlayPronunciation()
            }
        }
    }

    /** Undoes the last [rate] call: restores the card's pre-review state, its review log entry, and how it was presented. */
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
                    activeMode = pending.previousActiveMode,
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
            _uiState.update { state ->
                val nextCard = newQueue.getOrNull(index)
                state.copy(
                    queue = newQueue,
                    isFlipped = false,
                    isSessionComplete = done,
                    canUndo = false,
                    activeMode = nextCard?.let { pickActiveMode(it, state.mode) } ?: state.activeMode,
                )
            }
            if (!done) {
                loadPreview()
                autoPlayPronunciation()
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
            autoPlayPronunciation()
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
    }
}
