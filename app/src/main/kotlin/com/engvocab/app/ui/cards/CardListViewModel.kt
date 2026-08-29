package com.engvocab.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.repository.CardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CardListViewModel(private val cardRepository: CardRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    val cards: StateFlow<List<CardEntity>> = query
        .debounce(200)
        .flatMapLatest { q -> if (q.isBlank()) cardRepository.observeAllCards() else cardRepository.searchCards(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }

    fun delete(card: CardEntity) {
        viewModelScope.launch { cardRepository.deleteCard(card) }
    }
}
