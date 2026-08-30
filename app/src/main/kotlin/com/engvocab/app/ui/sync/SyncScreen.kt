package com.engvocab.app.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.rememberAppContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncScreen() {
    val container = rememberAppContainer()
    val viewModel: SyncViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SyncViewModel(
                    container.syncRepository,
                    container.cardRepository,
                    container.enrichmentService,
                    container.settingsRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sync", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your vocabulary lives in an online database you edit from a computer - the " +
                "Cloudflare dashboard for quick edits, or the `:cli` tool to bulk-push a Duocards " +
                "or Kindle export. Tap below to pull the latest words down to this phone. Your " +
                "review progress stays on this phone only and is never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    lastSyncedAt?.let { "Last synced ${formatTimestamp(it)}" } ?: "Never synced yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sync now")
                    }
                }
            }
        }

        uiState.lastResult?.let { result ->
            Text(
                "${result.added} added, ${result.updated} updated, ${result.removed} removed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        uiState.errorMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        Column {
            Text("Fill in missing translations", style = MaterialTheme.typography.titleMedium)
            Text(
                "Looks up a translation, a short example sentence showing the word in context, and " +
                    "a definition for every card in the selected language that still has no back - " +
                    "e.g. a batch just synced from a Duocards export, which never carries translations. " +
                    "Runs one card at a time using the same free services as the per-card Auto-fill " +
                    "button, so a large batch can take a while and may hit the free daily quota.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::fillMissingTranslations,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isAutoFilling) {
                    Text("Filling in... ${uiState.autoFillDone}/${uiState.autoFillTotal}")
                } else {
                    Text("Fill in missing translations")
                }
            }
            if (uiState.isAutoFilling && uiState.autoFillTotal > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.autoFillDone.toFloat() / uiState.autoFillTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            uiState.autoFillResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    if (result.stillMissing > 0) {
                        "${result.filled} filled in, ${result.stillMissing} still missing (no translation found - try again later)."
                    } else {
                        "${result.filled} filled in."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))
