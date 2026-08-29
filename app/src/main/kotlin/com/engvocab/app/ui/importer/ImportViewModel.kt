package com.engvocab.app.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engvocab.app.data.db.CardEntity
import com.engvocab.app.data.repository.CardRepository
import com.engvocab.app.data.repository.EnrichmentService
import com.engvocab.app.data.staged.StagedImportReader
import com.engvocab.core.importer.CardDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportRow(val draft: CardDraft, val included: Boolean = true)

/**
 * Import has no in-app file picker: cards arrive from the computer-side `:cli` tool via
 * `adb push` into the app's external files directory (see [StagedImportReader]). This
 * screen just reviews/confirms whatever is staged there.
 */
data class ImportUiState(
    val rows: List<ImportRow> = emptyList(),
    val hasCheckedForStaged: Boolean = false,
    val isImporting: Boolean = false,
    val enrichMissingBacks: Boolean = true,
    val importedCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val isDone: Boolean = false,
) {
    val includedCount: Int get() = rows.count { it.included }
}

class ImportViewModel(
    private val cardRepository: CardRepository,
    private val enrichmentService: EnrichmentService,
    private val stagedImportReader: StagedImportReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        checkForStagedImport()
    }

    /** Re-reads the staged file - call when returning to this screen in case one just arrived. */
    fun checkForStagedImport() {
        val drafts = stagedImportReader.read().orEmpty()
        _uiState.update {
            it.copy(rows = drafts.map(::ImportRow), hasCheckedForStaged = true, isDone = false)
        }
    }

    fun setEnrichMissingBacks(value: Boolean) = _uiState.update { it.copy(enrichMissingBacks = value) }

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
                    val enrichment = enrichmentService.enrich(draft.front, draft.language)
                    draft = draft.copy(
                        back = enrichment.translation ?: draft.back,
                        example = draft.example ?: enrichment.example,
                    )
                }
                if (draft.back.isBlank()) continue
                if (cardRepository.cardExists(draft.front, draft.language)) {
                    duplicates++
                    continue
                }
                toInsert.add(
                    CardEntity(
                        front = draft.front,
                        back = draft.back,
                        language = draft.language,
                        example = draft.example,
                        source = draft.source,
                        sourceLabel = draft.sourceLabel,
                        fsrs = cardRepository.initialFsrsState(draft.knownAlready),
                    ),
                )
            }

            cardRepository.addCards(toInsert)
            stagedImportReader.clear()
            _uiState.update {
                it.copy(
                    isImporting = false,
                    isDone = true,
                    rows = emptyList(),
                    importedCount = toInsert.size,
                    skippedDuplicates = duplicates,
                )
            }
        }
    }
}
