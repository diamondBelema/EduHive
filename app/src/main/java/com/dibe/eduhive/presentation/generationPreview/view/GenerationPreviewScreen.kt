package com.dibe.eduhive.presentation.generationPreview.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.domain.model.enums.QuizQuestionType
import com.dibe.eduhive.presentation.conceptList.viewmodel.GenerationMode
import com.dibe.eduhive.presentation.generationPreview.viewmodel.GenerationPreviewEvent
import com.dibe.eduhive.presentation.generationPreview.viewmodel.GenerationPreviewViewModel
import com.dibe.eduhive.presentation.generationPreview.viewmodel.PreviewTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationPreviewScreen(
    viewModel: GenerationPreviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToStudy: (PreviewTab) -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Navigate to study when user taps Start Studying
    LaunchedEffect(Unit) {
        viewModel.navigateToStudy.collect { destination ->
            onNavigateToStudy(destination)
        }
    }

    val showTabs = viewModel.mode == GenerationMode.BOTH

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ready to Study", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Summary chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.totalFlashcards > 0) {
                            SummaryChip(
                                label = "${state.totalFlashcards} flashcards",
                                icon = Icons.Rounded.Style
                            )
                        }
                        if (state.totalQuestions > 0) {
                            SummaryChip(
                                label = "${state.totalQuestions} questions",
                                icon = Icons.Rounded.Quiz
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.onEvent(GenerationPreviewEvent.StartStudying) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Studying", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row — only shown when both were generated
            if (showTabs) {
                TabRow(
                    selectedTabIndex = state.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = state.selectedTab == PreviewTab.FLASHCARDS,
                        onClick = {
                            viewModel.onEvent(GenerationPreviewEvent.SelectTab(PreviewTab.FLASHCARDS))
                        },
                        text = { Text("Flashcards (${state.totalFlashcards})") },
                        icon = {
                            Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    Tab(
                        selected = state.selectedTab == PreviewTab.QUIZ,
                        onClick = {
                            viewModel.onEvent(GenerationPreviewEvent.SelectTab(PreviewTab.QUIZ))
                        },
                        text = { Text("Quiz (${state.totalQuestions})") },
                        icon = {
                            Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = state.selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { tab ->
                when (tab) {
                    PreviewTab.FLASHCARDS -> FlashcardsPreviewList(flashcards = state.flashcards)
                    PreviewTab.QUIZ -> QuizPreviewList(questions = state.allQuestions)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Flashcard preview list
// ---------------------------------------------------------------------------

@Composable
private fun FlashcardsPreviewList(flashcards: List<Flashcard>) {
    if (flashcards.isEmpty()) {
        EmptyPreviewState(message = "No flashcards were generated.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(flashcards) { index, card ->
            FlashcardPreviewItem(index = index + 1, flashcard = card)
        }
    }
}

@Composable
private fun FlashcardPreviewItem(index: Int, flashcard: Flashcard) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$index",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    "Flashcard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                flashcard.front,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.TipsAndUpdates,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    flashcard.back,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Quiz preview list
// ---------------------------------------------------------------------------

@Composable
private fun QuizPreviewList(questions: List<QuizQuestion>) {
    if (questions.isEmpty()) {
        EmptyPreviewState(message = "No quiz questions were generated.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(questions) { index, question ->
            QuizPreviewItem(index = index + 1, question = question)
        }
    }
}

@Composable
private fun QuizPreviewItem(index: Int, question: QuizQuestion) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$index",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    question.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                question.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (question.type == QuizQuestionType.MCQ && !question.options.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                question.options.forEachIndexed { i, option ->
                    val letter = ('A' + i).toString()
                    val isCorrect = letter == question.correctAnswer
                    OptionRow(
                        letter = letter,
                        text = option,
                        isCorrect = isCorrect
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Answer: ${question.correctAnswer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(letter: String, text: String, isCorrect: Boolean) {
    val containerColor = if (isCorrect)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = if (isCorrect) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCorrect) FontWeight.SemiBold else FontWeight.Normal
            )
            if (isCorrect) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Correct",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun SummaryChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyPreviewState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}