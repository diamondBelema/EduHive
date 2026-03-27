package com.dibe.eduhive.presentation.flashcardStudy.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var showTips by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            "${state.currentIndex + 1} of ${state.flashcards.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTips = true }) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = "Tips")
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }

                state.isComplete -> {
                    StudyCompletionScreenExpressive(
                        count = state.completedCount,
                        onContinue = {
                            viewModel.onEvent(FlashcardStudyEvent.StudyAnyway)
                        },
                        onBack = onNavigateBack
                    )
                }

                state.flashcards.isEmpty() -> {
                    NoFlashcardsStateExpressive(
                        onNavigateBack = onNavigateBack,
                        onStudyAnyway = { viewModel.onEvent(FlashcardStudyEvent.StudyAnyway) }
                    )
                }

                else -> {
                    state.currentFlashcard?.let { flashcard ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Expressive Progress Bar
                            LinearProgressIndicator(
                                progress = state.progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )

                            Spacer(modifier = Modifier.weight(0.5f))

                            // Flashcard with enhanced Flip
                            FlipCardExpressive(
                                front = flashcard.front,
                                back = flashcard.back,
                                isFlipped = state.isFlipped,
                                onFlip = { viewModel.onEvent(FlashcardStudyEvent.FlipCard) }
                            )

                            Spacer(modifier = Modifier.weight(0.5f))

                            // Rating buttons with Animated Visibility
                            AnimatedVisibility(
                                visible = state.isFlipped,
                                enter = slideInVertically { it / 2 } + fadeIn(),
                                exit = slideOutVertically { it / 2 } + fadeOut()
                            ) {
                                RatingSectionExpressive(
                                    onRate = { level ->
                                        viewModel.onEvent(FlashcardStudyEvent.RateConfidence(level))
                                    }
                                )
                            }
                            
                            if (!state.isFlipped) {
                                Text(
                                    "Tap card to reveal answer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            if (showTips) {
                AlertDialog(
                    onDismissRequest = { showTips = false },
                    confirmButton = {
                        TextButton(onClick = { showTips = false }) { Text("Got it") }
                    },
                    title = { Text("Study Tips") },
                    text = {
                        Text(
                            "Tap a card to reveal the answer, then rate how well you knew it. " +
                                "If no cards are due, use Study Anyway to keep practicing."
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun FlipCardExpressive(
    front: String,
    back: String,
    isFlipped: Boolean,
    onFlip: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f) // Vertical card feel
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16f * density
            }
            .clickable(onClick = onFlip),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (rotation <= 90f) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                // Front side
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = front,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp
                    )
                }
            } else {
                // Back side
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .graphicsLayer { rotationY = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = back,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 32.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RatingSectionExpressive(
    onRate: (ConfidenceLevel) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "How well did you know this?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExpressiveRatingButton("😰", "Poor", MaterialTheme.colorScheme.errorContainer) { onRate(ConfidenceLevel.UNKNOWN) }
                ExpressiveRatingButton("😐", "Okay", MaterialTheme.colorScheme.secondaryContainer) { onRate(ConfidenceLevel.KNOWN_FAIRLY) }
                ExpressiveRatingButton("🎉", "Easy", MaterialTheme.colorScheme.primaryContainer) { onRate(ConfidenceLevel.MASTERED) }
            }
        }
    }
}

@Composable
fun ExpressiveRatingButton(
    emoji: String,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 32.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StudyCompletionScreenExpressive(
    count: Int,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {}
            Text(text = "🎊", style = MaterialTheme.typography.displayLarge)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "You reviewed $count concepts today. Your knowledge is growing!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Study More", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Back to Dashboard", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun NoFlashcardsStateExpressive(onNavigateBack: () -> Unit, onStudyAnyway: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🛌", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Hive is Resting",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "No cards are due for review right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onStudyAnyway,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Study Anyway")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Return Home")
        }
    }
}
