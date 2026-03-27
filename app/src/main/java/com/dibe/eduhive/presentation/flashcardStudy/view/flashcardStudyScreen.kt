package com.dibe.eduhive.presentation.flashcardStudy.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
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
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass

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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                        val isExpanded = windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
                        
                        if (isExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp, vertical = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1.2f)) {
                                    FlipCardExpressive(
                                        front = flashcard.front,
                                        back = flashcard.back,
                                        isFlipped = state.isFlipped,
                                        onFlip = { viewModel.onEvent(FlashcardStudyEvent.FlipCard) },
                                        modifier = Modifier.fillMaxHeight(0.85f)
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.weight(0.8f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(32.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(12.dp)
                                            .clip(CircleShape),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = state.isFlipped,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        RatingSectionExpressive(
                                            onRate = { level ->
                                                viewModel.onEvent(FlashcardStudyEvent.RateConfidence(level))
                                            }
                                        )
                                    }

                                    if (!state.isFlipped) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            shape = MaterialTheme.shapes.large,
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Text(
                                                "Recall the concept and tap the card to see if you were right.",
                                                modifier = Modifier.padding(24.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                FlipCardExpressive(
                                    front = flashcard.front,
                                    back = flashcard.back,
                                    isFlipped = state.isFlipped,
                                    onFlip = { viewModel.onEvent(FlashcardStudyEvent.FlipCard) }
                                )

                                Spacer(modifier = Modifier.height(32.dp))

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
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            "Reveal Answer",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            if (showTips) {
                AlertDialog(
                    onDismissRequest = { showTips = false },
                    confirmButton = {
                        TextButton(onClick = { showTips = false }) { Text("Understand") }
                    },
                    icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
                    title = { Text("Study Strategy") },
                    text = {
                        Text(
                            "Recall the answer before revealing the card. Be honest with your ratings to help the AI focus on what you don't know yet."
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
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "card_rotation"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 560.dp)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.School, 
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
                        .fillMaxSize()
                        .padding(32.dp)
                        .graphicsLayer { rotationY = 180f }
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle, 
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rate your confidence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ExpressiveRatingButton(
                    icon = Icons.Rounded.Close,
                    label = "Forgot",
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) { onRate(ConfidenceLevel.UNKNOWN) }
                
                ExpressiveRatingButton(
                    icon = Icons.Rounded.SentimentDissatisfied,
                    label = "Struggled",
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ) { onRate(ConfidenceLevel.KNOWN_LITTLE) }

                ExpressiveRatingButton(
                    icon = Icons.Rounded.SentimentNeutral,
                    label = "Fair",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) { onRate(ConfidenceLevel.KNOWN_FAIRLY) }

                ExpressiveRatingButton(
                    icon = Icons.Rounded.SentimentSatisfied,
                    label = "Good",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) { onRate(ConfidenceLevel.KNOWN_WELL) }

                ExpressiveRatingButton(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Mastered",
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) { onRate(ConfidenceLevel.MASTERED) }
            }
        }
    }
}

@Composable
fun ExpressiveRatingButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
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
        Surface(
            modifier = Modifier.size(160.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AutoAwesome, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Session Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "You reviewed $count concepts today. Your knowledge hive is growing stronger.",
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
            Text("Go Home", style = MaterialTheme.typography.titleMedium)
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
        Icon(
            Icons.Rounded.History, 
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Catch up complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your knowledge hive is in sync. No items require immediate review.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
