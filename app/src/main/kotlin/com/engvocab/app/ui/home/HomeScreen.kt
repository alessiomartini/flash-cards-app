package com.engvocab.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.components.LanguageChip
import com.engvocab.app.ui.rememberAppContainer

@Composable
fun HomeScreen(onStudyClick: () -> Unit) {
    val container = rememberAppContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.cardRepository, container.settingsRepository, container.updateService) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val updateBanner by viewModel.updateBanner.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EngVocab", style = MaterialTheme.typography.headlineMedium)
            LanguageChip(selected = uiState.language, onSelected = viewModel::setLanguage)
        }
        Text(
            "Vocabulary and expressions, long-term retention with FSRS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (updateBanner == UpdateBanner.NEEDS_INSTALL_PERMISSION) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "An update was downloaded, but EngVocab needs your permission to install apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = viewModel::openInstallPermissionSettings) { Text("Allow") }
                        TextButton(onClick = viewModel::dismissUpdateBanner) { Text("Dismiss") }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (uiState.dueCount > 0) "${uiState.dueCount} cards due today" else "All caught up!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onStudyClick, enabled = uiState.dueCount > 0) {
                    Text(if (uiState.dueCount > 0) "Study now" else "No cards due")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(label = "Total cards", value = uiState.totalCount.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Reviews today", value = uiState.reviewsToday.toString(), modifier = Modifier.weight(1f))
        }
        StatTile(
            label = "day streak",
            value = uiState.streakDays.toString(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
