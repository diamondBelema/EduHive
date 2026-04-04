package com.dibe.eduhive.presentation.conceptList.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.presentation.addMaterial.view.SummaryBadgeExpressive
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
                        },
                        label = "title_animation"
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
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "nav_animation"
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
                visible = state.isSelectionActive && !state.isGenerating,
                enter = slideInVertically { it } + fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                GenerationActionBar(
                    selectedCount = state.selectedCount,
                    isGenerating = state.isGenerating,
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }

                state.isGenerating -> {
                    GeneratingStateExpressive(
                        status = state.generationProgress ?: "Initializing...",
                        progress = state.generationProgressFloat,
                        currentConceptName = state.currentConceptName,
                        flashcardsCount = state.generatedFlashcards.size,
                        quizCount = state.generatedQuizPairs.sumOf { it.second.size }
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
// Generating State — Expressive UI mirroring AddMaterial
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GeneratingStateExpressive(
    status: String,
    progress: Float,
    currentConceptName: String?,
    flashcardsCount: Int,
    quizCount: Int
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gen_expressive")

    // Pulse scale for central icon
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Glowing colors
    val glowColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        targetValue = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft background glow
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .blur(40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                        ),
                        CircleShape
                    )
            )

            val density = LocalDensity.current
            val strokeWidthPx = with(density) { 14.dp.toPx() }

            // The Expressive Squiggly Indicator
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(240.dp),
                stroke = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    miter = 20f
                ),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                color = MaterialTheme.colorScheme.primary,
                gapSize = 0.dp,
                wavelength = 40.dp
            )

            // Central Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(pulseScale)
            ) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Building Hive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Status Pill
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
            shape = CircleShape,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Concept Name Overlay
        AnimatedContent(
            targetState = currentConceptName,
            transitionSpec = { fadeIn() + slideInVertically() togetherWith fadeOut() },
            label = "concept_label"
        ) { name ->
            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(Modifier.height(32.dp))
            }
        }

        // Summary Badges Section — Match AddMaterial style
        AnimatedVisibility(
            visible = flashcardsCount > 0 || quizCount > 0,
            enter = slideInVertically { it / 2 } + fadeIn(tween(800)),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (flashcardsCount > 0) {
                    SummaryBadgeExpressive(
                        label = "Flashcards",
                        value = "$flashcardsCount",
                        icon = Icons.Rounded.Style,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (quizCount > 0) {
                    SummaryBadgeExpressive(
                        label = "Quiz Q's",
                        value = "$quizCount",
                        icon = Icons.Rounded.Quiz,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
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
// Empty state
// ---------------------------------------------------------------------------

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
