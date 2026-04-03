package com.dibe.eduhive.presentation.conceptList.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.presentation.conceptList.viewmodel.ConceptListEvent
import com.dibe.eduhive.presentation.conceptList.viewmodel.ConceptListState
import com.dibe.eduhive.presentation.conceptList.viewmodel.ConceptListViewModel
import com.dibe.eduhive.presentation.conceptList.viewmodel.GenerationMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptListScreen(
    viewModel: ConceptListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenConceptFlashcards: (String, String) -> Unit,
    onNavigateToPreview: (GenerationMode) -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Navigate to preview when generation completes
    LaunchedEffect(state.generationMode, state.generatedFlashcards, state.generatedQuizPairs) {
        val mode = state.generationMode ?: return@LaunchedEffect
        val hasContent = state.generatedFlashcards.isNotEmpty() || state.generatedQuizPairs.isNotEmpty()
        if (hasContent) {
            onNavigateToPreview(mode)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.isSelectionActive,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { isSelecting ->
                        if (isSelecting) {
                            Text(
                                "${state.selectedCount} selected",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("Knowledge Base", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    AnimatedContent(
                        targetState = state.isSelectionActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { isSelecting ->
                        if (isSelecting) {
                            IconButton(onClick = { viewModel.onEvent(ConceptListEvent.ClearSelection) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                            }
                        } else {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                },
                actions = {
                    // "Select Weak" shortcut — always visible
                    IconButton(
                        onClick = { viewModel.onEvent(ConceptListEvent.SelectWeak) },
                        enabled = !state.isGenerating
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = "Auto-select weak concepts",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Animated selection action bar
            AnimatedVisibility(
                visible = state.isSelectionActive,
                enter = slideInVertically { it } + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                GenerationActionBar(
                    selectedCount = state.selectedCount,
                    isGenerating = state.isGenerating,
                    generationProgress = state.generationProgress,
                    onGenerate = { mode -> viewModel.onEvent(ConceptListEvent.Generate(mode)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.isGenerating -> {
                    GeneratingOverlay(
                        message = state.generationProgress ?: "Generating...",
                        progress = state.generationProgressFloat,
                        completed = state.generationCompleted,
                        total = state.generationTotal,
                        currentConceptName = state.currentConceptName,
                        generationMode = state.generationMode,
                        generatedFlashcardsCount = state.generatedFlashcards.size,
                        generatedQuizCount = state.generatedQuizPairs.sumOf { it.second.size },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.concepts.isEmpty() -> {
                    EmptyConceptsState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            // Extra bottom padding so last card clears the action bar
                            bottom = if (state.isSelectionActive) 140.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Hint banner when nothing is selected yet
                        if (!state.isSelectionActive) {
                            item {
                                SelectionHintBanner()
                            }
                        }

                        items(state.concepts, key = { it.id }) { concept ->
                            SelectableConceptCard(
                                concept = concept,
                                isSelected = concept.id in state.selectedIds,
                                onOpenFlashcards = {
                                    onOpenConceptFlashcards(concept.id, concept.name)
                                },
                                onToggle = {
                                    viewModel.onEvent(ConceptListEvent.ToggleSelection(concept.id))
                                }
                            )
                        }
                    }
                }
            }

            // Error snackbar
            AnimatedVisibility(
                visible = state.error != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                state.error?.let {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.onEvent(ConceptListEvent.DismissError) }) {
                                Text("Dismiss")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) { Text(it) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Selection hint
// ---------------------------------------------------------------------------

@Composable
private fun SelectionHintBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "Tap concepts to select them, then generate flashcards or a quiz. " +
                        "Tap ✨ to auto-select weak concepts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Selectable concept card
// ---------------------------------------------------------------------------

@Composable
fun SelectableConceptCard(
    concept: Concept,
    isSelected: Boolean,
    onOpenFlashcards: () -> Unit,
    onToggle: () -> Unit
) {
    var isExpanded by remember(concept.id) { mutableStateOf(false) }

    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    val border = if (isSelected)
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    else
        null

    OutlinedCard(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Confidence ring
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 4.dp
                )
                CircularProgressIndicator(
                    progress = { concept.confidence.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = confidenceColor(concept.confidence),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${(concept.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Name + description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    concept.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                concept.description?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (isExpanded) "Show less" else "Show more")
                    }
                }

                TextButton(
                    onClick = onOpenFlashcards,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("View flashcards")
                }
            }

            // Checkmark when selected
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom action bar
// ---------------------------------------------------------------------------

@Composable
private fun GenerationActionBar(
    selectedCount: Int,
    isGenerating: Boolean,
    generationProgress: String?,
    onGenerate: (GenerationMode) -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "$selectedCount concept${if (selectedCount == 1) "" else "s"} selected — choose what to generate:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GenerateChip(
                    label = "Cards",
                    icon = Icons.Rounded.Style,
                    onClick = { onGenerate(GenerationMode.FLASHCARDS) },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                )
                GenerateChip(
                    label = "Quiz",
                    icon = Icons.Rounded.Quiz,
                    onClick = { onGenerate(GenerationMode.QUIZ) },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                )
            }

            GenerateChip(
                label = "Generate Both",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { onGenerate(GenerationMode.BOTH) },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
                filled = true
            )
        }
    }
}

@Composable
private fun GenerateChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Generating overlay — expressive card mirroring the AddMaterial style
// ---------------------------------------------------------------------------

@Composable
private fun GeneratingOverlay(
    message: String,
    progress: Float,
    completed: Int,
    total: Int,
    currentConceptName: String?,
    generationMode: GenerationMode?,
    generatedFlashcardsCount: Int = 0,
    generatedQuizCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val modeLabel = when (generationMode) {
        GenerationMode.FLASHCARDS -> "Flashcards"
        GenerationMode.QUIZ -> "Quiz Questions"
        GenerationMode.BOTH -> "Flashcards & Quiz"
        null -> "Content"
    }
    val modeIcon = when (generationMode) {
        GenerationMode.FLASHCARDS -> Icons.Rounded.Style
        GenerationMode.QUIZ -> Icons.Rounded.Quiz
        GenerationMode.BOTH -> Icons.Rounded.AutoAwesome
        null -> Icons.Rounded.AutoAwesome
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Glowing progress ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            Surface(
                modifier = Modifier.size(130.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = glowAlpha),
                shape = CircleShape
            ) {}
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                strokeWidth = 8.dp
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(120.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    modeIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (total > 0) "$completed/$total" else "…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Info card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                modeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                modeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Generating",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedContent(
                    targetState = currentConceptName,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "conceptName"
                ) { name ->
                    if (name != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.LightbulbCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(40.dp))
                    }
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = if (total > 0) "${(animatedProgress * 100).toInt()}% complete" else "Preparing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }

        // Live summary badges — same card style as AddMaterial progress screen
        AnimatedVisibility(
            visible = generatedFlashcardsCount > 0 || generatedQuizCount > 0,
            enter = slideInVertically { it / 2 } + fadeIn(tween(600))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (generatedFlashcardsCount > 0) {
                    GenerationSummaryBadge(
                        label = "Flashcards",
                        value = "$generatedFlashcardsCount",
                        icon = Icons.Rounded.Style,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (generatedQuizCount > 0) {
                    GenerationSummaryBadge(
                        label = "Quiz Q's",
                        value = "$generatedQuizCount",
                        icon = Icons.Rounded.Quiz,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun GenerationSummaryBadge(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmptyConceptsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Book,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No concepts yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Import material to extract concepts into your hive.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun confidenceColor(confidence: Double) = when {
    confidence < 0.4 -> MaterialTheme.colorScheme.error
    confidence < 0.7 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}