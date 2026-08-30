package com.engvocab.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.components.LanguageDropdownField
import com.engvocab.app.ui.rememberAppContainer
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val container = rememberAppContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } },
    )
    val desiredRetention by viewModel.desiredRetention.collectAsState()
    val autoEnrichEnabled by viewModel.autoEnrichEnabled.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val cloudflareAccountId by viewModel.cloudflareAccountId.collectAsState()
    val cloudflareDatabaseId by viewModel.cloudflareDatabaseId.collectAsState()
    val cloudflareApiToken by viewModel.cloudflareApiToken.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Column {
            Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connects the Sync tab to your Cloudflare D1 database. See the README's " +
                    "\"Importing your vocabulary\" section for where to find these values.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = cloudflareAccountId,
                onValueChange = viewModel::setCloudflareAccountId,
                label = { Text("Cloudflare account ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = cloudflareDatabaseId,
                onValueChange = viewModel::setCloudflareDatabaseId,
                label = { Text("D1 database ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = cloudflareApiToken,
                onValueChange = viewModel::setCloudflareApiToken,
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Column {
            Text("Study language", style = MaterialTheme.typography.titleMedium)
            Text(
                "Which language's cards Home, Study, and Cards show. New cards default to this language.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LanguageDropdownField(selected = selectedLanguage, onSelected = viewModel::setSelectedLanguage)
        }

        Column {
            Text("Desired retention", style = MaterialTheme.typography.titleMedium)
            Text(
                "Target probability of remembering a card at review time. Higher = more frequent " +
                    "reviews but fewer forgotten cards. FSRS uses this value to compute intervals.",
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
                Text("Auto-fill", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Automatically look up a definition, example, and translation for new/imported cards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoEnrichEnabled, onCheckedChange = viewModel::setAutoEnrichEnabled)
        }

        Column {
            Text("How memorization works", style = MaterialTheme.typography.titleMedium)
            Text(
                "EngVocab uses FSRS (Free Spaced Repetition Scheduler), the most effective spaced " +
                    "repetition algorithm according to current research: compared to the classic SM-2 " +
                    "(Anki/SuperMemo), it needs 20-30% fewer reviews for the same retention, because it " +
                    "adapts intervals to each card's actual forgetting curve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
