package com.dibe.eduhive.presentation.quizStudy.view

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
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion
import com.dibe.eduhive.presentation.quizStudy.viewmodel.QuizStudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizStudyScreen(
    initialQuizPairs: List<Pair<Quiz, List<QuizQuestion>>> = emptyList(),
    viewModel: QuizStudyViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val snackbarHostState = remember { SnackbarHostState() }

    // If we navigated from generation preview, use those fresh quizzes immediately.
    LaunchedEffect(initialQuizPairs) {
        if (initialQuizPairs.isNotEmpty()) {
            viewModel.setInitialQuizPairs(initialQuizPairs)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val questions = remember(state.quizPairs) { state.quizPairs.flatMap { it.second } }
    var index by remember { mutableIntStateOf(0) }
    var isSubmitted by remember { mutableStateOf(false) }
    val selectedByQuestionId = remember { mutableStateMapOf<String, String>() }
    var correctCount by remember { mutableIntStateOf(0) }

    // Start timer for the first question
    LaunchedEffect(questions, index) {
        if (questions.isNotEmpty() && index < questions.size) {
            viewModel.startQuestionTimer(questions[index].id)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            if (questions.isEmpty()) "Knowledge Quiz" else "Question ${index + 1} of ${questions.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                questions.isEmpty() -> {
                    EmptyQuizState(onNavigateBack = onNavigateBack)
                }
                isSubmitted -> {
                    QuizCompletedStateExpressive(
                        score = correctCount,
                        total = questions.size,
                        onNavigateBack = onNavigateBack
                    )
                }
                else -> {
                    val question = questions[index]
                    val selectedAnswer = selectedByQuestionId[question.id]
                    val options = question.options ?: listOf("True", "False")
                    
                    // Adaptive layout logic
                    val isExpanded = windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

                    if (isExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(48.dp)
                        ) {
                            // Question Side
                            ElevatedCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Rounded.Quiz, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        text = question.question,
                                        style = MaterialTheme.typography.headlineMedium,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Options Side
                            Column(
                                modifier = Modifier
                                    .weight(0.8f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Choose an answer",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(8.dp))
                                
                                options.forEachIndexed { optIndex, optionText ->
                                    val letter = ('A' + optIndex).toString()
                                    QuizOptionExpressive(
                                        letter = letter,
                                        text = optionText,
                                        isSelected = selectedAnswer == letter,
                                        onClick = { selectedByQuestionId[question.id] = letter }
                                    )
                                }

                                Spacer(Modifier.weight(1f))
                                
                                QuizNavigationButton(
                                    isLast = index >= questions.lastIndex,
                                    enabled = selectedAnswer != null,
                                    onClick = {
                                        if (index < questions.lastIndex) {
                                            index += 1
                                        } else {
                                            viewModel.submitResults(selectedByQuestionId)
                                            correctCount = questions.count { q ->
                                                isAnswerCorrect(selectedByQuestionId[q.id], q.correctAnswer)
                                            }
                                            isSubmitted = true
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Progress
                            LinearProgressIndicator(
                                progress = { (index.toFloat() + 1) / questions.size },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )

                            // Scrollable content area
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = question.question,
                                        modifier = Modifier.padding(24.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        lineHeight = 32.sp
                                    )
                                }

                                options.forEachIndexed { optIndex, optionText ->
                                    val letter = ('A' + optIndex).toString()
                                    QuizOptionExpressive(
                                        letter = letter,
                                        text = optionText,
                                        isSelected = selectedAnswer == letter,
                                        onClick = { selectedByQuestionId[question.id] = letter }
                                    )
                                }
                            }

                            QuizNavigationButton(
                                isLast = index >= questions.lastIndex,
                                enabled = selectedAnswer != null,
                                onClick = {
                                    if (index < questions.lastIndex) {
                                        index += 1
                                    } else {
                                        viewModel.submitResults(selectedByQuestionId)
                                        correctCount = questions.count { q ->
                                            isAnswerCorrect(selectedByQuestionId[q.id], q.correctAnswer)
                                        }
                                        isSubmitted = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizOptionExpressive(
    letter: String,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        letter, 
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun QuizNavigationButton(
    isLast: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isLast) "Submit Results" else "Next Question",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                if (isLast) Icons.AutoMirrored.Rounded.Send else Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null
            )
        }
    }
}

private fun isAnswerCorrect(selected: String?, correctAnswerRaw: String): Boolean {
    if (selected == null) return false
    val correct = correctAnswerRaw.trim().uppercase()
    return selected.uppercase() == correct ||
        (selected.uppercase() == "A" && correct == "TRUE") ||
        (selected.uppercase() == "B" && correct == "FALSE")
}

@Composable
private fun EmptyQuizState(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Text(
                "No Quiz Found", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "To validate your mastery, first import some material and extract concepts to generate questions.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onNavigateBack, 
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) { 
                Text("Return Home") 
            }
        }
    }
}

@Composable
private fun QuizCompletedStateExpressive(
    score: Int,
    total: Int,
    onNavigateBack: () -> Unit
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
                    Icons.Rounded.CheckCircle, 
                    contentDescription = null, 
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Quiz Complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = "Score: $score / $total",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Text(
            text = "Your results have been synchronized with the Knowledge Hive.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Back to Dashboard", style = MaterialTheme.typography.titleMedium)
        }
    }
}
