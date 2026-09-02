package com.engvocab.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.audio.AudioPlayer
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.db.CardType
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.EnrichmentService
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditUiState(
    val front: String = "",
    val back: String = "",
    val language: TargetLanguage = TargetLanguage.ENGLISH,
    val definition: String = "",
    val example: String = "",
    val partOfSpeech: String = "",
    val phonetic: String = "",
    val audioUrl: String? = null,
    val cardType: CardType = CardType.WORD,
    val tags: String = "",
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isEnriching: Boolean = false,
    val isSaved: Boolean = false,
) {
    val canSave: Boolean get() = front.isNotBlank() && back.isNotBlank()
}

class AddEditCardViewModel(
    private val cardRepository: CardRepository,
    private val enrichmentService: EnrichmentService,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: AudioPlayer,
    private val cardId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEditUiState(isNew = cardId == null, isLoading = cardId != null))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var loadedCard: CardEntity? = null

    init {
        viewModelScope.launch {
            if (cardId != null) {
                val card = cardRepository.getCard(cardId)
                loadedCard = card
                if (card != null) {
                    _uiState.value = AddEditUiState(
                        front = card.front,
                        back = card.back,
                        language = card.language,
                        definition = card.definition.orEmpty(),
                        example = card.example.orEmpty(),
                        partOfSpeech = card.partOfSpeech.orEmpty(),
                        phonetic = card.phonetic.orEmpty(),
                        audioUrl = card.audioUrl,
                        cardType = card.cardType,
                        tags = card.tags,
                        isNew = false,
                        isLoading = false,
                    )
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } else {
                val defaultLanguage = settingsRepository.selectedLanguage.first()
                _uiState.update { it.copy(language = defaultLanguage) }
            }
        }
    }

    fun onFrontChange(value: String) = _uiState.update { it.copy(front = value) }
    fun onBackChange(value: String) = _uiState.update { it.copy(back = value) }
    fun onLanguageChange(value: TargetLanguage) = _uiState.update { it.copy(language = value) }
    fun onDefinitionChange(value: String) = _uiState.update { it.copy(definition = value) }
    fun onExampleChange(value: String) = _uiState.update { it.copy(example = value) }
    fun onPartOfSpeechChange(value: String) = _uiState.update { it.copy(partOfSpeech = value) }
    fun onPhoneticChange(value: String) = _uiState.update { it.copy(phonetic = value) }
    fun onCardTypeChange(value: CardType) = _uiState.update { it.copy(cardType = value) }
    fun onTagsChange(value: String) = _uiState.update { it.copy(tags = value) }

    fun autoComplete() {
        val front = uiState.value.front.trim()
        if (front.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEnriching = true) }
            val enrichment = enrichmentService.enrich(front, uiState.value.language)
            _uiState.update {
                it.copy(
                    isEnriching = false,
                    back = it.back.ifBlank { enrichment.translation.orEmpty() },
                    definition = it.definition.ifBlank { enrichment.definition.orEmpty() },
                    example = it.example.ifBlank { enrichment.example.orEmpty() },
                    partOfSpeech = it.partOfSpeech.ifBlank { enrichment.partOfSpeech.orEmpty() },
                    phonetic = it.phonetic.ifBlank { enrichment.phonetic.orEmpty() },
                    audioUrl = it.audioUrl ?: enrichment.audioUrl,
                )
            }
        }
    }

    /** Plays the recorded pronunciation clip if the dictionary had one, otherwise speaks the front text. */
    fun playPronunciation() {
        val state = uiState.value
        val audioUrl = state.audioUrl
        if (audioUrl != null) audioPlayer.play(audioUrl) else audioPlayer.speak(state.front, state.language.apiCode)
    }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val base = loadedCard ?: CardEntity(front = state.front, back = state.back)
            val toSave = base.copy(
                front = state.front.trim(),
                back = state.back.trim(),
                language = state.language,
                definition = state.definition.trim().takeIf { it.isNotEmpty() },
                example = state.example.trim().takeIf { it.isNotEmpty() },
                partOfSpeech = state.partOfSpeech.trim().takeIf { it.isNotEmpty() },
                phonetic = state.phonetic.trim().takeIf { it.isNotEmpty() },
                audioUrl = state.audioUrl,
                cardType = state.cardType,
                tags = state.tags.trim(),
            )
            if (loadedCard == null) cardRepository.addCard(toSave) else cardRepository.updateCard(toSave)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
    }
}
