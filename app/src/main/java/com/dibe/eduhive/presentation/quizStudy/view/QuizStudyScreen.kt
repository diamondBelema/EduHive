package com.dibe.eduhive.presentation.quizStudy.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dibe.eduhive.domain.model.Quiz
import com.dibe.eduhive.domain.model.QuizQuestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizStudyScreen(
    quizPairs: List<Pair<Quiz, List<QuizQuestion>>>,
    onNavigateBack: () -> Unit
) {
    val questions = remember(quizPairs) { quizPairs.flatMap { it.second } }
    var index by remember { mutableIntStateOf(0) }
    var isSubmitted by remember { mutableStateOf(false) }
    val selectedByQuestionId = remember { mutableStateMapOf<String, String>() }
    var correctCount by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (questions.isEmpty()) "Quiz" else "Question ${index + 1} of ${questions.size}",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            questions.isEmpty() -> {
                EmptyQuizState(modifier = Modifier.padding(padding), onNavigateBack = onNavigateBack)
            }
            isSubmitted -> {
                QuizCompletedState(
                    score = correctCount,
                    total = questions.size,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                val question = questions[index]
                val selectedAnswer = selectedByQuestionId[question.id]
                val options = question.options ?: listOf("True", "False")

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = question.question,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    options.forEachIndexed { optionIndex, optionText ->
                        val letter = ('A' + optionIndex).toString()
                        val isSelected = selectedAnswer == letter
                        OutlinedButton(
                            onClick = { selectedByQuestionId[question.id] = letter },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "$letter. $optionText",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()

                    if (index < questions.lastIndex) {
                        Button(
                            onClick = { index += 1 },
                            enabled = selectedAnswer != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Next")
                        }
                    } else {
                        Button(
                            onClick = {
                                correctCount = questions.count { q ->
                                    selectedByQuestionId[q.id] == q.correctAnswer
                                }
                                isSubmitted = true
                            },
                            enabled = selectedAnswer != null,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Submit Quiz")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQuizState(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Rounded.Quiz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Text("No quiz available", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Generate quiz questions from concepts to start a quiz session.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onNavigateBack) { Text("Back") }
        }
    }
}

@Composable
private fun QuizCompletedState(
    score: Int,
    total: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(18.dp)
                )
            }
            Text("Quiz Completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "You scored $score / $total",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Dashboard")
            }
        }
    }
}
