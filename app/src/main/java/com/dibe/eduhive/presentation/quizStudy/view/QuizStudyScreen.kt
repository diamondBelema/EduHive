package com.dibe.eduhive.presentation.quizStudy.view

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
    
    // Feedback state
    var showFeedback by remember { mutableStateOf(false) }

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
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            if (questions.isEmpty()) "Quiz" else "${index + 1} / ${questions.size}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
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

                    val isCorrect = selectedAnswer != null && isAnswerCorrect(selectedAnswer, question.correctAnswer)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress bar with counter chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (index.toFloat() + 1) / questions.size },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(CircleShape),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    "${index + 1}/${questions.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Scrollable content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Question hero card with gradient accent
                            QuestionHeroCard(questionText = question.question)

                            // Options
                            options.forEachIndexed { optIndex, optionText ->
                                val letter = ('A' + optIndex).toString()
                                val isOptionSelected = selectedAnswer == letter

                                QuizOptionExpressive(
                                    letter = letter,
                                    text = optionText,
                                    isSelected = isOptionSelected,
                                    isCorrect = if (showFeedback) letter == question.correctAnswer.trim().uppercase() else null,
                                    showFeedback = showFeedback,
                                    onClick = {
                                        if (!showFeedback) {
                                            selectedByQuestionId[question.id] = letter
                                            showFeedback = true
                                            if (isAnswerCorrect(letter, question.correctAnswer)) {
                                                correctCount++
                                            }
                                        }
                                    }
                                )
                            }

                            // Spacer so feedback panel doesn't overlap last option
                            if (showFeedback) Spacer(Modifier.height(8.dp))
                        }

                        AnimatedVisibility(
                            visible = showFeedback,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            FeedbackSection(
                                isCorrect = isCorrect,
                                correctAnswer = question.correctAnswer,
                                options = options,
                                onNext = {
                                    if (index < questions.lastIndex) {
                                        index += 1
                                        showFeedback = false
                                    } else {
                                        viewModel.submitResults(selectedByQuestionId)
                                        isSubmitted = true
                                    }
                                },
                                isLast = index >= questions.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionHeroCard(questionText: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "question_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Decorative glow blob
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-20).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = questionText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuizOptionExpressive(
    letter: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    showFeedback: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        showFeedback && isCorrect == true -> MaterialTheme.colorScheme.tertiaryContainer
        showFeedback && isSelected && isCorrect == false -> MaterialTheme.colorScheme.errorContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }

    val borderColor = when {
        showFeedback && isCorrect == true -> MaterialTheme.colorScheme.tertiary
        showFeedback && isSelected && isCorrect == false -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = backgroundColor,
        border = if (borderColor != Color.Transparent) androidx.compose.foundation.BorderStroke(3.dp, borderColor) else null,
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = when {
                    showFeedback && isCorrect == true -> MaterialTheme.colorScheme.tertiary
                    showFeedback && isSelected && isCorrect == false -> MaterialTheme.colorScheme.error
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (showFeedback && isCorrect == true) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(24.dp))
                    } else if (showFeedback && isSelected && isCorrect == false) {
                        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            letter, 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected || (showFeedback && isCorrect == true)) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    showFeedback && isCorrect == true -> MaterialTheme.colorScheme.onTertiaryContainer
                    showFeedback && isSelected && isCorrect == false -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun FeedbackSection(
    isCorrect: Boolean,
    correctAnswer: String,
    options: List<String>,
    onNext: () -> Unit,
    isLast: Boolean
) {
    Surface(
        color = if (isCorrect) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isCorrect) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isCorrect) "Masterful!" else "Not this time",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = if (isCorrect) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            if (!isCorrect) {
                val correctText = remember(correctAnswer, options) {
                    val idx = correctAnswer.trim().uppercase().firstOrNull()?.minus('A') ?: -1
                    if (idx in options.indices) options[idx] else correctAnswer
                }
                Text(
                    "The correct answer is: $correctText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCorrect) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    contentColor = if (isCorrect) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onError
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    if (isLast) "View Hive Results" else "Continue Journey",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Icon(if (isLast) Icons.AutoMirrored.Rounded.Send else Icons.AutoMirrored.Rounded.NavigateNext, null)
            }
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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "No Quiz Found", 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "To validate your mastery, first import some material and extract concepts to generate questions.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onNavigateBack, 
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) { 
                Text("Return Home", style = MaterialTheme.typography.titleMedium) 
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
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CheckCircle, 
                    contentDescription = null, 
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        val performanceMessage = when {
            score == total -> "Ultimate Mastery!"
            score >= total * 0.8 -> "Exceptional Effort!"
            score >= total * 0.5 -> "Solid Progress"
            else -> "Keep Pushing Forward"
        }

        Text(
            text = performanceMessage,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Text(
                text = "$score / $total Correct",
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        Text(
            text = "Your results are synced with the Knowledge Hive.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(56.dp))
        
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Back to Dashboard", style = MaterialTheme.typography.titleMedium)
        }
    }
}
