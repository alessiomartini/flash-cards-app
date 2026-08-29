package com.engvocab.app.ui.importer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.rememberAppContainer
import java.io.IOException

@Composable
fun ImportScreen(onDone: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: ImportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ImportViewModel(container.cardRepository, container.enrichmentService) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            readTextFromUri(context, uri)?.let(viewModel::parse)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Import vocabulary", style = MaterialTheme.typography.headlineMedium)

        if (uiState.isDone) {
            ImportDone(uiState.importedCount, uiState.skippedDuplicates, onDone)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.format == ImportFormat.DUOCARDS,
                onClick = { viewModel.setFormat(ImportFormat.DUOCARDS) },
                label = { Text("Export Duocards (CSV)") },
            )
            FilterChip(
                selected = uiState.format == ImportFormat.KINDLE,
                onClick = { viewModel.setFormat(ImportFormat.KINDLE) },
                label = { Text("Kindle (My Clippings.txt)") },
            )
        }

        Text(
            when (uiState.format) {
                ImportFormat.DUOCARDS -> "Export your deck from Duocards and pick the CSV/TSV file here."
                ImportFormat.KINDLE -> "Connect your Kindle via USB and pick documents/My Clippings.txt."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose file")
        }

        if (uiState.isParsing) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (uiState.rows.isNotEmpty()) {
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

private fun readTextFromUri(context: Context, uri: Uri): String? =
    try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: IOException) {
        null
    }
