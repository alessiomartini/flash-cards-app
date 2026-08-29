package com.engvocab.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.SettingsRepository
import com.engvocab.core.model.TargetLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CardListViewModel(
    private val cardRepository: CardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    val language: StateFlow<TargetLanguage> = settingsRepository.selectedLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TargetLanguage.ENGLISH)

    val cards: StateFlow<List<CardEntity>> = combine(
        query.debounce(200),
        settingsRepository.selectedLanguage,
    ) { q, language -> q to language }
        .flatMapLatest { (q, language) ->
            if (q.isBlank()) cardRepository.observeAllCards(language) else cardRepository.searchCards(q, language)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }

    fun setLanguage(language: TargetLanguage) {
        viewModelScope.launch { settingsRepository.setSelectedLanguage(language) }
    }

    fun delete(card: CardEntity) {
        viewModelScope.launch { cardRepository.deleteCard(card) }
    }
}
