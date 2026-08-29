package com.engvocab.app.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.rememberAppContainer

@Composable
fun ImportScreen(onDone: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportViewModel(container.cardRepository, container.enrichmentService, container.stagedImportReader) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    // Re-check for a newly pushed file when the app resumes - but only while nothing is
    // already staged for review, so we never clobber in-progress row selections.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = viewModel.uiState.value
                if (current.rows.isEmpty() && !current.isDone) viewModel.checkForStagedImport()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Import vocabulary", style = MaterialTheme.typography.headlineMedium)

        if (uiState.isDone) {
            ImportDone(uiState.importedCount, uiState.skippedDuplicates, onDone)
            return@Column
        }

        if (uiState.rows.isEmpty()) {
            NoStagedImport(onCheckAgain = viewModel::checkForStagedImport)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${uiState.includedCount}/${uiState.rows.size} selected",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fill in missing translations", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = uiState.enrichMissingBacks, onCheckedChange = viewModel::setEnrichMissingBacks)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(uiState.rows.size) { index ->
                val row = uiState.rows[index]
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = row.included, onCheckedChange = { viewModel.toggleRow(index) })
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(row.draft.front, fontWeight = FontWeight.SemiBold)
                            Text(
                                row.draft.back.ifBlank { "(to be filled in)" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = viewModel::confirmImport,
            enabled = uiState.includedCount > 0 && !uiState.isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Import ${uiState.includedCount} cards")
            }
        }
    }
}

@Composable
private fun NoStagedImport(onCheckAgain: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("No pending import", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bulk imports happen from your computer, not from the phone. Run the import tool " +
                "there (see the README's \"Importing your vocabulary\" section), then come back " +
                "here - or tap below if you just ran it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Check again")
        }
    }
}

@Composable
private fun ImportDone(importedCount: Int, skippedDuplicates: Int, onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Import complete", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("$importedCount cards added.")
        if (skippedDuplicates > 0) {
            Text("$skippedDuplicates duplicates skipped (already present).")
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}
