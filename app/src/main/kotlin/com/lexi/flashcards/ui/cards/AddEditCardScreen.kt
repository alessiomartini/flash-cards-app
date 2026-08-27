package com.lexi.flashcards.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lexi.flashcards.data.db.CardType
import com.lexi.flashcards.ui.rememberAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCardScreen(cardId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: AddEditCardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AddEditCardViewModel(container.cardRepository, container.enrichmentService, cardId) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "Nuova carta" else "Modifica carta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = uiState.front,
                onValueChange = viewModel::onFrontChange,
                label = { Text("Termine / espressione (inglese)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::autoComplete,
                    enabled = uiState.front.isNotBlank() && !uiState.isEnriching,
                ) {
                    if (uiState.isEnriching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(" Auto-completa")
                }
            }

            OutlinedTextField(
                value = uiState.back,
                onValueChange = viewModel::onBackChange,
                label = { Text("Traduzione italiana") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.definition,
                onValueChange = viewModel::onDefinitionChange,
                label = { Text("Definizione in inglese (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.example,
                onValueChange = viewModel::onExampleChange,
                label = { Text("Frase d'esempio (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.partOfSpeech,
                onValueChange = viewModel::onPartOfSpeechChange,
                label = { Text("Categoria grammaticale (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            CardTypeDropdown(selected = uiState.cardType, onSelected = viewModel::onCardTypeChange)

            OutlinedTextField(
                value = uiState.tags,
                onValueChange = viewModel::onTagsChange,
                label = { Text("Tag (separati da virgola)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = viewModel::save,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salva")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardTypeDropdown(selected: CardType, onSelected: (CardType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        CardType.WORD to "Parola",
        CardType.PHRASAL_VERB to "Phrasal verb",
        CardType.IDIOM to "Modo di dire",
        CardType.EXPRESSION to "Espressione",
    )

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labels.getValue(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
