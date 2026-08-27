package com.lexi.flashcards.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lexi.flashcards.ui.rememberAppContainer
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } },
    )
    val desiredRetention by viewModel.desiredRetention.collectAsState()
    val autoEnrichEnabled by viewModel.autoEnrichEnabled.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineMedium)

        Column {
            Text("Retention desiderata", style = MaterialTheme.typography.titleMedium)
            Text(
                "Probabilità target di ricordare una carta al momento del ripasso. Più alta = ripassi più " +
                    "frequenti ma meno dimenticanze. FSRS usa questo valore per calcolare gli intervalli.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = desiredRetention.toFloat(),
                    onValueChange = { viewModel.setDesiredRetention(it.toDouble()) },
                    valueRange = 0.75f..0.97f,
                    modifier = Modifier.weight(1f),
                )
                Text("${(desiredRetention * 100).roundToInt()}%")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-completamento", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cerca automaticamente definizione, esempio e traduzione per le carte nuove/importate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoEnrichEnabled, onCheckedChange = viewModel::setAutoEnrichEnabled)
        }

        Column {
            Text("Come funziona la memorizzazione", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lexi usa FSRS (Free Spaced Repetition Scheduler), l'algoritmo di ripetizione dilazionata " +
                    "più efficace secondo la ricerca attuale: rispetto al classico SM-2 (Anki/SuperMemo) " +
                    "richiede il 20-30% di ripassi in meno per la stessa retention, perché adatta gli " +
                    "intervalli alla curva di dimenticanza reale di ogni singola carta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
