package com.dibe.eduhive.presentation.flashcardStudy.view

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.presentation.flashcardStudy.viewmodel.FlashcardStudyEvent
import com.dibe.eduhive.presentation.flashcardStudy.viewmodel.FlashcardStudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardStudyScreen(
    viewModel: FlashcardStudyViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${state.currentIndex + 1} / ${state.flashcards.size}",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.isComplete -> {
                    CompletionScreen(
                        count = state.completedCount,
                        onContinue = onNavigateBack,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.flashcards.isEmpty() -> {
                    NoFlashcardsState(
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    state.currentFlashcard?.let { flashcard ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Progress bar
                            LinearProgressIndicator(
                                progress = state.progress,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Flashcard
                            FlipCard(
                                front = flashcard.front,
                                back = flashcard.back,
                                isFlipped = state.isFlipped,
                                onFlip = { viewModel.onEvent(FlashcardStudyEvent.FlipCard) }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Rating buttons (only show when flipped)
                            if (state.isFlipped) {
                                RatingButtons(
                                    onRate = { level ->
                                        viewModel.onEvent(FlashcardStudyEvent.RateConfidence(level))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
fun FlipCard(
    front: String,
    back: String,
    isFlipped: Boolean,
    onFlip: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onFlip),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                // Front side
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = front,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap to flip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Back side (mirrored)
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .graphicsLayer { rotationY = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = back,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun RatingButtons(
    onRate: (ConfidenceLevel) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "How well did you know this?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RatingButton(
                emoji = "😰",
                label = "Unknown",
                onClick = { onRate(ConfidenceLevel.UNKNOWN) }
            )
            RatingButton(
                emoji = "😕",
                label = "Little",
                onClick = { onRate(ConfidenceLevel.KNOWN_LITTLE) }
            )
            RatingButton(
                emoji = "😐",
                label = "Fair",
                onClick = { onRate(ConfidenceLevel.KNOWN_FAIRLY) }
            )
            RatingButton(
                emoji = "😊",
                label = "Well",
                onClick = { onRate(ConfidenceLevel.KNOWN_WELL) }
            )
            RatingButton(
                emoji = "🎉",
                label = "Master",
                onClick = { onRate(ConfidenceLevel.MASTERED) }
            )
        }
    }
}

@Composable
fun RatingButton(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun NoFlashcardsState(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎉",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "All caught up!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No flashcards due right now",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
fun CompletionScreen(
    count: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✅",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Session Complete!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Reviewed $count flashcards",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue) {
            Text("Continue")
        }
    }
}