package com.lexi.flashcards.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexi.flashcards.core.importer.CardDraft
import com.lexi.flashcards.core.importer.DelimitedTextParser
import com.lexi.flashcards.core.importer.ImportSource
import com.lexi.flashcards.core.importer.KindleClippingsParser
import com.lexi.flashcards.data.db.CardEntity
import com.lexi.flashcards.data.repository.CardRepository
import com.lexi.flashcards.data.repository.EnrichmentService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportFormat { DUOCARDS, KINDLE }

data class ImportRow(val draft: CardDraft, val included: Boolean = true)

data class ImportUiState(
    val format: ImportFormat = ImportFormat.DUOCARDS,
    val rows: List<ImportRow> = emptyList(),
    val isParsing: Boolean = false,
    val isImporting: Boolean = false,
    val enrichMissingBacks: Boolean = true,
    val importedCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val isDone: Boolean = false,
    val error: String? = null,
) {
    val includedCount: Int get() = rows.count { it.included }
}

class ImportViewModel(
    private val cardRepository: CardRepository,
    private val enrichmentService: EnrichmentService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun setFormat(format: ImportFormat) = _uiState.update { it.copy(format = format, rows = emptyList()) }

    fun setEnrichMissingBacks(value: Boolean) = _uiState.update { it.copy(enrichMissingBacks = value) }

    fun parse(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, error = null) }
            val drafts = when (uiState.value.format) {
                ImportFormat.DUOCARDS -> DelimitedTextParser.parseCards(text, ImportSource.DUOCARDS)
                ImportFormat.KINDLE -> KindleClippingsParser.parse(text)
            }
            _uiState.update {
                it.copy(
                    isParsing = false,
                    rows = drafts.map(::ImportRow),
                    error = if (drafts.isEmpty()) "Nessuna voce trovata in questo file." else null,
                )
            }
        }
    }

    fun toggleRow(index: Int) {
        _uiState.update { state ->
            val rows = state.rows.toMutableList()
            val row = rows.getOrNull(index) ?: return@update state
            rows[index] = row.copy(included = !row.included)
            state.copy(rows = rows)
        }
    }

    fun confirmImport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }

            val selected = uiState.value.rows.filter { it.included }
            val shouldEnrich = uiState.value.enrichMissingBacks
            val toInsert = mutableListOf<CardEntity>()
            var duplicates = 0

            for (row in selected) {
                var draft = row.draft
                if (shouldEnrich && draft.back.isBlank()) {
                    val enrichment = enrichmentService.enrich(draft.front)
                    draft = draft.copy(
                        back = enrichment.translation ?: draft.back,
                        example = draft.example ?: enrichment.example,
                    )
                }
                if (draft.back.isBlank()) continue
                if (cardRepository.cardExists(draft.front)) {
                    duplicates++
                    continue
                }
                toInsert.add(
                    CardEntity(
                        front = draft.front,
                        back = draft.back,
                        exampleEn = draft.example,
                        source = draft.source,
                        sourceLabel = draft.sourceLabel,
                    ),
                )
            }

            cardRepository.addCards(toInsert)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    isDone = true,
                    importedCount = toInsert.size,
                    skippedDuplicates = duplicates,
                )
            }
        }
    }
}
