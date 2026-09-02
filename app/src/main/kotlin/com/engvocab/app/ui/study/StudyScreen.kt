package com.engvocab.app.ui.study

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.engvocab.app.ui.components.icon
import com.engvocab.app.ui.rememberAppContainer
import com.engvocab.core.model.Rating
import kotlin.math.roundToInt

@Composable
fun StudyScreen(onFinished: () -> Unit, onEditCard: (Long) -> Unit) {
    val container = rememberAppContainer()
    val viewModel: StudyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StudyViewModel(container.cardRepository, container.settingsRepository, container.audioPlayer) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // The card just edited (or deleted from the Cards tab) may be stale in our in-memory queue -
    // pick up any change made while we were away, e.g. on the Edit screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCurrentCard()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this card?") },
            text = { Text("This removes it permanently from your deck. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteCurrentCard()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        if (uiState.queue.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { (uiState.currentIndex.toFloat() / uiState.queue.size).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                if (!uiState.isSessionComplete && uiState.currentCard != null) {
                    IconButton(onClick = { uiState.currentCard?.let { onEditCard(it.id) } }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit this card")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete this card")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${uiState.remaining} to review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                uiState.isLoading -> Text("Loading…")
                uiState.isSessionComplete -> SessionComplete(onFinished)
                else -> uiState.currentCard?.let { card ->
                    FlashCard(
                        front = card.front,
                        back = card.back,
                        example = card.example,
                        phonetic = card.phonetic,
                        icon = card.icon(),
                        isFlipped = uiState.isFlipped,
                        onClick = viewModel::flip,
                        onPlayPronunciation = viewModel::playPronunciation,
                    )
                }
            }
        }

        if (!uiState.isSessionComplete && !uiState.isLoading && uiState.currentCard != null) {
            if (uiState.isFlipped) {
                RatingRow(previewIntervals = uiState.previewIntervals, onRate = viewModel::rate)
            } else {
                Button(onClick = viewModel::flip, modifier = Modifier.fillMaxWidth()) {
                    Text("Show answer")
                }
            }
        }
    }
}

@Composable
private fun FlashCard(
    front: String,
    back: String,
    example: String?,
    phonetic: String?,
    icon: String,
    isFlipped: Boolean,
    onClick: () -> Unit,
    onPlayPronunciation: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = isFlipped, label = "flip") { flipped ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!flipped) {
                        Text(icon, style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = if (flipped) back else front,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (!flipped) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!phonetic.isNullOrBlank()) {
                                Text(
                                    phonetic,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onPlayPronunciation) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "Play pronunciation")
                            }
                        }
                    }
                    if (flipped && !example.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            example,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!flipped) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Tap to reveal",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingRow(previewIntervals: Map<Rating, Long>, onRate: (Rating) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        RatingButton("Again", Color(0xFFDC2626), previewIntervals[Rating.AGAIN], Modifier.weight(1f)) { onRate(Rating.AGAIN) }
        RatingButton("Hard", Color(0xFFD97706), previewIntervals[Rating.HARD], Modifier.weight(1f)) { onRate(Rating.HARD) }
        RatingButton("Good", Color(0xFF16A34A), previewIntervals[Rating.GOOD], Modifier.weight(1f)) { onRate(Rating.GOOD) }
        RatingButton("Easy", Color(0xFF2563EB), previewIntervals[Rating.EASY], Modifier.weight(1f)) { onRate(Rating.EASY) }
    }
}

@Composable
private fun RatingButton(label: String, color: Color, intervalMillis: Long?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (intervalMillis != null) {
                Text(formatInterval(intervalMillis), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SessionComplete(onFinished: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Great work!", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "You're done with the cards due for now.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onFinished) { Text("Back to home") }
    }
}

private fun formatInterval(millis: Long): String {
    val minutes = millis / 60_000.0
    val hours = millis / 3_600_000.0
    val days = millis / 86_400_000.0
    return when {
        minutes < 60 -> "${minutes.roundToInt()} min"
        hours < 24 -> "${hours.roundToInt()} h"
        days < 30 -> "${days.roundToInt()} d"
        days < 365 -> "${(days / 30).roundToInt()} mo"
        else -> "${(days / 365).roundToInt()} yr"
    }
}
